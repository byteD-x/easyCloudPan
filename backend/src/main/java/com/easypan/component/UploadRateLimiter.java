package com.easypan.component;

import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * 上传限流组件.
 *
 * <p>对应任务：
 * 8.1.1 创建 UploadRateLimiter 组件
 *
 * <p>每个用户同时允许的最大并发上传数默认 5 个.
 */
@Component
@Slf4j
public class UploadRateLimiter {

    private com.github.benmanes.caffeine.cache.LoadingCache<String, Semaphore> userSemaphores;

    /**
     * 初始化限流器.
     */
    @PostConstruct
    public void init() {
        this.userSemaphores = Caffeine.newBuilder()
                .maximumSize(10_000)
                .expireAfterAccess(10, TimeUnit.MINUTES)
                .build(key -> new Semaphore(5));
    }

    /**
     * 尝试获取上传许可.
     *
     * @param userId 用户 ID
     * @return true 表示获取成功，可以继续上传；false 表示被限流
     */
    public boolean tryAcquire(String userId) {
        boolean acquired = userSemaphores.get(userId).tryAcquire();
        if (!acquired) {
            log.debug("Upload rate limited for userId={}", userId);
        }
        return acquired;
    }

    /**
     * 释放上传许可.
     *
     * @param userId 用户 ID
     */
    public void release(String userId) {
        userSemaphores.get(userId).release();
    }
}
