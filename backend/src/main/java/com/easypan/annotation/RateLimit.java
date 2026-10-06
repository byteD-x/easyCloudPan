package com.easypan.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 接口防刷速率限制注解.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RateLimit {

    /**
     * 限制时间窗口，默认 1 秒.
     */
    int time() default 1;

    /**
     * 时间窗口内允许的最大请求次数，默认 5 次.
     */
    int count() default 5;

    /**
     * 限流前缀键名，默认 "rate_limit:".
     */
    String key() default "rate_limit:";
}
