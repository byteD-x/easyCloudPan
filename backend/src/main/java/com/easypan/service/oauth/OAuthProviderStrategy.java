package com.easypan.service.oauth;

/**
 * OAuth 提供商策略接口.
 */
public interface OAuthProviderStrategy {
    String getProviderName(); // "github", "gitee", "google", "microsoft", "qq"

    String buildAuthorizationUrl(String state); // 构造授权页 URL

    String getAccessToken(String code); // code 换 token

    OAuthUserInfo getUserInfo(String accessToken); // 获取用户信息
}
