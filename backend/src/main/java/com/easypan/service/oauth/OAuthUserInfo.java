package com.easypan.service.oauth;

import lombok.Data;

/**
 * OAuth 用户信息.
 */
@Data
public class OAuthUserInfo {
    private String providerId; // Provider 唯一用户 ID
    private String nickname;
    private String email;
    private String avatarUrl;
    private String provider;
}
