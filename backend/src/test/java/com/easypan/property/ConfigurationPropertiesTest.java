package com.easypan.property;

import com.easypan.config.VirtualThreadConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import net.jqwik.api.constraints.AlphaChars;
import net.jqwik.api.constraints.StringLength;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * 配置属性的基于属性的测试。
 * 验证配置属性是否正确应用到执行器和组件配置中。
 * 
 * 此测试验证：
 * - 属性 24：配置属性应用
 * - 属性 25：功能开关行为
 */
@Slf4j
public class ConfigurationPropertiesTest {

    /**
     * 属性 24：配置属性应用
     * 
     * 对于在 application.properties 中设置的任何虚拟线程配置属性，
     * 该值应该被应用到相应的执行器或组件配置中。
     * 
     * **验证：需求 11.1**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 24: Configuration Property Application")
    void configurationPropertiesAreAppliedToExecutor(
            @ForAll boolean enabledValue,
            @ForAll @StringLength(min = 3, max = 20) @AlphaChars String namePrefixValue) {

        log.debug("测试配置属性应用 - enabled: {}, namePrefix: {}", enabledValue, namePrefixValue);

        // 设置系统属性来模拟 application.properties 中的配置
        String originalEnabled = System.getProperty("spring.threads.virtual.enabled");
        String originalPrefix = System.getProperty("spring.threads.virtual.prefix");

        try {
            // 应用测试配置
            System.setProperty("spring.threads.virtual.enabled", String.valueOf(enabledValue));
            System.setProperty("spring.threads.virtual.prefix", namePrefixValue);

            // 创建 Spring 上下文并注册配置
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(VirtualThreadConfig.class, TestConfigService.class);
            context.refresh();

            // 获取测试服务
            TestConfigService testService = context.getBean(TestConfigService.class);

            // 执行异步方法以捕获线程信息
            CompletableFuture<ConfigThreadInfo> future = testService.captureConfigInfo();
            ConfigThreadInfo threadInfo = future.join();

            // 验证配置是否正确应用
            if (enabledValue) {
                // 当启用虚拟线程时，应该使用虚拟线程
                assert threadInfo.isVirtual()
                        : String.format("当 virtual-threads.enabled=true 时，应该使用虚拟线程，但实际使用的是平台线程: %s",
                                threadInfo.getThreadName());

                // 验证线程名称前缀是否正确应用
                assert threadInfo.getThreadName() != null : "线程名称不应为空";
                assert threadInfo.getThreadName().startsWith(namePrefixValue)
                        : String.format("虚拟线程名称应该以配置的前缀 '%s' 开头，但实际名称为: %s",
                                namePrefixValue, threadInfo.getThreadName());

                log.debug("✓ 配置正确应用 - 虚拟线程: {}, 前缀匹配: {}",
                        threadInfo.getThreadName(), namePrefixValue);
            } else {
                // 当禁用虚拟线程时，应该回退到平台线程
                assert !threadInfo.isVirtual()
                        : String.format("当 virtual-threads.enabled=false 时，应该使用平台线程，但实际使用的是虚拟线程: %s",
                                threadInfo.getThreadName());

                // 平台线程应该使用默认的 "async-" 前缀
                assert threadInfo.getThreadName() != null : "线程名称不应为空";
                assert threadInfo.getThreadName().startsWith("async-")
                        : String.format("平台线程名称应该以 'async-' 开头，但实际名称为: %s",
                                threadInfo.getThreadName());

                log.debug("✓ 配置正确应用 - 平台线程回退: {}", threadInfo.getThreadName());
            }

            // 关闭上下文
            context.close();

        } finally {
            // 恢复原始系统属性
            if (originalEnabled != null) {
                System.setProperty("spring.threads.virtual.enabled", originalEnabled);
            } else {
                System.clearProperty("spring.threads.virtual.enabled");
            }

            if (originalPrefix != null) {
                System.setProperty("spring.threads.virtual.prefix", originalPrefix);
            } else {
                System.clearProperty("spring.threads.virtual.prefix");
            }
        }
    }

    /**
     * 属性 25：功能开关行为
     * 
     * 对于 virtual-threads.enabled 配置标志的任何值，
     * 系统应该在值为 true 时使用虚拟线程，在值为 false 时使用平台线程。
     * 
     * **验证：需求 11.2**
     */
    @Property(tries = 2)
    @Label("Feature: virtual-threads-optimization, Property 25: Feature Toggle Behavior")
    void featureToggleControlsThreadType(@ForAll boolean enabledFlag) {

        log.debug("测试功能开关行为 - enabled: {}", enabledFlag);

        // 保存原始系统属性
        String originalEnabled = System.getProperty("spring.threads.virtual.enabled");

        try {
            // 设置功能开关
            System.setProperty("spring.threads.virtual.enabled", String.valueOf(enabledFlag));

            // 创建 Spring 上下文并注册配置
            AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext();
            context.register(VirtualThreadConfig.class, TestConfigService.class);
            context.refresh();

            // 获取测试服务
            TestConfigService testService = context.getBean(TestConfigService.class);

            // 执行异步方法以捕获线程信息
            CompletableFuture<ConfigThreadInfo> future = testService.captureConfigInfo();
            ConfigThreadInfo threadInfo = future.join();

            // 验证功能开关是否正确控制线程类型
            if (enabledFlag) {
                // 当功能开关为 true 时，必须使用虚拟线程
                assert threadInfo.isVirtual()
                        : String.format("当 virtual-threads.enabled=true 时，必须使用虚拟线程，但实际使用的是平台线程: %s",
                                threadInfo.getThreadName());

                log.debug("✓ 功能开关 true - 正确使用虚拟线程: {}", threadInfo.getThreadName());
            } else {
                // 当功能开关为 false 时，必须使用平台线程
                assert !threadInfo.isVirtual()
                        : String.format("当 virtual-threads.enabled=false 时，必须使用平台线程，但实际使用的是虚拟线程: %s",
                                threadInfo.getThreadName());

                log.debug("✓ 功能开关 false - 正确使用平台线程: {}", threadInfo.getThreadName());
            }

            // 验证线程名称符合预期模式
            if (enabledFlag) {
                // 虚拟线程应该使用配置的前缀（默认为 "vt-async-"）
                assert threadInfo.getThreadName() != null : "虚拟线程名称不应为空";
                assert threadInfo.getThreadName().startsWith("vt-async-")
                        : String.format("虚拟线程名称应该以 'vt-async-' 开头，但实际名称为: %s",
                                threadInfo.getThreadName());
            } else {
                // 平台线程应该使用 "async-" 前缀
                assert threadInfo.getThreadName() != null : "平台线程名称不应为空";
                assert threadInfo.getThreadName().startsWith("async-")
                        : String.format("平台线程名称应该以 'async-' 开头，但实际名称为: %s",
                                threadInfo.getThreadName());
            }

            // 关闭上下文
            context.close();

        } finally {
            // 恢复原始系统属性
            if (originalEnabled != null) {
                System.setProperty("virtual-threads.enabled", originalEnabled);
            } else {
                System.clearProperty("virtual-threads.enabled");
            }
        }
    }

    /**
     * 测试服务，用于捕获配置信息。
     * 模拟应用程序中的实际异步服务。
     */
    @Service
    @Slf4j
    static class TestConfigService {

        /**
         * 捕获当前线程的配置信息。
         * 此方法使用 @Async 注解，应该在配置的执行器上执行。
         */
        @Async
        public CompletableFuture<ConfigThreadInfo> captureConfigInfo() {
            Thread currentThread = Thread.currentThread();

            log.debug("捕获配置信息 - 线程: {} (isVirtual: {})",
                    currentThread.getName(), currentThread.isVirtual());

            // 模拟一些工作
            try {
                Thread.sleep(5); // 小延迟以模拟 I/O
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("任务被中断", e);
            }

            ConfigThreadInfo info = new ConfigThreadInfo(
                    currentThread.getName(),
                    currentThread.isVirtual(),
                    currentThread.getClass().getName());

            return CompletableFuture.completedFuture(info);
        }
    }

    /**
     * 用于捕获配置线程信息的辅助类。
     * 根据 AGENTS.md 指南使用 Lombok 保持代码整洁。
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    static class ConfigThreadInfo {
        private String threadName;
        private boolean isVirtual;
        private String threadClassName;
    }
}
