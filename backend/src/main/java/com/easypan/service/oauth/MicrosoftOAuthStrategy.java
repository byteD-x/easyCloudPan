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
 * Microsoft OAuth 登录策略.
 */
@Component
public class MicrosoftOAuthStrategy implements OAuthProviderStrategy {

    @Resource
    private AppConfig appConfig;

    @Override
    public String getProviderName() {
        return "microsoft";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        // Microsoft 的 common tenant 同时支持个人账号与组织账号。
        return String.format(
                "https://login.microsoftonline.com/common/oauth2/v2.0/authorize?client_id=%s&response_type=code&redirect_uri=%s&response_mode=query&scope=User.Read&state=%s",
                appConfig.getMicrosoftClientId(), appConfig.getMicrosoftRedirectUri(), state);
    }

    @Override
    public String getAccessToken(String code) {
        String url = "https://login.microsoftonline.com/common/oauth2/v2.0/token";
        Map<String, String> params = new HashMap<>();
        params.put("client_id", appConfig.getMicrosoftClientId());
        params.put("scope", "User.Read");
        params.put("code", code);
        params.put("redirect_uri", appConfig.getMicrosoftRedirectUri());
        params.put("grant_type", "authorization_code");
        params.put("client_secret", appConfig.getMicrosoftClientSecret());

        String response = OKHttpUtils.postRequest(url, params);
        if (response == null) {
            throw new BusinessException("Failed to get Microsoft access token");
        }

        Map<String, Object> map = JsonUtils.convertJson2Map(response);
        if (map == null || !map.containsKey("access_token")) {
            throw new BusinessException("Failed to get Microsoft access token: " + response);
        }
        return (String) map.get("access_token");
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String url = "https://graph.microsoft.com/v1.0/me";
        Map<String, String> header = new HashMap<>();
        header.put("Authorization", "Bearer " + accessToken);

        String response = OKHttpUtils.getRequest(url, header);
        if (response == null) {
            throw new BusinessException("Failed to get Microsoft user info");
        }

        Map<String, Object> userInfoMap = JsonUtils.convertJson2Map(response);
        if (userInfoMap == null || userInfoMap.containsKey("error")) {
            throw new BusinessException("Failed to get Microsoft user info: " + response);
        }

        OAuthUserInfo userInfo = new OAuthUserInfo();
        userInfo.setProviderId((String) userInfoMap.get("id"));
        userInfo.setNickname((String) userInfoMap.get("displayName"));
        userInfo.setEmail((String) userInfoMap.get("userPrincipalName")); // 通常为邮箱或 UPN
        if (userInfoMap.containsKey("mail") && userInfoMap.get("mail") != null) {
            userInfo.setEmail((String) userInfoMap.get("mail"));
        }

        // 头像需要额外调用 MS Graph 接口，当前版本先不拉取。
        userInfo.setAvatarUrl(null);

        return userInfo;
    }
}
