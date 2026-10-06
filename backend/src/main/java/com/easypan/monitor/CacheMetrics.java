package com.easypan.monitor;

import com.easypan.entity.po.UserInfo;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.stats.CacheStats;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

/**
 * 缓存指标监控.
 *
 * <p>对应任务：5.3.3 实现缓存命中率监控
 *
 * <p>暴露 userInfo 本地缓存的命中率指标：
 *
 * <p>- 指标名：cache.userInfo.hit.rate
 */
@Component
public class CacheMetrics {

    private final Cache<String, UserInfo> userInfoCache;

    /**
     * 构造函数，注册缓存命中率指标.
     *
     * @param userInfoCache 用户信息缓存
     * @param meterRegistry 指标注册器
     */
    public CacheMetrics(Cache<String, UserInfo> userInfoCache, MeterRegistry meterRegistry) {
        this.userInfoCache = userInfoCache;

        Gauge.builder("cache.userInfo.hit.rate", this, CacheMetrics::calculateHitRate)
                .description("Hit rate of Caffeine userInfo cache")
                .register(meterRegistry);
    }

    private double calculateHitRate() {
        CacheStats stats = userInfoCache.stats();
        if (stats.requestCount() == 0) {
            return 0.0;
        }
        return stats.hitRate();
    }
}

