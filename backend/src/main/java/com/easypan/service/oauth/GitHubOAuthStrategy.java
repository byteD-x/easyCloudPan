package com.easypan.service.oauth;

import com.easypan.entity.config.AppConfig;
import com.easypan.exception.BusinessException;
import com.easypan.utils.JsonUtils;
import com.easypan.utils.OKHttpUtils;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.Map;

/**
 * GitHub OAuth 登录策略.
 */
@Component
public class GitHubOAuthStrategy implements OAuthProviderStrategy {

    @Resource
    private AppConfig appConfig;

    @Override
    public String getProviderName() {
        return "github";
    }

    @Override
    public String buildAuthorizationUrl(String state) {
        return String.format(
                "https://github.com/login/oauth/authorize?client_id=%s&redirect_uri=%s&state=%s&scope=user:email",
                appConfig.getGithubClientId(), appConfig.getGithubRedirectUri(), state);
    }

    @Override
    public String getAccessToken(String code) {
        String url = String.format(
                "https://github.com/login/oauth/access_token?client_id=%s&client_secret=%s&code=%s&redirect_uri=%s",
                appConfig.getGithubClientId(), appConfig.getGithubClientSecret(), code,
                appConfig.getGithubRedirectUri());
        String response = OKHttpUtils.getRequest(url);
        // GitHub 默认返回 access_token=...&scope=...&token_type=bearer 格式字符串。
        // 这里按 x-www-form-urlencoded 结果手动解析 access_token。
        if (response != null && response.contains("access_token=")) {
            String[] parts = response.split("&");
            for (String part : parts) {
                if (part.startsWith("access_token=")) {
                    return part.split("=")[1];
                }
            }
        }
        throw new BusinessException("Failed to get GitHub access token: " + response);
    }

    @Override
    public OAuthUserInfo getUserInfo(String accessToken) {
        String url = "https://api.github.com/user";
        Map<String, String> header = new java.util.HashMap<>();
        header.put("Authorization", "token " + accessToken);

        String response = OKHttpUtils.getRequest(url, header);
        if (response == null) {
            throw new BusinessException("Failed to get GitHub user info");
        }

        Map<String, Object> userInfoMap = JsonUtils.convertJson2Map(response);
        if (userInfoMap == null || userInfoMap.containsKey("message")) {
            throw new BusinessException("Failed to get GitHub user info: " + response);
        }

        OAuthUserInfo userInfo = new OAuthUserInfo();
        userInfo.setProviderId(String.valueOf(userInfoMap.get("id")));
        userInfo.setNickname((String) userInfoMap.getOrDefault("name", userInfoMap.get("login")));
        userInfo.setAvatarUrl((String) userInfoMap.get("avatar_url"));

        // GitHub 邮箱可能是私密字段；当前先读基础资料，不存在时使用兜底邮箱。
        String email = (String) userInfoMap.get("email");
        if (email == null) {
            // 业务注册链路通常要求邮箱，这里使用 login@github.com 占位避免注册失败。
            email = userInfoMap.get("login") + "@github.com";
        }
        userInfo.setEmail(email);

        return userInfo;
    }
}
