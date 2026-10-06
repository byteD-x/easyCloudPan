package com.easypan.service.oauth;

import com.easypan.entity.config.AppConfig;
import com.easypan.exception.BusinessException;
import com.easypan.utils.JsonUtils;
import com.easypan.utils.OKHttpUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * Google OAuth 登录策略.
 */
@Component
public class GoogleOAuthStrategy implements OAuthProviderStrategy {

    @Resource
    private AppConfig appConfig;

    @Override
    public String getProviderName() {
        return "google";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return String.format(
                "https://accounts.google.com/o/oauth2/v2/auth?client_id=%s&redirect_uri=%s&response_type=code&scope=email%%20profile&state=%s",
                appConfig.getGoogleClientId(), appConfig.getGoogleRedirectUri(), state);
    }

    @Override
    public String getAccessToken(String code) {
        String url = "https://oauth2.googleapis.com/token";
        Map<String, String> params = new HashMap<>();
        params.put("client_id", appConfig.getGoogleClientId());
        params.put("client_secret", appConfig.getGoogleClientSecret());
        params.put("code", code);
        params.put("grant_type", "authorization_code");
        params.put("redirect_uri", appConfig.getGoogleRedirectUri());

        String response = OKHttpUtils.postRequest(url, params);
        if (response == null) {
            throw new BusinessException("Failed to get Google access token");
        }

        Map<String, Object> map = JsonUtils.convertJson2Map(response);
        if (map == null || !map.containsKey("access_token")) {
            throw new BusinessException("Failed to get Google access token: " + response);
        }
        return (String) map.get("access_token");
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String url = "https://www.googleapis.com/oauth2/v2/userinfo";
        Map<String, String> header = new HashMap<>();
        header.put("Authorization", "Bearer " + accessToken);

        String response = OKHttpUtils.getRequest(url, header);
        if (response == null) {
            throw new BusinessException("Failed to get Google user info");
        }

        Map<String, Object> userInfoMap = JsonUtils.convertJson2Map(response);
        if (userInfoMap == null || userInfoMap.containsKey("error")) {
            throw new BusinessException("Failed to get Google user info: " + response);
        }

        OAuthUserInfo userInfo = new OAuthUserInfo();
        userInfo.setProviderId((String) userInfoMap.get("id")); // Google 'id' is string
        userInfo.setNickname((String) userInfoMap.get("name"));
        userInfo.setAvatarUrl((String) userInfoMap.get("picture"));
        userInfo.setEmail((String) userInfoMap.get("email"));

        return userInfo;
    }
}
