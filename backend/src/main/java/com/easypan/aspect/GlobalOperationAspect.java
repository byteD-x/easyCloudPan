package com.easypan.aspect;

import com.easypan.component.TenantContextHolder;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.lang.reflect.Parameter;

/**
 * 鍏ㄥ眬鎷︽埅鍒囬潰锛岃礋璐ｇ櫥褰曟€佹牎楠屼笌鍙傛暟鏍￠獙.
 */
@Aspect
@Component
public class GlobalOperationAspect {

    private static final Logger logger = LoggerFactory.getLogger(GlobalOperationAspect.class);

    @Pointcut("@annotation(com.easypan.annotation.GlobalInterceptor)")
    private void requestInterceptor() {
    }

    /**
     * 鎵ц鍏ㄥ眬鎷︽埅锛岀粺涓€澶勭悊鐧诲綍鏍￠獙涓庡弬鏁版牎楠?
     *
     * @param point AOP 鍒囩偣涓婁笅鏂?
     * @return 鍘熷鏂规硶鎵ц缁撴灉
     * @throws Throwable 閫忎紶鎵ц杩囩▼涓姏鍑虹殑寮傚父
     */
    @Around("requestInterceptor()")
    public Object interceptorDo(ProceedingJoinPoint point) throws Throwable {
        final long startTime = System.currentTimeMillis();
        Object target = point.getTarget();
        Object[] arguments = point.getArgs();
        String methodName = point.getSignature().getName();
        Class<?>[] parameterTypes = ((MethodSignature) point.getSignature()).getMethod().getParameterTypes();
        Method method = target.getClass().getMethod(methodName, parameterTypes);
        com.easypan.annotation.GlobalInterceptor interceptor = method
                .getAnnotation(com.easypan.annotation.GlobalInterceptor.class);

        if (interceptor == null) {
            return point.proceed();
        }

        if (interceptor.checkLogin() || interceptor.checkAdmin()) {
            checkLogin(interceptor.checkAdmin());
        }

        if (interceptor.checkParams()) {
            validateParams(method, arguments);
        }

        Object result = point.proceed();

        long duration = System.currentTimeMillis() - startTime;
        if (duration > 1000) {
            logger.warn("Slow API {}.{} cost {}ms", target.getClass().getSimpleName(), methodName, duration);
        } else if (logger.isDebugEnabled()) {
            logger.debug("API {}.{} cost {}ms", target.getClass().getSimpleName(), methodName, duration);
        }
        return result;
    }

    @jakarta.annotation.Resource
    private com.easypan.component.JwtTokenProvider jwtTokenProvider;

    @jakarta.annotation.Resource
    private com.easypan.service.JwtBlacklistService jwtBlacklistService;

    @jakarta.annotation.Resource
    private com.easypan.component.RedisComponent redisComponent;

    @jakarta.annotation.Resource
    private com.easypan.service.UserInfoService userInfoService;

    @jakarta.annotation.Resource
    private com.easypan.entity.config.AppConfig appConfig;

    @jakarta.annotation.Resource
    private com.github.benmanes.caffeine.cache.Cache<String, com.easypan.entity.po.UserInfo> userInfoCache;

    private void checkLogin(boolean checkAdmin) {
        HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                .getRequest();

        SessionWebUserDto sessionWebUserDto = null;
        org.springframework.security.core.Authentication authentication = org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication();

        if (authentication != null && authentication.isAuthenticated()
                && !"anonymousUser".equals(authentication.getPrincipal())) {
            String userId = String.valueOf(authentication.getPrincipal());
            com.easypan.entity.po.UserInfo userInfo = loadUserInfo(userId);
            if (userInfo != null) {
                sessionWebUserDto = new SessionWebUserDto();
                sessionWebUserDto.setUserId(userInfo.getUserId());
                sessionWebUserDto.setNickName(userInfo.getNickName());
                sessionWebUserDto.setAdmin(
                        org.apache.commons.lang3.ArrayUtils.contains(
                                appConfig.getAdminEmails().split(","),
                                userInfo.getEmail()));
                sessionWebUserDto.setAvatar(userInfo.getQqAvatar());
                sessionWebUserDto.setTenantId(resolveTenantId(userInfo.getTenantId()));
            }
        }

        if (sessionWebUserDto == null) {
            jakarta.servlet.http.HttpSession session = request.getSession(false);
            if (session != null) {
                Object sessionObj = session.getAttribute(com.easypan.entity.constants.Constants.SESSION_KEY);
                if (sessionObj instanceof SessionWebUserDto dto) {
                    sessionWebUserDto = dto;
                }
            }
        }

        if (sessionWebUserDto == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_901);
        }

        bindTenantContext(request, sessionWebUserDto);
        request.setAttribute(com.easypan.entity.constants.Constants.SESSION_KEY, sessionWebUserDto);
        if (checkAdmin && !Boolean.TRUE.equals(sessionWebUserDto.getAdmin())) {
            throw new BusinessException(ResponseCodeEnum.CODE_404);
        }
    }

    private com.easypan.entity.po.UserInfo loadUserInfo(String userId) {
        com.easypan.entity.po.UserInfo userInfo = userInfoCache.getIfPresent(userId);
        if (userInfo == null) {
            userInfo = userInfoService.getUserInfoByUserId(userId);
            if (userInfo != null) {
                userInfoCache.put(userId, userInfo);
            }
        }
        return userInfo;
    }

    private void bindTenantContext(HttpServletRequest request, SessionWebUserDto sessionWebUserDto) {
        String userTenantId = sessionWebUserDto.getTenantId();
        if (!StringUtils.hasText(userTenantId) && StringUtils.hasText(sessionWebUserDto.getUserId())) {
            com.easypan.entity.po.UserInfo userInfo = loadUserInfo(sessionWebUserDto.getUserId());
            if (userInfo != null) {
                userTenantId = userInfo.getTenantId();
            }
        }
        userTenantId = resolveTenantId(userTenantId);

        String requestTenantId = request.getHeader("X-Tenant-Id");
        if (StringUtils.hasText(requestTenantId) && !userTenantId.equals(requestTenantId.trim())) {
            logger.warn("Tenant mismatch rejected: userId={}, tenantFromUser={}, tenantFromHeader={}",
                    sessionWebUserDto.getUserId(), userTenantId, requestTenantId);
            throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "Tenant info mismatch");
        }

        sessionWebUserDto.setTenantId(userTenantId);
        TenantContextHolder.setTenantId(userTenantId);
    }

    private String resolveTenantId(String tenantId) {
        return StringUtils.hasText(tenantId) ? tenantId : "default";
    }

    private void validateParams(Method method, Object[] arguments) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            Parameter parameter = parameters[i];
            Object value = arguments[i];
            com.easypan.annotation.VerifyParam verifyParam = parameter
                    .getAnnotation(com.easypan.annotation.VerifyParam.class);
            if (verifyParam == null) {
                continue;
            }
            if (value == null) {
                if (verifyParam.required()) {
                    throw new BusinessException(ResponseCodeEnum.CODE_600);
                }
                continue;
            }
            if (value instanceof String) {
                checkString((String) value, verifyParam);
            } else if (value instanceof Integer) {
                checkInteger((Integer) value, verifyParam);
            } else if (value instanceof Long) {
                checkLong((Long) value, verifyParam);
            }
        }
    }

    private void checkString(String value, com.easypan.annotation.VerifyParam verifyParam) {
        if (com.easypan.utils.StringTools.isEmpty(value)) {
            if (verifyParam.required()) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
            return;
        }
        int min = verifyParam.min();
        int max = verifyParam.max();
        if (min >= 0 && value.length() < min) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (max >= 0 && value.length() > max) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (verifyParam.regex() != null && verifyParam.regex() != com.easypan.entity.enums.VerifyRegexEnum.NO) {
            java.util.regex.Pattern pattern = verifyParam.regex().getPattern();
            if (pattern != null && !pattern.matcher(value).matches()) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
        }
    }

    private void checkInteger(Integer value, com.easypan.annotation.VerifyParam verifyParam) {
        if (value == null) {
            if (verifyParam.required()) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
            return;
        }
        int min = verifyParam.min();
        int max = verifyParam.max();
        if (min >= 0 && value < min) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (max >= 0 && value > max) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }

    private void checkLong(Long value, com.easypan.annotation.VerifyParam verifyParam) {
        if (value == null) {
            if (verifyParam.required()) {
                throw new BusinessException(ResponseCodeEnum.CODE_600);
            }
            return;
        }
        int min = verifyParam.min();
        int max = verifyParam.max();
        if (min >= 0 && value < min) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
        if (max >= 0 && value > max) {
            throw new BusinessException(ResponseCodeEnum.CODE_600);
        }
    }
}
