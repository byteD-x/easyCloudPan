package com.easypan.entity.dto;

import com.easypan.service.oauth.OAuthUserInfo;

/**
 * OAuth 回调 DTO.
 */
public class OAuthCallbackDto {
    private boolean loginSuccess;
    private SessionWebUserDto sessionWebUserDto;
    private OAuthUserInfo oauthUserInfo;
    private String registerKey;

    public boolean isLoginSuccess() {
        return loginSuccess;
    }

    public void setLoginSuccess(boolean loginSuccess) {
        this.loginSuccess = loginSuccess;
    }

    public SessionWebUserDto getSessionWebUserDto() {
        return sessionWebUserDto;
    }

    public void setSessionWebUserDto(SessionWebUserDto sessionWebUserDto) {
        this.sessionWebUserDto = sessionWebUserDto;
    }

    public OAuthUserInfo getOauthUserInfo() {
        return oauthUserInfo;
    }

    public void setOauthUserInfo(OAuthUserInfo oauthUserInfo) {
        this.oauthUserInfo = oauthUserInfo;
    }

    public String getRegisterKey() {
        return registerKey;
    }

    public void setRegisterKey(String registerKey) {
        this.registerKey = registerKey;
    }
}
