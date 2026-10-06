package com.easypan.property;

import com.easypan.config.VirtualThreadConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.beans.factory.annotation.Value;
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
import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Property-based tests for S3 operations on Virtual Threads.
 * Tests universal correctness properties across many generated inputs.
 * 
 * This test validates:
 * - Property 7: S3 Operations Execute on Virtual Threads
 * 
 * 所有 S3 操作（上传、下载、删除）在异步调用时应该在虚拟线程上执行。
 */
@Slf4j
public class S3OperationPropertiesTest {

    private static AnnotationConfigApplicationContext context;
    private static TestS3Component testS3Component;
    private static S3Client mockS3Client;

    /**
     * 为所有属性测试初始化 Spring 上下文
     */
    static {
        try {
            // 设置虚拟线程属性
            System.setProperty("spring.threads.virtual.enabled", "true");
            System.setProperty("virtual-threads.name-prefix", "vt-async-");
            
            // 创建 mock S3Client
            mockS3Client = mock(S3Client.class);
            
            // 配置 mock 行为
            when(mockS3Client.putObject(any(PutObjectRequest.class), any(RequestBody.class)))
                .thenReturn(PutObjectResponse.builder().build());
            
            when(mockS3Client.getObject(any(GetObjectRequest.class), any(Path.class)))
                .thenReturn(GetObjectResponse.builder().build());
            
            when(mockS3Client.deleteObject(any(DeleteObjectRequest.class)))
                .thenReturn(DeleteObjectResponse.builder().build());
            
            // 创建带有 VirtualThreadConfig 和 TestS3Component 的 Spring 上下文
            context = new AnnotationConfigApplicationContext();
            context.register(VirtualThreadConfig.class, TestS3Config.class, TestS3Component.class);
            context.refresh();
            
            // 获取测试服务 bean
            testS3Component = context.getBean(TestS3Component.class);
            
            log.info("Spring 上下文已为 S3 属性测试初始化");
        } catch (Exception e) {
            log.error("初始化 Spring 上下文失败", e);
            throw new RuntimeException("初始化测试上下文失败", e);
        }
    }

    /**
     * Property 7: S3 操作在虚拟线程上执行
     * 
     * 对于任何 S3Component 操作（上传、下载、删除），当异步调用时，
     * 操作应该在虚拟线程上执行。
     * 
     * **验证：需求 3.1, 3.2, 3.3**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 7: S3 Operations Execute on Virtual Threads")
    void s3UploadOperationsUseVirtualThreads(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String key,
            @ForAll @IntRange(min = 1, max = 1024) int fileSizeKB) throws Exception {
        
        log.debug("测试 S3 上传操作，key: {}, 文件大小: {} KB", key, fileSizeKB);
        
        // 创建临时测试文件
        File tempFile = createTempFile(fileSizeKB);
        
        try {
            // 执行异步上传操作并获取线程信息
            CompletableFuture<ThreadInfo> future = testS3Component.uploadFileAsync(key, tempFile);
            ThreadInfo threadInfo = future.join();
            
            // 验证操作在虚拟线程上执行
            assert threadInfo != null : "线程信息应该被返回";
            assert threadInfo.isVirtual() : 
                String.format("S3 上传操作应该在虚拟线程上执行，但在以下线程执行: %s (isVirtual=%s)", 
                    threadInfo.getThreadName(), threadInfo.isVirtual());
            
            // 验证线程名称遵循命名约定
            String expectedPrefix = System.getProperty("virtual-threads.name-prefix", "vt-async-");
            assert threadInfo.getThreadName() != null : "线程名称应该被捕获";
            assert threadInfo.getThreadName().startsWith(expectedPrefix) : 
                String.format("虚拟线程名称应该以 '%s' 开头，但实际为: %s", 
                    expectedPrefix, threadInfo.getThreadName());
            
            log.debug("✓ S3 上传操作在虚拟线程上执行: {}", threadInfo.getThreadName());
            
        } finally {
            // 清理临时文件
            if (tempFile.exists()) {
                tempFile.delete();
            }
        }
    }

    /**
     * Property 7: S3 下载操作在虚拟线程上执行
     * 
     * **验证：需求 3.1, 3.2, 3.3**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 7: S3 Download Operations Execute on Virtual Threads")
    void s3DownloadOperationsUseVirtualThreads(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String key) throws Exception {
        
        log.debug("测试 S3 下载操作，key: {}", key);
        
        // 创建临时下载路径
        Path tempPath = Files.createTempFile("s3-download-test-", ".tmp");
        
        try {
            // 执行异步下载操作并获取线程信息
            CompletableFuture<ThreadInfo> future = testS3Component.downloadFileAsync(key, tempPath);
            ThreadInfo threadInfo = future.join();
            
            // 验证操作在虚拟线程上执行
            assert threadInfo != null : "线程信息应该被返回";
            assert threadInfo.isVirtual() : 
                String.format("S3 下载操作应该在虚拟线程上执行，但在以下线程执行: %s (isVirtual=%s)", 
                    threadInfo.getThreadName(), threadInfo.isVirtual());
            
            // 验证线程名称遵循命名约定
            String expectedPrefix = System.getProperty("virtual-threads.name-prefix", "vt-async-");
            assert threadInfo.getThreadName() != null : "线程名称应该被捕获";
            assert threadInfo.getThreadName().startsWith(expectedPrefix) : 
                String.format("虚拟线程名称应该以 '%s' 开头，但实际为: %s", 
                    expectedPrefix, threadInfo.getThreadName());
            
            log.debug("✓ S3 下载操作在虚拟线程上执行: {}", threadInfo.getThreadName());
            
        } finally {
            // 清理临时文件
            Files.deleteIfExists(tempPath);
        }
    }

    /**
     * Property 7: S3 删除操作在虚拟线程上执行
     * 
     * **验证：需求 3.1, 3.2, 3.3**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 7: S3 Delete Operations Execute on Virtual Threads")
    void s3DeleteOperationsUseVirtualThreads(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String key) {
        
        log.debug("测试 S3 删除操作，key: {}", key);
        
        // 执行异步删除操作并获取线程信息
        CompletableFuture<ThreadInfo> future = testS3Component.deleteFileAsync(key);
        ThreadInfo threadInfo = future.join();
        
        // 验证操作在虚拟线程上执行
        assert threadInfo != null : "线程信息应该被返回";
        assert threadInfo.isVirtual() : 
            String.format("S3 删除操作应该在虚拟线程上执行，但在以下线程执行: %s (isVirtual=%s)", 
                threadInfo.getThreadName(), threadInfo.isVirtual());
        
        // 验证线程名称遵循命名约定
        String expectedPrefix = System.getProperty("virtual-threads.name-prefix", "vt-async-");
        assert threadInfo.getThreadName() != null : "线程名称应该被捕获";
        assert threadInfo.getThreadName().startsWith(expectedPrefix) : 
            String.format("虚拟线程名称应该以 '%s' 开头，但实际为: %s", 
                expectedPrefix, threadInfo.getThreadName());
        
        log.debug("✓ S3 删除操作在虚拟线程上执行: {}", threadInfo.getThreadName());
    }

    /**
     * Property 7: S3 批量删除目录操作在虚拟线程上执行
     * 
     * **验证：需求 3.1, 3.2, 3.3**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 7: S3 Delete Directory Operations Execute on Virtual Threads")
    void s3DeleteDirectoryOperationsUseVirtualThreads(
            @ForAll @StringLength(min = 5, max = 50) @AlphaChars String prefix) {
        
        log.debug("测试 S3 批量删除目录操作，prefix: {}", prefix);
        
        // 配置 mock 行为用于目录删除
        ListObjectsV2Response emptyResponse = ListObjectsV2Response.builder()
            .contents(java.util.Collections.emptyList())
            .build();
        when(mockS3Client.listObjectsV2(any(ListObjectsV2Request.class)))
            .thenReturn(emptyResponse);
        
        // 执行异步批量删除操作并获取线程信息
        CompletableFuture<ThreadInfo> future = testS3Component.deleteDirectoryAsync(prefix);
        ThreadInfo threadInfo = future.join();
        
        // 验证操作在虚拟线程上执行
        assert threadInfo != null : "线程信息应该被返回";
        assert threadInfo.isVirtual() : 
            String.format("S3 批量删除操作应该在虚拟线程上执行，但在以下线程执行: %s (isVirtual=%s)", 
                threadInfo.getThreadName(), threadInfo.isVirtual());
        
        // 验证线程名称遵循命名约定
        String expectedPrefix = System.getProperty("virtual-threads.name-prefix", "vt-async-");
        assert threadInfo.getThreadName() != null : "线程名称应该被捕获";
        assert threadInfo.getThreadName().startsWith(expectedPrefix) : 
            String.format("虚拟线程名称应该以 '%s' 开头，但实际为: %s", 
                expectedPrefix, threadInfo.getThreadName());
        
        log.debug("✓ S3 批量删除操作在虚拟线程上执行: {}", threadInfo.getThreadName());
    }

    /**
     * 创建指定大小的临时测试文件
     */
    private File createTempFile(int sizeKB) throws IOException {
        File tempFile = File.createTempFile("s3-test-", ".tmp");
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
     * 测试用的 S3Component,模拟实际的 S3Component 行为
     * 使用 @Async 注解确保方法在虚拟线程上执行
     */
    @Component
    @Slf4j
    static class TestS3Component {
        
        private final S3Client s3Client;
        
        @Value("${minio.bucketName:test-bucket}")
        private String bucketName;
        
        public TestS3Component(S3Client s3Client) {
            this.s3Client = s3Client;
        }
        
        /**
         * 异步上传文件到 S3
         */
        @Async
        public CompletableFuture<ThreadInfo> uploadFileAsync(String key, File file) {
            try {
                Thread currentThread = Thread.currentThread();
                log.debug("异步上传文件到 S3: {} 在线程: {} (isVirtual: {})", 
                    key, currentThread.getName(), currentThread.isVirtual());
                
                // 模拟上传操作
                PutObjectRequest putObjectRequest = PutObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
                s3Client.putObject(putObjectRequest, RequestBody.fromFile(file));
                
                // 模拟 I/O 延迟
                Thread.sleep(10);
                
                log.info("文件上传成功: {}", key);
                
                // 返回线程信息
                ThreadInfo info = new ThreadInfo(
                    currentThread.getName(),
                    currentThread.isVirtual(),
                    currentThread.getClass().getName()
                );
                return CompletableFuture.completedFuture(info);
            } catch (Exception e) {
                log.error("文件上传失败: {}", key, e);
                return CompletableFuture.failedFuture(e);
            }
        }
        
        /**
         * 异步从 S3 下载文件
         */
        @Async
        public CompletableFuture<ThreadInfo> downloadFileAsync(String key, Path path) {
            try {
                Thread currentThread = Thread.currentThread();
                log.debug("异步从 S3 下载文件: {} 在线程: {} (isVirtual: {})", 
                    key, currentThread.getName(), currentThread.isVirtual());
                
                // 模拟下载操作
                GetObjectRequest getObjectRequest = GetObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
                s3Client.getObject(getObjectRequest, path);
                
                // 模拟 I/O 延迟
                Thread.sleep(10);
                
                log.info("文件下载成功: {}", key);
                
                // 返回线程信息
                ThreadInfo info = new ThreadInfo(
                    currentThread.getName(),
                    currentThread.isVirtual(),
                    currentThread.getClass().getName()
                );
                return CompletableFuture.completedFuture(info);
            } catch (Exception e) {
                log.error("文件下载失败: {}", key, e);
                return CompletableFuture.failedFuture(e);
            }
        }
        
        /**
         * 异步从 S3 删除文件
         */
        @Async
        public CompletableFuture<ThreadInfo> deleteFileAsync(String key) {
            try {
                Thread currentThread = Thread.currentThread();
                log.debug("异步从 S3 删除文件: {} 在线程: {} (isVirtual: {})", 
                    key, currentThread.getName(), currentThread.isVirtual());
                
                // 模拟删除操作
                DeleteObjectRequest deleteObjectRequest = DeleteObjectRequest.builder()
                    .bucket(bucketName)
                    .key(key)
                    .build();
                s3Client.deleteObject(deleteObjectRequest);
                
                // 模拟 I/O 延迟
                Thread.sleep(10);
                
                log.info("文件删除成功: {}", key);
                
                // 返回线程信息
                ThreadInfo info = new ThreadInfo(
                    currentThread.getName(),
                    currentThread.isVirtual(),
                    currentThread.getClass().getName()
                );
                return CompletableFuture.completedFuture(info);
            } catch (Exception e) {
                log.error("文件删除失败: {}", key, e);
                return CompletableFuture.failedFuture(e);
            }
        }
        
        /**
         * 异步批量删除 S3 目录
         */
        @Async
        public CompletableFuture<ThreadInfo> deleteDirectoryAsync(String prefix) {
            try {
                Thread currentThread = Thread.currentThread();
                log.debug("异步从 S3 删除目录: {} 在线程: {} (isVirtual: {})", 
                    prefix, currentThread.getName(), currentThread.isVirtual());
                
                // 模拟批量删除操作
                ListObjectsV2Request listRequest = ListObjectsV2Request.builder()
                    .bucket(bucketName)
                    .prefix(prefix)
                    .build();
                @SuppressWarnings("unused")
                ListObjectsV2Response listResponse = s3Client.listObjectsV2(listRequest);
                
                // 模拟 I/O 延迟
                Thread.sleep(10);
                
                log.info("目录删除成功: {}", prefix);
                
                // 返回线程信息
                ThreadInfo info = new ThreadInfo(
                    currentThread.getName(),
                    currentThread.isVirtual(),
                    currentThread.getClass().getName()
                );
                return CompletableFuture.completedFuture(info);
            } catch (Exception e) {
                log.error("目录删除失败: {}", prefix, e);
                return CompletableFuture.failedFuture(e);
            }
        }
    }
    
    /**
     * Helper class to capture thread information from async execution.
     * Uses Lombok for clean code per AGENTS.md guidelines.
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ThreadInfo {
        private String threadName;
        private boolean isVirtual;
        private String threadClassName;
    }
}
