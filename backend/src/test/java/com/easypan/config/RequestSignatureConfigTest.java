package com.easypan.config;

import com.easypan.component.JwtTokenProvider;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.ServletException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.IOException;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("RequestSignatureConfig 签名与重放防护测试")
class RequestSignatureConfigTest {

    private static final String TEST_JWT = "unit-test-jwt-token";
    private static final String SIGNATURE_VERSION = "v2";
    private static final String NONCE_PREFIX = "easypan:nonce:";

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private JwtTokenProvider jwtTokenProvider;

    private final Set<String> nonceCache = ConcurrentHashMap.newKeySet();
    private RequestSignatureConfig filter;

    @BeforeEach
    void setUp() {
        filter = new RequestSignatureConfig(redisTemplate, new ObjectMapper());
        ReflectionTestUtils.setField(filter, "jwtTokenProvider", jwtTokenProvider);
        lenient().when(jwtTokenProvider.validateToken(TEST_JWT)).thenReturn(true);

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(valueOperations.setIfAbsent(anyString(), anyString(), anyLong(), any(TimeUnit.class)))
                .thenAnswer(invocation -> nonceCache.add(invocation.getArgument(0, String.class)));
    }

    @Test
    @DisplayName("敏感接口：合法签名请求应通过")
    void shouldAllowSensitivePathWhenSignatureValid() throws ServletException, IOException {
        MockHttpServletRequest request = buildSignedRequest(
                "POST",
                "/api/file/uploadFile",
                String.valueOf(System.currentTimeMillis()),
                "nonce-valid");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("敏感接口：缺少签名头应拒绝")
    void shouldRejectSensitivePathWhenSignatureMissing() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/file/delFile");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("敏感接口：静态共享密钥签名应拒绝")
    void shouldRejectStaticSecretWithoutJwt() throws ServletException, IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-static-secret";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/file/delFile");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", RequestSignatureConfig.generateSignature(
                timestamp, nonce, "POST", "/api/file/delFile", "", "legacy-static-secret"));
        request.addHeader("X-Signature-Version", SIGNATURE_VERSION);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("非敏感接口：缺少签名头可兼容通过")
    void shouldAllowNonSensitivePathWithoutSignature() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/file/loadDataList");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilter(request, response, chain);

        assertEquals(200, response.getStatus());
        assertNotNull(chain.getRequest());
    }

    @Test
    @DisplayName("签名重放：同一 nonce 第二次请求应拒绝")
    void shouldRejectReplayRequestWithSameNonce() throws ServletException, IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-replay";

        MockHttpServletRequest first = buildSignedRequest("POST", "/api/file/uploadFile", timestamp, nonce);
        MockHttpServletResponse firstResponse = new MockHttpServletResponse();
        filter.doFilter(first, firstResponse, new MockFilterChain());
        assertEquals(200, firstResponse.getStatus());

        MockHttpServletRequest second = buildSignedRequest("POST", "/api/file/uploadFile", timestamp, nonce);
        MockHttpServletResponse secondResponse = new MockHttpServletResponse();
        filter.doFilter(second, secondResponse, new MockFilterChain());

        assertEquals(400, secondResponse.getStatus());
    }

    @Test
    @DisplayName("签名过期：超时请求应拒绝")
    void shouldRejectExpiredTimestamp() throws ServletException, IOException {
        long expiredTs = System.currentTimeMillis() - TimeUnit.MINUTES.toMillis(10);
        MockHttpServletRequest request = buildSignedRequest(
                "POST",
                "/api/file/uploadFile",
                String.valueOf(expiredTs),
                "nonce-expired");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(400, response.getStatus());
    }

    @Test
    @DisplayName("签名篡改：错误签名应拒绝")
    void shouldRejectInvalidSignature() throws ServletException, IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-invalid";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/file/uploadFile");
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", "invalid-signature");
        request.addHeader("X-Signature-Version", SIGNATURE_VERSION);
        request.addHeader("Authorization", "Bearer " + TEST_JWT);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(401, response.getStatus());
    }

    @Test
    @DisplayName("签名版本错误应拒绝")
    void shouldRejectUnsupportedSignatureVersion() throws ServletException, IOException {
        String timestamp = String.valueOf(System.currentTimeMillis());
        String nonce = "nonce-version";
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/file/uploadFile");
        String signature = RequestSignatureConfig.generateSignature(
                timestamp,
                nonce,
                "POST",
                "/api/file/uploadFile",
                "",
                TEST_JWT);
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", signature);
        request.addHeader("X-Signature-Version", "v3");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, new MockFilterChain());

        assertEquals(400, response.getStatus());
    }

    private MockHttpServletRequest buildSignedRequest(
            String method,
            String path,
            String timestamp,
            String nonce) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        String signature = RequestSignatureConfig.generateSignature(
                timestamp,
                nonce,
                method,
                path,
                "",
                TEST_JWT);
        request.addHeader("X-Timestamp", timestamp);
        request.addHeader("X-Nonce", nonce);
        request.addHeader("X-Signature", signature);
        request.addHeader("X-Signature-Version", SIGNATURE_VERSION);
        request.addHeader("Authorization", "Bearer " + TEST_JWT);
        return request;
    }
}
