package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.component.RedisComponent;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.OAuthCallbackDto;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.exception.BusinessException;
import com.easypan.service.oauth.OAuthLoginService;
import com.easypan.service.oauth.OAuthUserInfo;
import com.easypan.utils.StringTools;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * OAuth 登录控制器.
 */
@RestController
@RequestMapping("/oauth")
public class OAuthController extends ABaseController {

    @Resource
    private OAuthLoginService oauthLoginService;

    @Resource
    private com.easypan.component.JwtTokenProvider jwtTokenProvider;

    @Resource
    private RedisComponent redisComponent;

    private static final String OAUTH_TEMP_PREFIX = "oauth:temp:";
    private static final long OAUTH_TEMP_EXPIRE = 600; // 10分钟过期

    /**
     * 获取 OAuth 登录授权 URL.
     */
    @RequestMapping("/login/{provider}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<String> login(HttpSession session,
            @VerifyParam(required = true) @PathVariable("provider") String provider,
            String callbackUrl) {
        String state = StringTools.getRandomString(Constants.LENGTH_30);
        session.setAttribute(state, callbackUrl);
        String url = oauthLoginService.buildAuthorizationUrl(provider, state);
        return getSuccessResponseVO(url);
    }

    /**
     * OAuth 登录回调处理.
     * 返回两种情况：
     * 1. status=login_success: 用户已存在，直接登录，返回 token 和 userInfo
     * 2. status=need_register: 新用户，返回 registerKey 和 email，前端需跳转注册页
     */
    @RequestMapping("/callback/{provider}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<Map<String, Object>> callback(HttpSession session,
            @VerifyParam(required = true) @PathVariable("provider") String provider,
            @VerifyParam(required = true) String code,
            @VerifyParam(required = true) String state) {

        String cachedCallbackUrl = (String) session.getAttribute(state);

        OAuthCallbackDto callbackDto = oauthLoginService.login(provider, code);
        Map<String, Object> result = new HashMap<>();

        if (callbackDto.isLoginSuccess()) {
            // 已有用户，直接登录
            SessionWebUserDto sessionWebUserDto = callbackDto.getSessionWebUserDto();
            session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
            // T4: 同步 request attribute
            ServletRequestAttributes requestAttributes =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (requestAttributes != null) {
                requestAttributes.getRequest().setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
            }

            String token = jwtTokenProvider.generateToken(sessionWebUserDto.getUserId(), null);
            String refreshToken = jwtTokenProvider.generateRefreshToken(sessionWebUserDto.getUserId(), null);
            long refreshExpirationSeconds = 2592000L;
            redisComponent.saveRefreshToken(sessionWebUserDto.getUserId(), refreshToken, refreshExpirationSeconds);

            result.put("status", "login_success");
            result.put("token", token);
            result.put("refreshToken", refreshToken);
            result.put("userInfo", sessionWebUserDto);
            result.put("callbackUrl", cachedCallbackUrl != null ? cachedCallbackUrl : "/");
        } else {
            // 新用户，需要注册
            OAuthUserInfo oauthUser = callbackDto.getOauthUserInfo();
            String registerKey = StringTools.getRandomString(Constants.LENGTH_30);
            // 将 OAuth 用户信息临时存入 Redis，10 分钟有效
            redisComponent.saveOAuthTempUser(OAUTH_TEMP_PREFIX + registerKey, oauthUser, OAUTH_TEMP_EXPIRE);

            result.put("status", "need_register");
            result.put("registerKey", registerKey);
            result.put("email", oauthUser.getEmail());
            result.put("nickname", oauthUser.getNickname());
            result.put("avatarUrl", oauthUser.getAvatarUrl());
            result.put("provider", provider);
        }

        session.removeAttribute(state);
        return getSuccessResponseVO(result);
    }

    /**
     * OAuth 注册接口.
     * 新用户通过 OAuth 登录后，需调用此接口设置密码并完成注册.
     */
    @RequestMapping("/register")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<Map<String, Object>> register(HttpSession session,
            @VerifyParam(required = true) String registerKey,
            @VerifyParam(required = true) String password) {

        // 从 Redis 取出临时 OAuth 用户信息
        Object cached = redisComponent.getOAuthTempUser(OAUTH_TEMP_PREFIX + registerKey);
        if (cached == null) {
            throw new BusinessException("注册链接已过期，请重新通过第三方登录");
        }
        OAuthUserInfo oauthUser = (OAuthUserInfo) cached;

        // 注册用户
        SessionWebUserDto sessionWebUserDto = oauthLoginService.register(
                oauthUser.getProvider(), oauthUser, password);
        session.setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
        // T4: 同步 request attribute
        ServletRequestAttributes requestAttributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            requestAttributes.getRequest().setAttribute(Constants.SESSION_KEY, sessionWebUserDto);
        }

        // 生成 Token
        String token = jwtTokenProvider.generateToken(sessionWebUserDto.getUserId(), null);
        String refreshToken = jwtTokenProvider.generateRefreshToken(sessionWebUserDto.getUserId(), null);
        long refreshExpirationSeconds = 2592000L;
        redisComponent.saveRefreshToken(sessionWebUserDto.getUserId(), refreshToken, refreshExpirationSeconds);

        Map<String, Object> result = new HashMap<>();
        result.put("token", token);
        result.put("refreshToken", refreshToken);
        result.put("userInfo", sessionWebUserDto);

        return getSuccessResponseVO(result);
    }
}

