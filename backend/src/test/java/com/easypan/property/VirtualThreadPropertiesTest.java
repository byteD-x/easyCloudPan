package com.easypan.property;

import com.easypan.config.VirtualThreadConfig;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.jqwik.api.*;
import net.jqwik.api.constraints.IntRange;
import net.jqwik.api.constraints.StringLength;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Property-based tests for Virtual Thread optimization.
 * Tests universal correctness properties across many generated inputs.
 * 
 * This test validates:
 * - Property 1: @Async Methods Execute on Virtual Threads
 * - Property 2: Virtual Thread Naming Convention
 */
@Slf4j
public class VirtualThreadPropertiesTest {

    private static AnnotationConfigApplicationContext context;
    private static TestAsyncService testAsyncService;

    /**
     * Initialize Spring context once for all property tests
     */
    static {
        try {
            // Set properties for Virtual Threads
            System.setProperty("virtual-threads.enabled", "true");
            System.setProperty("virtual-threads.name-prefix", "vt-async-");
            
            // Create Spring context with VirtualThreadConfig
            context = new AnnotationConfigApplicationContext();
            context.register(VirtualThreadConfig.class, TestAsyncService.class);
            context.refresh();
            
            // Get the test service bean
            testAsyncService = context.getBean(TestAsyncService.class);
            
            log.info("Spring context initialized for property tests");
        } catch (Exception e) {
            log.error("Failed to initialize Spring context", e);
            throw new RuntimeException("Failed to initialize test context", e);
        }
    }

    /**
     * Property 1: @Async Methods Execute on Virtual Threads
     * 
     * For any @Async annotated method in the system, when invoked, 
     * the method SHALL execute on a Virtual Thread 
     * (verified by Thread.currentThread().isVirtual() returning true).
     * 
     * **Validates: Requirements 1.2**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 1: @Async Methods Execute on Virtual Threads")
    void asyncMethodsExecuteOnVirtualThreads(
            @ForAll @StringLength(min = 1, max = 50) String taskName,
            @ForAll @IntRange(min = 1, max = 1000) int taskId) {
        
        log.debug("Testing async method execution with taskName: {}, taskId: {}", taskName, taskId);
        
        // Execute async method that captures thread info internally
        CompletableFuture<ThreadInfo> future = testAsyncService.captureThreadInfo(taskName, taskId);
        
        // Wait for completion and get thread info
        ThreadInfo threadInfo = future.join();
        
        // Verify the async method executed successfully
        assert threadInfo != null : "Thread info should be captured";
        assert threadInfo.getThreadName() != null : "Thread name should be captured";
        
        // Verify it executed on a Virtual Thread
        assert threadInfo.isVirtual() : 
            String.format("Async method should execute on Virtual Thread, but executed on: %s (isVirtual=%s)", 
                threadInfo.getThreadName(), threadInfo.isVirtual());
        
        log.debug("✓ Async method executed on Virtual Thread: {}", threadInfo.getThreadName());
    }

    /**
     * Property 2: Virtual Thread Naming Convention
     * 
     * For any Virtual Thread created by the custom executor, 
     * the thread name SHALL match the configured naming pattern 
     * (e.g., starts with "vt-async-").
     * 
     * **Validates: Requirements 1.3**
     */
    @Property(tries = 10)
    @Label("Feature: virtual-threads-optimization, Property 2: Virtual Thread Naming Convention")
    void virtualThreadsFollowNamingConvention(
            @ForAll @StringLength(min = 1, max = 50) String taskName,
            @ForAll @IntRange(min = 1, max = 1000) int taskId) {
        
        log.debug("Testing thread naming convention with taskName: {}, taskId: {}", taskName, taskId);
        
        // Execute async method that captures thread info internally
        CompletableFuture<ThreadInfo> future = testAsyncService.captureThreadInfo(taskName, taskId);
        
        // Wait for completion and get thread info
        ThreadInfo threadInfo = future.join();
        
        // Get the configured name prefix
        String expectedPrefix = System.getProperty("virtual-threads.name-prefix", "vt-async-");
        
        // Verify the thread is virtual
        assert threadInfo.isVirtual() : 
            String.format("Thread should be a Virtual Thread, but was platform thread: %s", 
                threadInfo.getThreadName());
        
        // Verify the thread name follows the naming convention
        assert threadInfo.getThreadName() != null : "Thread name should be captured";
        assert threadInfo.getThreadName().startsWith(expectedPrefix) : 
            String.format("Virtual Thread name should start with '%s', but was: %s", 
                expectedPrefix, threadInfo.getThreadName());
        
        log.debug("✓ Virtual Thread follows naming convention: {}", threadInfo.getThreadName());
    }

    /**
     * Test service with @Async method for property testing.
     * This simulates the actual async services in the application.
     */
    @Service
    @Slf4j
    static class TestAsyncService {
        
        /**
         * Simulates an async operation that should execute on a Virtual Thread.
         * This method is annotated with @Async, which should cause it to execute
         * on the Virtual Thread executor configured in VirtualThreadConfig.
         */
        @Async
        public CompletableFuture<String> executeAsyncTask(String taskName, int taskId) {
            Thread currentThread = Thread.currentThread();
            
            log.debug("Executing async task '{}' with ID {} on thread: {} (isVirtual: {})",
                    taskName, taskId, currentThread.getName(), currentThread.isVirtual());
            
            // Simulate some work
            try {
                Thread.sleep(10); // Small delay to simulate I/O
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
            
            String result = String.format("Task '%s' (ID: %d) completed on thread: %s", 
                    taskName, taskId, currentThread.getName());
            
            return CompletableFuture.completedFuture(result);
        }
        
        /**
         * Captures thread information during async execution.
         * Used for testing thread naming conventions and Virtual Thread properties.
         */
        @Async
        public CompletableFuture<ThreadInfo> captureThreadInfo(String taskName, int taskId) {
            Thread currentThread = Thread.currentThread();
            
            log.debug("Capturing thread info for task '{}' with ID {} on thread: {} (isVirtual: {})",
                    taskName, taskId, currentThread.getName(), currentThread.isVirtual());
            
            // Simulate some work
            try {
                Thread.sleep(10); // Small delay to simulate I/O
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Task interrupted", e);
            }
            
            ThreadInfo info = new ThreadInfo(
                    currentThread.getName(),
                    currentThread.isVirtual(),
                    currentThread.getClass().getName()
            );
            
            return CompletableFuture.completedFuture(info);
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
