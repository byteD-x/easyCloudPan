package com.easypan.service;

/**
 * JWT 黑名单服务接口.
 */
public interface JwtBlacklistService {
    /**
     * 将令牌添加到黑名单.
     *
     * @param token JWT 令牌
     * @param expirationTimeInMs 令牌剩余过期时间（毫秒）
     */
    void addToBlacklist(String token, long expirationTimeInMs);

    /**
     * 检查令牌是否在黑名单中.
     *
     * @param token JWT 令牌
     * @return 如果在黑名单中则返回 true
     */
    boolean isBlacklisted(String token);
}
