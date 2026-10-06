package com.easypan.metrics;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 自定义监控指标类.
 */
@Component
public class CustomMetrics {

    private final MeterRegistry meterRegistry;
    private final Counter fileUploadCounter;
    private final Counter fileDownloadCounter;
    private final Counter shareCreateCounter;
    private final Counter shareAccessCounter;
    private final Counter loginSuccessCounter;
    private final Counter loginFailureCounter;
    private final Counter cacheHitCounter;
    private final Counter cacheMissCounter;
    private final AtomicLong activeUsers = new AtomicLong(0);
    private final AtomicLong totalStorageUsed = new AtomicLong(0);
    private final AtomicLong virtualThreadCount = new AtomicLong(0);
    private final AtomicLong dbConnectionActive = new AtomicLong(0);
    private final AtomicLong dbConnectionIdle = new AtomicLong(0);
    private final ConcurrentHashMap<String, Timer> fileOperationTimers = new ConcurrentHashMap<>();

    /**
     * 构造函数，初始化所有监控指标.
     *
     * @param meterRegistry 指标注册器
     */
    public CustomMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
        
        fileUploadCounter = Counter.builder("easypan_file_uploads_total")
                .description("Total number of file uploads")
                .tag("type", "upload")
                .register(meterRegistry);
        
        fileDownloadCounter = Counter.builder("easypan_file_downloads_total")
                .description("Total number of file downloads")
                .tag("type", "download")
                .register(meterRegistry);
        
        shareCreateCounter = Counter.builder("easypan_shares_created_total")
                .description("Total number of shares created")
                .tag("type", "create")
                .register(meterRegistry);
        
        shareAccessCounter = Counter.builder("easypan_shares_accessed_total")
                .description("Total number of share accesses")
                .tag("type", "access")
                .register(meterRegistry);
        
        loginSuccessCounter = Counter.builder("easypan_login_total")
                .description("Total number of login attempts")
                .tag("result", "success")
                .register(meterRegistry);
        
        loginFailureCounter = Counter.builder("easypan_login_total")
                .description("Total number of login attempts")
                .tag("result", "failure")
                .register(meterRegistry);
        
        cacheHitCounter = Counter.builder("easypan_cache_requests_total")
                .description("Total number of cache requests")
                .tag("result", "hit")
                .register(meterRegistry);
        
        cacheMissCounter = Counter.builder("easypan_cache_requests_total")
                .description("Total number of cache requests")
                .tag("result", "miss")
                .register(meterRegistry);
        
        Gauge.builder("easypan_active_users", activeUsers, AtomicLong::get)
                .description("Current number of active users")
                .register(meterRegistry);
        
        Gauge.builder("easypan_storage_used_bytes", totalStorageUsed, AtomicLong::get)
                .description("Total storage used in bytes")
                .register(meterRegistry);
        
        Gauge.builder("easypan_virtual_threads_active", virtualThreadCount, AtomicLong::get)
                .description("Current number of active virtual threads")
                .register(meterRegistry);
        
        Gauge.builder("easypan_db_connections_active", dbConnectionActive, AtomicLong::get)
                .description("Current number of active database connections")
                .register(meterRegistry);
        
        Gauge.builder("easypan_db_connections_idle", dbConnectionIdle, AtomicLong::get)
                .description("Current number of idle database connections")
                .register(meterRegistry);
    }

    public void incrementFileUpload() {
        fileUploadCounter.increment();
    }

    public void incrementFileDownload() {
        fileDownloadCounter.increment();
    }

    public void incrementShareCreate() {
        shareCreateCounter.increment();
    }

    public void incrementShareAccess() {
        shareAccessCounter.increment();
    }

    public void incrementLoginSuccess() {
        loginSuccessCounter.increment();
    }

    public void incrementLoginFailure() {
        loginFailureCounter.increment();
    }

    public void incrementCacheHit() {
        cacheHitCounter.increment();
    }

    public void incrementCacheMiss() {
        cacheMissCounter.increment();
    }

    public void setActiveUsers(long count) {
        activeUsers.set(count);
    }

    public void incrementActiveUsers() {
        activeUsers.incrementAndGet();
    }

    public void decrementActiveUsers() {
        activeUsers.decrementAndGet();
    }

    public void setTotalStorageUsed(long bytes) {
        totalStorageUsed.set(bytes);
    }

    public void addStorageUsed(long bytes) {
        totalStorageUsed.addAndGet(bytes);
    }

    public void setVirtualThreadCount(long count) {
        virtualThreadCount.set(count);
    }

    public void incrementVirtualThreads() {
        virtualThreadCount.incrementAndGet();
    }

    public void decrementVirtualThreads() {
        virtualThreadCount.decrementAndGet();
    }

    public void setDbConnectionStats(long active, long idle) {
        dbConnectionActive.set(active);
        dbConnectionIdle.set(idle);
    }

    public Timer.Sample startTimer() {
        return Timer.start(meterRegistry);
    }

    /**
     * 记录文件操作时间.
     *
     * @param operation 操作名称
     * @param sample 计时器样本
     */
    public void recordFileOperationTime(String operation, Timer.Sample sample) {
        Timer timer = fileOperationTimers.computeIfAbsent(operation, op ->
                Timer.builder("easypan_file_operation_duration")
                        .description("File operation duration")
                        .tag("operation", op)
                        .register(meterRegistry)
        );
        sample.stop(timer);
    }

    /**
     * 记录自定义计数器.
     *
     * @param name 计数器名称
     * @param tagName 标签名
     * @param tagValue 标签值
     */
    public void recordCustomCounter(String name, String tagName, String tagValue) {
        Counter.builder(name)
                .tag(tagName, tagValue)
                .register(meterRegistry)
                .increment();
    }
}
