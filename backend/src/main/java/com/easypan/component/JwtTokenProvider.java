package com.easypan.component;

import com.easypan.service.JwtBlacklistService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtBuilder;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.UnsupportedJwtException;
import io.jsonwebtoken.security.Keys;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import jakarta.annotation.PostConstruct;
import java.util.Date;
import java.util.Map;

/**
 * JWT Token 提供者，用于生成、解析和验证 JWT Token.
 */
@Component
public class JwtTokenProvider {

    private static final Logger logger = LoggerFactory.getLogger(JwtTokenProvider.class);

    @Value("${jwt.secret:}")
    private String jwtSecret;

    /**
     * Access Token 过期时间（默认 1 小时）.
     * 生产环境建议使用较短的有效期以提高安全性.
     */
    @Value("${jwt.expiration:3600000}")
    private long jwtExpirationInMs;

    /**
     * Refresh Token 过期时间（默认 30 天）.
     */
    @Value("${jwt.refresh-expiration:2592000000}")
    private long jwtRefreshExpirationInMs;

    @jakarta.annotation.Resource
    private JwtBlacklistService jwtBlacklistService;

    /**
     * JWT 密钥缺失或过短时立即失败，避免服务启动后才在登录请求中暴露配置错误。
     */
    @PostConstruct
    void validateSecret() {
        if (jwtSecret == null || jwtSecret.isBlank()) {
            throw new IllegalStateException("JWT_SECRET 未配置，拒绝启动");
        }
        if (jwtSecret.getBytes(StandardCharsets.UTF_8).length < 32) {
            throw new IllegalStateException("JWT_SECRET 长度至少需要 32 个字节");
        }
    }

    private SecretKey getSigningKey() {
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    /**
     * 生成 Token.
     *
     * @param userId 用户ID
     * @param claims 自定义声明
     * @return JWT Token
     */
    public String generateToken(String userId, Map<String, Object> claims) {
        return generateToken(userId, claims, jwtExpirationInMs);
    }

    /**
     * 生成指定过期时间的 Token.
     *
     * @param userId 用户ID
     * @param claims 自定义声明
     * @param expiration 过期时间（毫秒）
     * @return JWT Token
     */
    private String generateToken(String userId, Map<String, Object> claims, long expiration) {
        Date now = new Date();
        Date expiryDate = new Date(now.getTime() + expiration);

        JwtBuilder builder = Jwts.builder()
                .subject(userId)
                .issuedAt(new Date())
                .expiration(expiryDate)
                .signWith(getSigningKey());

        if (claims != null) {
            builder.claims(claims);
        }

        String token = builder.compact();
        logger.info("JWT token generated for userId={}, expiresAt={}", userId, expiryDate);
        return token;
    }

    /**
     * 生成 Refresh Token.
     *
     * @param userId 用户ID
     * @param claims 自定义声明
     * @return Refresh Token
     */
    public String generateRefreshToken(String userId, Map<String, Object> claims) {
        return generateToken(userId, claims, jwtRefreshExpirationInMs);
    }

    /**
     * 从 Token 中获取 User ID.
     *
     * @param token JWT Token
     * @return 用户ID
     */
    public String getUserIdFromJWT(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();

        return claims.getSubject();
    }

    /**
     * 获取 Token 剩余有效期（毫秒）.
     *
     * @param token JWT Token
     * @return 剩余有效期（毫秒）
     */
    public long getRemainingTime(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Date expiration = claims.getExpiration();
            return expiration.getTime() - System.currentTimeMillis();
        } catch (Exception ex) {
            return 0;
        }
    }

    /**
     * 验证 Token 是否有效.
     *
     * @param authToken JWT Token
     * @return 是否有效
     */
    public boolean validateToken(String authToken) {
        try {
            if (jwtBlacklistService.isBlacklisted(authToken)) {
                logger.warn("JWT Token is in blacklist: {}", maskToken(authToken));
                return false;
            }

            Jwts.parser()
                    .verifyWith(getSigningKey())
                    .build()
                    .parseSignedClaims(authToken);
            return true;
        } catch (MalformedJwtException ex) {
            logger.warn("Invalid JWT token", ex);
        } catch (ExpiredJwtException ex) {
            logger.info("Expired JWT token");
        } catch (UnsupportedJwtException ex) {
            logger.warn("Unsupported JWT token", ex);
        } catch (IllegalArgumentException ex) {
            logger.warn("JWT claims string is empty.", ex);
        } catch (Exception ex) {
            logger.error("JWT validation error", ex);
        }
        return false;
    }

    private String maskToken(String token) {
        if (token == null || token.isEmpty()) {
            return "empty";
        }
        int prefixLength = Math.min(8, token.length());
        return token.substring(0, prefixLength) + "***";
    }
}
