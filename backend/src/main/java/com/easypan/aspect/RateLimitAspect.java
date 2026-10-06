package com.easypan.aspect;

import com.easypan.annotation.RateLimit;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.exception.BusinessException;
import com.easypan.utils.StringTools;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * 速率限制切面.
 */
@Aspect
@Component
public class RateLimitAspect {

    private static final Logger logger = LoggerFactory.getLogger(RateLimitAspect.class);

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /**
     * 拦截带有 RateLimit 注解的方法.
     *
     * @param joinPoint 切入点
     * @return 方法执行结果
     * @throws Throwable 内部发生异常时抛出
     */
    @Around("@annotation(com.easypan.annotation.RateLimit)")
    public Object interceptor(ProceedingJoinPoint joinPoint) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        Method method = signature.getMethod();
        RateLimit rateLimit = method.getAnnotation(RateLimit.class);

        if (rateLimit != null) {
            HttpServletRequest request = ((ServletRequestAttributes) RequestContextHolder.getRequestAttributes())
                    .getRequest();
            String ip = getIpAddress(request);
            String uri = request.getRequestURI();
            String key = rateLimit.key() + uri + ":" + ip;

            long count = stringRedisTemplate.opsForValue().increment(key, 1);
            if (count == 1) {
                stringRedisTemplate.expire(key, rateLimit.time(), TimeUnit.SECONDS);
            }

            if (count > rateLimit.count()) {
                logger.warn("请求过于频繁, IP: {}, URI: {}", ip, uri);
                throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "请求过于频繁，请稍后再试");
            }
        }

        return joinPoint.proceed();
    }

    private String getIpAddress(HttpServletRequest request) {
        String ip = request.getHeader("x-forwarded-for");
        if (StringTools.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (StringTools.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (StringTools.isEmpty(ip) || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0];
        }
        return ip;
    }
}
