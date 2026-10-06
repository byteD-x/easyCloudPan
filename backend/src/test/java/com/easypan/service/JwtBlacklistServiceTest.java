package com.easypan.service;

import com.easypan.component.RedisComponent;
import com.easypan.service.impl.JwtBlacklistServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class JwtBlacklistServiceTest {

    @InjectMocks
    private JwtBlacklistServiceImpl jwtBlacklistService;

    @Mock
    private RedisComponent redisComponent;

    @Test
    @DisplayName("Test Add to Blacklist")
    void testAddToBlacklist() {
        String token = "testToken";
        long expirationAndMillis = 3600000; // 1 hour

        jwtBlacklistService.addToBlacklist(token, expirationAndMillis);

        verify(redisComponent, times(1)).addBlacklistToken(eq(token), eq(expirationAndMillis / 1000));
    }

    @Test
    @DisplayName("Test Is Blacklisted")
    void testIsBlacklisted() {
        String token = "testToken";
        when(redisComponent.isTokenBlacklisted(token)).thenReturn(true);

        assertTrue(jwtBlacklistService.isBlacklisted(token));
        verify(redisComponent, times(1)).isTokenBlacklisted(token);
    }
}
