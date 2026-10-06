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
 * Gitee OAuth 登录策略.
 */
@Component
public class GiteeOAuthStrategy implements OAuthProviderStrategy {

    @Resource
    private AppConfig appConfig;

    @Override
    public String getProviderName() {
        return "gitee";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return String.format(
                "https://gitee.com/oauth/authorize?client_id=%s&redirect_uri=%s&response_type=code&state=%s",
                appConfig.getGiteeClientId(), appConfig.getGiteeRedirectUri(), state);
    }

    @Override
    public String getAccessToken(String code) {
        String url = "https://gitee.com/oauth/token";
        Map<String, String> params = new HashMap<>();
        params.put("grant_type", "authorization_code");
        params.put("code", code);
        params.put("client_id", appConfig.getGiteeClientId());
        params.put("redirect_uri", appConfig.getGiteeRedirectUri());
        params.put("client_secret", appConfig.getGiteeClientSecret());

        String response = OKHttpUtils.postRequest(url, params);
        if (response == null) {
            throw new BusinessException("Failed to get Gitee access token");
        }

        Map<String, Object> map = JsonUtils.convertJson2Map(response);
        if (map == null || !map.containsKey("access_token")) {
            throw new BusinessException("Failed to get Gitee access token: " + response);
        }
        return (String) map.get("access_token");
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String url = "https://gitee.com/api/v5/user?access_token=" + accessToken;
        String response = OKHttpUtils.getRequest(url);
        if (response == null) {
            throw new BusinessException("Failed to get Gitee user info");
        }

        Map<String, Object> userInfoMap = JsonUtils.convertJson2Map(response);
        if (userInfoMap == null || userInfoMap.containsKey("message")) {
            throw new BusinessException("Failed to get Gitee user info: " + response);
        }

        OAuthUserInfo userInfo = new OAuthUserInfo();
        userInfo.setProviderId(String.valueOf(userInfoMap.get("id")));
        userInfo.setNickname((String) userInfoMap.getOrDefault("name", userInfoMap.get("login")));
        userInfo.setAvatarUrl((String) userInfoMap.get("avatar_url"));

        Object emailObj = userInfoMap.get("email");
        if (emailObj != null) {
            userInfo.setEmail((String) emailObj);
        } else {
            userInfo.setEmail(userInfoMap.get("login") + "@gitee.com");
        }

        return userInfo;
    }
}
