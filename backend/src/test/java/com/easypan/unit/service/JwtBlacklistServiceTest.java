package com.easypan.unit.service;

import com.easypan.component.RedisComponent;
import com.easypan.service.impl.JwtBlacklistServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("JwtBlacklistService 单元测试")
class JwtBlacklistServiceTest {

    @Mock
    private RedisComponent redisComponent;

    @InjectMocks
    private JwtBlacklistServiceImpl jwtBlacklistService;

    private String testToken;
    private static final long ONE_HOUR_MS = 60 * 60 * 1000L;
    private static final long ONE_MINUTE_MS = 60 * 1000L;

    @BeforeEach
    void setUp() {
        testToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.test_payload.signature";
    }

    @Test
    @DisplayName("添加 Token 到黑名单 - 正常过期时间")
    void testAddToBlacklist_NormalExpiration() {
        long expirationTimeMs = ONE_HOUR_MS;

        jwtBlacklistService.addToBlacklist(testToken, expirationTimeMs);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(ONE_HOUR_MS / 1000));
    }

    @Test
    @DisplayName("添加 Token 到黑名单 - 剩余时间很短")
    void testAddToBlacklist_ShortExpiration() {
        long expirationTimeMs = ONE_MINUTE_MS;

        jwtBlacklistService.addToBlacklist(testToken, expirationTimeMs);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(ONE_MINUTE_MS / 1000));
    }

    @Test
    @DisplayName("添加 Token 到黑名单 - 零过期时间（不添加）")
    void testAddToBlacklist_ZeroExpiration() {
        long expirationTimeMs = 0L;

        jwtBlacklistService.addToBlacklist(testToken, expirationTimeMs);

        verify(redisComponent, never()).addBlacklistToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("添加 Token 到黑名单 - 负过期时间（不添加）")
    void testAddToBlacklist_NegativeExpiration() {
        long expirationTimeMs = -1000L;

        jwtBlacklistService.addToBlacklist(testToken, expirationTimeMs);

        verify(redisComponent, never()).addBlacklistToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("添加 Token 到黑名单 - 大过期时间（天级别）")
    void testAddToBlacklist_LargeExpiration() {
        long sevenDaysMs = 7 * 24 * 60 * 60 * 1000L;

        jwtBlacklistService.addToBlacklist(testToken, sevenDaysMs);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(sevenDaysMs / 1000));
    }

    @Test
    @DisplayName("检查 Token 是否在黑名单 - 已存在")
    void testIsBlacklisted_TokenExists() {
        when(redisComponent.isTokenBlacklisted(testToken)).thenReturn(true);

        boolean result = jwtBlacklistService.isBlacklisted(testToken);

        assertTrue(result);
        verify(redisComponent, times(1)).isTokenBlacklisted(testToken);
    }

    @Test
    @DisplayName("检查 Token 是否在黑名单 - 不存在")
    void testIsBlacklisted_TokenNotExists() {
        when(redisComponent.isTokenBlacklisted(testToken)).thenReturn(false);

        boolean result = jwtBlacklistService.isBlacklisted(testToken);

        assertFalse(result);
        verify(redisComponent, times(1)).isTokenBlacklisted(testToken);
    }

    @Test
    @DisplayName("检查 Token 是否在黑名单 - 空Token")
    void testIsBlacklisted_EmptyToken() {
        when(redisComponent.isTokenBlacklisted("")).thenReturn(false);

        boolean result = jwtBlacklistService.isBlacklisted("");

        assertFalse(result);
    }

    @Test
    @DisplayName("检查 Token 是否在黑名单 - null Token")
    void testIsBlacklisted_NullToken() {
        when(redisComponent.isTokenBlacklisted(null)).thenReturn(false);

        boolean result = jwtBlacklistService.isBlacklisted(null);

        assertFalse(result);
    }

    @Test
    @DisplayName("登出场景 - Token 加入黑名单后检查")
    void testLogoutScenario() {
        when(redisComponent.isTokenBlacklisted(testToken))
                .thenReturn(false)
                .thenReturn(true);

        boolean beforeBlacklist = jwtBlacklistService.isBlacklisted(testToken);
        assertFalse(beforeBlacklist);

        jwtBlacklistService.addToBlacklist(testToken, ONE_HOUR_MS);

        boolean afterBlacklist = jwtBlacklistService.isBlacklisted(testToken);
        assertTrue(afterBlacklist);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), anyLong());
        verify(redisComponent, times(2)).isTokenBlacklisted(testToken);
    }

    @Test
    @DisplayName("毫秒转秒 - 精度验证")
    void testMillisecondToSecondConversion() {
        long ms = 5000L;
        long expectedSeconds = 5L;

        jwtBlacklistService.addToBlacklist(testToken, ms);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(expectedSeconds));
    }

    @Test
    @DisplayName("毫秒转秒 - 小于1秒的时间被截断为0（不添加）")
    void testMillisecondToSecondConversion_LessThanSecond() {
        long ms = 500L;

        jwtBlacklistService.addToBlacklist(testToken, ms);

        verify(redisComponent, never()).addBlacklistToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("毫秒转秒 - 999毫秒被截断为0（不添加）")
    void testMillisecondToSecondConversion_999ms() {
        long ms = 999L;

        jwtBlacklistService.addToBlacklist(testToken, ms);

        verify(redisComponent, never()).addBlacklistToken(anyString(), anyLong());
    }

    @Test
    @DisplayName("毫秒转秒 - 1000毫秒转为1秒")
    void testMillisecondToSecondConversion_Exactly1000ms() {
        long ms = 1000L;
        long expectedSeconds = 1L;

        jwtBlacklistService.addToBlacklist(testToken, ms);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(expectedSeconds));
    }

    @Test
    @DisplayName("毫秒转秒 - 1001毫秒转为1秒")
    void testMillisecondToSecondConversion_1001ms() {
        long ms = 1001L;
        long expectedSeconds = 1L;

        jwtBlacklistService.addToBlacklist(testToken, ms);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(expectedSeconds));
    }

    @Test
    @DisplayName("多 Token 场景 - 不同 Token 独立管理")
    void testMultipleTokens_IndependentManagement() {
        String token1 = "token_user1_session1";
        String token2 = "token_user1_session2";
        String token3 = "token_user2_session1";

        when(redisComponent.isTokenBlacklisted(token1)).thenReturn(false);
        when(redisComponent.isTokenBlacklisted(token2)).thenReturn(true);
        when(redisComponent.isTokenBlacklisted(token3)).thenReturn(false);

        assertFalse(jwtBlacklistService.isBlacklisted(token1));
        assertTrue(jwtBlacklistService.isBlacklisted(token2));
        assertFalse(jwtBlacklistService.isBlacklisted(token3));

        jwtBlacklistService.addToBlacklist(token1, ONE_HOUR_MS);
        jwtBlacklistService.addToBlacklist(token3, ONE_HOUR_MS);

        verify(redisComponent, times(1)).addBlacklistToken(eq(token1), anyLong());
        verify(redisComponent, times(1)).addBlacklistToken(eq(token3), anyLong());
        verify(redisComponent, never()).addBlacklistToken(eq(token2), anyLong());
    }

    @Test
    @DisplayName("边界值测试 - 最大过期时间（30天）")
    void testBoundary_MaxExpiration() {
        long thirtyDaysMs = 30L * 24 * 60 * 60 * 1000;
        long expectedSeconds = 30L * 24 * 60 * 60;

        jwtBlacklistService.addToBlacklist(testToken, thirtyDaysMs);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(expectedSeconds));
    }

    @Test
    @DisplayName("边界值测试 - 最小有效过期时间（1秒）")
    void testBoundary_MinValidExpiration() {
        long oneSecondMs = 1000L;
        long expectedSeconds = 1L;

        jwtBlacklistService.addToBlacklist(testToken, oneSecondMs);

        verify(redisComponent, times(1)).addBlacklistToken(eq(testToken), eq(expectedSeconds));
    }
}
