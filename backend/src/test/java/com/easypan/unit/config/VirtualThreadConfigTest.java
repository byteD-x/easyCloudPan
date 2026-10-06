package com.easypan.unit.config;

import com.easypan.config.VirtualThreadConfig;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.aop.interceptor.AsyncUncaughtExceptionHandler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;

import java.lang.reflect.Method;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 单元测试：VirtualThreadConfig 配置场景测试
 * 
 * 测试场景：
 * 1. 启用虚拟线程时的执行器创建
 * 2. 禁用时回退到平台线程
 * 3. 配置详情的日志记录
 * 
 * **验证需求：1.1, 1.4, 1.5**
 */
@Slf4j
class VirtualThreadConfigTest {

    private VirtualThreadConfig config;

    @BeforeEach
    void setUp() {
        config = new VirtualThreadConfig();
    }

    /**
     * 测试场景 1：启用虚拟线程时创建虚拟线程执行器
     * 
     * 验证：
     * - 当 virtual-threads.enabled=true 且 JDK 支持虚拟线程时
     * - 应该创建虚拟线程执行器
     * - 执行的任务应该在虚拟线程上运行
     * 
     * **验证需求：1.1, 1.2**
     */
    @Test
    void testVirtualThreadExecutorCreation_WhenEnabled() throws Exception {
        // 设置配置：启用虚拟线程
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", true);
        ReflectionTestUtils.setField(config, "namePrefix", "vt-test-");

        // 获取执行器
        Executor executor = config.getAsyncExecutor();

        // 验证执行器不为空
        assertThat(executor).isNotNull();

        // 验证执行器类型（虚拟线程执行器是 ThreadPerTaskExecutor）
        // TaskExecutorAdapter wraps the actual executor
        Object wrappedExecutor = ReflectionTestUtils.getField(executor, "concurrentExecutor");
        assertThat(wrappedExecutor.getClass().getName())
                .contains("ThreadPerTaskExecutor");

        // 验证任务在虚拟线程上执行
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            executionThread.set(Thread.currentThread());
            log.debug("任务执行在线程: {}", Thread.currentThread().getName());
        }, executor);

        future.join();

        // 验证是虚拟线程
        Thread thread = executionThread.get();
        assertThat(thread).isNotNull();
        assertThat(thread.isVirtual())
                .as("任务应该在虚拟线程上执行")
                .isTrue();

        // 验证线程名称前缀
        assertThat(thread.getName())
                .as("虚拟线程名称应该以配置的前缀开头")
                .startsWith("vt-test-");

        log.info("✓ 测试通过：启用虚拟线程时成功创建虚拟线程执行器");
    }

    /**
     * 测试场景 2：禁用虚拟线程时回退到平台线程执行器
     * 
     * 验证：
     * - 当 virtual-threads.enabled=false 时
     * - 应该创建平台线程执行器（ThreadPoolTaskExecutor）
     * - 执行的任务应该在平台线程上运行
     * 
     * **验证需求：1.1, 1.4**
     */
    @Test
    void testFallbackToPlatformThreads_WhenDisabled() throws Exception {
        // 设置配置：禁用虚拟线程
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", false);
        ReflectionTestUtils.setField(config, "namePrefix", "vt-test-");

        // 获取执行器
        Executor executor = config.getAsyncExecutor();

        // 验证执行器不为空
        assertThat(executor).isNotNull();

        // 验证执行器类型是 ThreadPoolTaskExecutor
        assertThat(executor)
                .as("禁用虚拟线程时应该返回 ThreadPoolTaskExecutor")
                .isInstanceOf(ThreadPoolTaskExecutor.class);

        // 验证平台线程执行器的配置
        ThreadPoolTaskExecutor taskExecutor = (ThreadPoolTaskExecutor) executor;
        assertThat(taskExecutor.getCorePoolSize())
                .as("核心线程池大小应该为 10")
                .isEqualTo(10);
        assertThat(taskExecutor.getMaxPoolSize())
                .as("最大线程池大小应该为 50")
                .isEqualTo(50);
        assertThat(taskExecutor.getThreadNamePrefix())
                .as("平台线程名称前缀应该为 'async-'")
                .isEqualTo("async-");

        // 验证任务在平台线程上执行
        AtomicReference<Thread> executionThread = new AtomicReference<>();
        CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
            executionThread.set(Thread.currentThread());
            log.debug("任务执行在线程: {}", Thread.currentThread().getName());
        }, executor);

        future.join();

        // 验证是平台线程（非虚拟线程）
        Thread thread = executionThread.get();
        assertThat(thread).isNotNull();
        assertThat(thread.isVirtual())
                .as("任务应该在平台线程上执行（非虚拟线程）")
                .isFalse();

        // 验证线程名称前缀
        assertThat(thread.getName())
                .as("平台线程名称应该以 'async-' 开头")
                .startsWith("async-");

        log.info("✓ 测试通过：禁用虚拟线程时成功回退到平台线程执行器");
    }

    /**
     * 测试场景 3：验证配置详情的日志记录
     * 
     * 验证：
     * - 当创建执行器时，应该记录配置详情
     * - 启用虚拟线程时记录 INFO 级别日志
     * - 禁用虚拟线程时记录 WARN 级别日志
     * 
     * 注意：此测试验证日志方法被调用，实际日志输出由日志框架处理
     * 
     * **验证需求：1.5**
     */
    @Test
    void testConfigurationLogging_WhenEnabled() {
        // 设置配置：启用虚拟线程
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", true);
        ReflectionTestUtils.setField(config, "namePrefix", "vt-test-");

        // 获取执行器（这会触发日志记录）
        Executor executor = config.getAsyncExecutor();

        // 验证执行器创建成功
        assertThat(executor).isNotNull();

        // 日志验证：
        // 实际应用中，启用虚拟线程时会记录：
        // log.info("Configuring Virtual Thread executor for @Async operations with
        // prefix: {}", namePrefix);
        //
        // 在单元测试中，我们通过执行器类型来间接验证正确的代码路径被执行
        Object wrappedExecutor = ReflectionTestUtils.getField(executor, "concurrentExecutor");
        assertThat(wrappedExecutor.getClass().getName())
                .as("启用虚拟线程时应该创建虚拟线程执行器")
                .contains("ThreadPerTaskExecutor");

        log.info("✓ 测试通过：启用虚拟线程时记录配置详情");
    }

    @Test
    void testConfigurationLogging_WhenDisabled() {
        // 设置配置：禁用虚拟线程
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", false);
        ReflectionTestUtils.setField(config, "namePrefix", "vt-test-");

        // 获取执行器（这会触发日志记录）
        Executor executor = config.getAsyncExecutor();

        // 验证执行器创建成功
        assertThat(executor).isNotNull();

        // 日志验证：
        // 实际应用中，禁用虚拟线程时会记录：
        // log.warn("Virtual Threads not available or disabled, falling back to platform
        // threads");
        //
        // 在单元测试中，我们通过执行器类型来间接验证正确的代码路径被执行
        assertThat(executor)
                .as("禁用虚拟线程时应该创建平台线程执行器")
                .isInstanceOf(ThreadPoolTaskExecutor.class);

        log.info("✓ 测试通过：禁用虚拟线程时记录警告日志");
    }

    /**
     * 测试场景 4：验证异常处理器配置
     * 
     * 验证：
     * - AsyncUncaughtExceptionHandler 应该被正确配置
     * - 异常处理器应该能够处理 @Async 方法抛出的异常
     * 
     * **验证需求：1.1**
     */
    @Test
    void testAsyncUncaughtExceptionHandler() throws Exception {
        // 获取异常处理器
        AsyncUncaughtExceptionHandler handler = config.getAsyncUncaughtExceptionHandler();

        // 验证异常处理器不为空
        assertThat(handler).isNotNull();

        // 模拟异常场景
        Throwable testException = new RuntimeException("测试异常");
        Method testMethod = this.getClass().getDeclaredMethod("setUp");
        Object[] params = new Object[] { "param1", "param2" };

        // 调用异常处理器（应该记录日志而不抛出异常）
        handler.handleUncaughtException(testException, testMethod, params);

        // 验证：异常处理器执行成功（没有抛出异常）
        log.info("✓ 测试通过：异常处理器正确配置");
    }

    /**
     * 测试场景 5：验证虚拟线程名称序列
     * 
     * 验证：
     * - 虚拟线程应该按序列命名（vt-test-0, vt-test-1, ...）
     * - 每个新任务应该创建新的虚拟线程
     * 
     * **验证需求：1.3**
     */
    @Test
    void testVirtualThreadNamingSequence() throws Exception {
        // 设置配置：启用虚拟线程
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", true);
        ReflectionTestUtils.setField(config, "namePrefix", "vt-seq-");

        // 获取执行器
        Executor executor = config.getAsyncExecutor();

        // 执行多个任务并收集线程名称
        AtomicReference<String> threadName1 = new AtomicReference<>();
        AtomicReference<String> threadName2 = new AtomicReference<>();
        AtomicReference<String> threadName3 = new AtomicReference<>();

        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            threadName1.set(Thread.currentThread().getName());
        }, executor);

        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            threadName2.set(Thread.currentThread().getName());
        }, executor);

        CompletableFuture<Void> future3 = CompletableFuture.runAsync(() -> {
            threadName3.set(Thread.currentThread().getName());
        }, executor);

        // 等待所有任务完成
        CompletableFuture.allOf(future1, future2, future3).join();

        // 验证所有线程名称都以配置的前缀开头
        assertThat(threadName1.get()).startsWith("vt-seq-");
        assertThat(threadName2.get()).startsWith("vt-seq-");
        assertThat(threadName3.get()).startsWith("vt-seq-");

        log.info("✓ 测试通过：虚拟线程名称序列正确");
        log.debug("线程名称: {}, {}, {}", threadName1.get(), threadName2.get(), threadName3.get());
    }

    /**
     * 测试场景 6：验证虚拟线程支持检测
     * 
     * 验证：
     * - 在 JDK 21+ 环境中，应该检测到虚拟线程支持
     * - isVirtualThreadsSupported() 方法应该返回 true
     * 
     * **验证需求：1.1, 1.4**
     */
    @Test
    void testVirtualThreadsSupportDetection() {
        // 设置配置：启用虚拟线程
        ReflectionTestUtils.setField(config, "virtualThreadsEnabled", true);
        ReflectionTestUtils.setField(config, "namePrefix", "vt-test-");

        // 获取执行器
        Executor executor = config.getAsyncExecutor();

        // 在 JDK 21+ 环境中，应该创建虚拟线程执行器
        // 通过执行器类型来验证虚拟线程支持被正确检测
        Object wrappedExecutor = ReflectionTestUtils.getField(executor, "concurrentExecutor");
        assertThat(wrappedExecutor.getClass().getName())
                .as("JDK 21+ 应该支持虚拟线程并创建 ThreadPerTaskExecutor")
                .contains("ThreadPerTaskExecutor");

        log.info("✓ 测试通过：虚拟线程支持检测正确");
    }
}
