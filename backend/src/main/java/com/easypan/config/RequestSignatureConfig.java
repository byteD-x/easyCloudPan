package com.easypan.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 请求签名过滤器.
 * 对敏感接口执行重放防护与签名校验.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestSignatureConfig extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(RequestSignatureConfig.class);

    private static final String SIGNATURE_HEADER = "X-Signature";
    private static final String TIMESTAMP_HEADER = "X-Timestamp";
    private static final String NONCE_HEADER = "X-Nonce";
    private static final String SIGNATURE_VERSION_HEADER = "X-Signature-Version";
    private static final String SIGNATURE_VERSION = "v2";

    private static final long SIGNATURE_VALIDITY_MS = 5 * 60 * 1000;
    private static final String NONCE_CACHE_PREFIX = "easypan:nonce:";

    private static final Set<String> SKIP_PATHS = Set.of(
            "/api/login",
            "/api/register",
            "/api/logout",
            "/api/refreshToken",
            "/api/checkCode",
            "/api/sendEmailCode",
            "/api/actuator",
            "/api/swagger-ui",
            "/api/v3/api-docs");

    private static final Set<String> SENSITIVE_PATH_PREFIXES = Set.of(
            "/api/file/uploadFile",
            "/api/file/delFile",
            "/api/file/changeFileFolder",
            "/api/file/rename",
            "/api/file/newFoloder",
            "/api/share/shareFile",
            "/api/share/cancelShare",
            "/api/showShare/saveShare",
            "/api/recycle/",
            "/api/admin/",
            "/api/updatePassword",
            "/api/updateUserAvatar",
            "/api/updateNickName");

    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;

    @jakarta.annotation.Resource
    private com.easypan.component.JwtTokenProvider jwtTokenProvider;

    public RequestSignatureConfig(RedisTemplate<String, String> redisTemplate, ObjectMapper objectMapper) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            filterChain.doFilter(request, response);
            return;
        }

        String requestPath = request.getRequestURI();
        if (shouldSkipSignature(requestPath)) {
            filterChain.doFilter(request, response);
            return;
        }

        String signature = request.getHeader(SIGNATURE_HEADER);
        String timestamp = request.getHeader(TIMESTAMP_HEADER);
        String nonce = request.getHeader(NONCE_HEADER);
        String signatureVersion = request.getHeader(SIGNATURE_VERSION_HEADER);
        boolean hasSignatureHeaders = StringUtils.hasText(signature)
                && StringUtils.hasText(timestamp)
                && StringUtils.hasText(nonce);

        if (!hasSignatureHeaders) {
            if (requiresSignature(requestPath, request.getMethod())) {
                sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Missing signature headers");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        if (StringUtils.hasText(signatureVersion) && !SIGNATURE_VERSION.equals(signatureVersion)) {
            logger.warn("Unsupported signature version: path={}, version={}", requestPath, signatureVersion);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Unsupported signature version");
            return;
        }

        if (!validateTimestamp(timestamp)) {
            logger.warn("Request timestamp expired: path={}, timestamp={}", requestPath, timestamp);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Request timestamp expired");
            return;
        }

        String signingSecret = resolveSigningSecret(request);
        if (!StringUtils.hasText(signingSecret)) {
            // 签名必须绑定已验证的 JWT，避免浏览器/仓库中的静态共享密钥被伪造。
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Authentication required");
            return;
        }

        String expectedSignature = generateSignature(
                timestamp,
                nonce,
                request.getMethod(),
                requestPath,
                "",
                signingSecret);
        if (!constantTimeEquals(signature, expectedSignature)) {
            logger.warn("Invalid request signature: path={}", requestPath);
            sendErrorResponse(response, HttpServletResponse.SC_UNAUTHORIZED, "Invalid signature");
            return;
        }

        if (!claimNonce(nonce)) {
            logger.warn("Request nonce already used or nonce store unavailable: path={}, nonce={}", requestPath, nonce);
            sendErrorResponse(response, HttpServletResponse.SC_BAD_REQUEST, "Duplicate request");
            return;
        }
        filterChain.doFilter(request, response);
    }

    private String resolveSigningSecret(HttpServletRequest request) {
        String authorization = request.getHeader("Authorization");
        if (StringUtils.hasText(authorization)) {
            String token = authorization.startsWith("Bearer ")
                    ? authorization.substring(7)
                    : authorization;
            if (StringUtils.hasText(token) && jwtTokenProvider != null
                    && jwtTokenProvider.validateToken(token)) {
                // JWT 已是客户端持有的短期凭证，用它作为本次 HMAC 密钥，避免仓库/前端存在共享静态密钥。
                return token;
            }
            return null;
        }
        return null;
    }

    private boolean shouldSkipSignature(String path) {
        return SKIP_PATHS.stream().anyMatch(path::startsWith);
    }

    private boolean requiresSignature(String path, String method) {
        if ("GET".equalsIgnoreCase(method) || "HEAD".equalsIgnoreCase(method)) {
            return false;
        }
        return SENSITIVE_PATH_PREFIXES.stream().anyMatch(path::startsWith);
    }

    private boolean validateTimestamp(String timestamp) {
        try {
            long requestTime = Long.parseLong(timestamp);
            long currentTime = System.currentTimeMillis();
            return Math.abs(currentTime - requestTime) <= SIGNATURE_VALIDITY_MS;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    /** 原子占用 nonce，避免 check-then-set 竞态让同一签名并发穿透。 */
    private boolean claimNonce(String nonce) {
        String key = NONCE_CACHE_PREFIX + nonce;
        try {
            return Boolean.TRUE.equals(redisTemplate.opsForValue()
                    .setIfAbsent(key, "1", SIGNATURE_VALIDITY_MS, TimeUnit.MILLISECONDS));
        } catch (RuntimeException e) {
            // 重放存储不可用时拒绝请求，不能降级为无保护放行。
            return false;
        }
    }

    private boolean constantTimeEquals(String provided, String expected) {
        return MessageDigest.isEqual(
                provided.getBytes(StandardCharsets.UTF_8),
                expected.getBytes(StandardCharsets.UTF_8));
    }

    private void sendErrorResponse(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType("application/json;charset=UTF-8");
        Map<String, Object> error = new HashMap<>();
        error.put("status", "error");
        error.put("code", status);
        error.put("info", message);
        response.getWriter().write(objectMapper.writeValueAsString(error));
    }

    /**
     * 使用 HMAC-SHA256 生成请求签名.
     */
    public static String generateSignature(
            String timestamp,
            String nonce,
            String method,
            String path,
            String body,
            String secret) {
        try {
            String data = String.join("&", timestamp, nonce, method.toUpperCase(), path, body == null ? "" : body);
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return bytesToHex(mac.doFinal(data.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new RuntimeException("Failed to generate signature", e);
        }
    }

    private static String bytesToHex(byte[] bytes) {
        StringBuilder hexString = new StringBuilder();
        for (byte b : bytes) {
            String hex = Integer.toHexString(0xff & b);
            if (hex.length() == 1) {
                hexString.append('0');
            }
            hexString.append(hex);
        }
        return hexString.toString();
    }
}
