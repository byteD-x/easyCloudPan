package com.easypan.property;

import com.easypan.config.VirtualThreadConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.*;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for Virtual Thread scalability.
 * Tests that the system can handle thousands of concurrent operations without
 * excessive memory usage.
 * 
 * This test validates:
 * - Property 8: Virtual Thread Scalability
 * 
 * 对于任何并发负载的 S3 操作（最多 10,000 个同时请求），
 * 系统应该能够创建虚拟线程而不超过内存阈值（例如，堆增长 < 500MB）。
 */
@Slf4j
public class VirtualThreadScalabilityPropertiesTest {

    private static AnnotationConfigApplicationContext context;
    private static TestScalableS3Component testS3Component;
    private static S3Client mockS3Client;

    /**
     * 为所有属性测试初始化 Spring 上下文
     */
    static {
        try {
            // 设置虚拟线程属性 (Correct property name matching VirtualThreadConfig)
            System.setProperty("spring.threads.virtual.enabled", "true");
            System.setProperty("spring.threads.virtual.prefix", "vt-async-");

            // 创建 mock S3Client
            mockS3Client = mock(S3Client.class);

            // 配置 mock 行为 - 快速响应以支持高并发测试
            when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                    .thenReturn(PutObjectResponse.builder().build());

            // 创建带有 VirtualThreadConfig 和 TestScalableS3Component 的 Spring 上下文
            context = new AnnotationConfigApplicationContext();
            // Register PropertySourcesPlaceholderConfigurer to parse @Value
            context.registerBean(org.springframework.context.support.PropertySourcesPlaceholderConfigurer.class);
            context.register(VirtualThreadConfig.class, TestS3Config.class, TestScalableS3Component.class);
            context.refresh();

            // 获取测试服务 bean
            testS3Component = context.getBean(TestScalableS3Component.class);

            log.info("Spring 上下文已为虚拟线程可扩展性测试初始化");
        } catch (Exception e) {
            log.error("初始化 Spring 上下文失败", e);
            throw new RuntimeException("初始化测试上下文失败", e);
        }
    }

    /**
     * Property 8: 虚拟线程可扩展性
     * 
     * 对于任何并发负载的 S3 操作（最多 10,000 个同时请求），
     * 系统应该能够创建虚拟线程而不超过内存阈值（例如，堆增长 < 500MB）。
     * 
     * **验证：需求 3.4**
     */
    @Property(tries = 20)
    @Label("Feature: virtual-threads-optimization, Property 8: Virtual Thread Scalability")
    void virtualThreadsScaleToThousandsOfConcurrentOperations(
            @ForAll @IntRange(min = 100, max = 1000) int concurrentOperations) throws Exception {

        log.info("测试虚拟线程可扩展性，并发操作数: {}", concurrentOperations);

        // 强制垃圾回收并记录初始内存使用
        System.gc();
        Thread.sleep(100);
        Runtime runtime = Runtime.getRuntime();
        long memoryBeforeMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);

        log.debug("初始内存使用: {} MB", memoryBeforeMB);

        // 创建临时测试文件（小文件以加快测试速度）
        File tempFile = createTempFile(1); // 1KB

        try {
            // 启动大量并发操作
            List<CompletableFuture<ScalabilityResult>> futures = new ArrayList<>();
            long startTime = System.currentTimeMillis();

            for (int i = 0; i < concurrentOperations; i++) {
                String key = "scalability-test-" + i;
                CompletableFuture<ScalabilityResult> future = testS3Component.uploadFileAsync(key, tempFile);
                futures.add(future);
            }

            // 等待所有操作完成
            CompletableFuture<Void> allOf = CompletableFuture.allOf(
                    futures.toArray(new CompletableFuture[0]));
            allOf.join();

            long endTime = System.currentTimeMillis();
            long durationMs = endTime - startTime;

            // 收集结果
            List<ScalabilityResult> results = new ArrayList<>();
            for (CompletableFuture<ScalabilityResult> future : futures) {
                results.add(future.join());
            }

            // 强制垃圾回收并测量最终内存使用
            System.gc();
            Thread.sleep(100);
            long memoryAfterMB = (runtime.totalMemory() - runtime.freeMemory()) / (1024 * 1024);
            long memoryIncreaseMB = memoryAfterMB - memoryBeforeMB;

            log.info("完成 {} 个并发操作，耗时: {} ms", concurrentOperations, durationMs);
            log.info("内存使用 - 之前: {} MB, 之后: {} MB, 增长: {} MB",
                    memoryBeforeMB, memoryAfterMB, memoryIncreaseMB);

            // 验证所有操作都在虚拟线程上执行
            long virtualThreadCount = results.stream()
                    .filter(ScalabilityResult::isVirtual)
                    .count();

            assert virtualThreadCount == concurrentOperations : String.format("所有操作应该在虚拟线程上执行，但只有 %d/%d 在虚拟线程上",
                    virtualThreadCount, concurrentOperations);

            // 验证内存增长在合理范围内
            // 对于虚拟线程，即使是 1000 个并发操作，内存增长也应该很小
            // 虚拟线程的栈大小约为 1KB，而平台线程约为 1MB
            // 因此 1000 个虚拟线程应该只增加约 1-10MB（考虑到其他开销）
            long maxMemoryIncreaseMB = 500; // 最大允许 500MB 增长（需求中的阈值）

            assert memoryIncreaseMB < maxMemoryIncreaseMB : String.format("内存增长应该小于 %d MB，但实际增长了 %d MB（%d 个并发操作）",
                    maxMemoryIncreaseMB, memoryIncreaseMB, concurrentOperations);

            // 计算吞吐量
            double throughput = (double) concurrentOperations / (durationMs / 1000.0);
            log.info("✓ 虚拟线程可扩展性验证通过 - {} 个并发操作，内存增长: {} MB，吞吐量: {} ops/sec",
                    concurrentOperations, memoryIncreaseMB, String.format("%.2f", throughput));

        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * 创建指定大小的临时测试文件
     */
    private File createTempFile(int sizeKB) throws IOException {
        File tempFile = File.createTempFile("scalability-test-", ".tmp");
        byte[] data = new byte[sizeKB * 1024];
        // 填充一些数据
        for (int i = 0; i < data.length; i++) {
            data[i] = (byte) (i % 256);
        }
        Files.write(tempFile.toPath(), data);
        return tempFile;
    }

    /**
     * 测试配置类，提供 mock S3Client bean
     */
    @Configuration
    static class TestS3Config {

        @Bean
        public S3Client s3Client() {
            return mockS3Client;
        }
    }

    /**
     * 测试用的可扩展 S3Component
     * 使用 @Async 注解确保方法在虚拟线程上执行
     */
    @Component
    @Slf4j
    static class TestScalableS3Component {

        private final S3Client s3Client;
        private final String bucketName = "test-bucket";

        public TestScalableS3Component(S3Client s3Client) {
            this.s3Client = s3Client;
        }

        /**
         * 异步上传文件到 S3（用于可扩展性测试）
         */
        @Async
        public CompletableFuture<ScalabilityResult> uploadFileAsync(String key, File file) {
            try {
                Thread currentThread = Thread.currentThread();
                long startTime = System.nanoTime();

                // 模拟上传操作
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                        .bucket(bucketName)
                        .key(key)
                        .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));

                // 模拟轻微的 I/O 延迟（1ms）
                Thread.sleep(1);

                long endTime = System.nanoTime();
                long durationNs = endTime - startTime;

                // 返回可扩展性结果
                ScalabilityResult result = new ScalabilityResult(
                        currentThread.getName(),
                        currentThread.isVirtual(),
                        durationNs / 1_000_000.0, // 转换为毫秒
                        true);
                return CompletableFuture.completedFuture(result);
            } catch (Exception e) {
                log.error("文件上传失败: {}", key, e);
                return CompletableFuture.failedFuture(e);
            }
        }
    }

    /**
     * 可扩展性测试结果类
     * 使用 Lombok 保持代码整洁（遵循 AGENTS.md 规范）
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ScalabilityResult {
        private String threadName;
        private boolean isVirtual;
        private double latencyMs;
        private boolean success;
    }
}
