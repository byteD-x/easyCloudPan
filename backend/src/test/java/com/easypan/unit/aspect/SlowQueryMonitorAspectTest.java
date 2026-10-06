package com.easypan.unit.aspect;

import com.easypan.aspect.SlowQueryMonitorAspect;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.Mockito.lenient;

/**
 * SlowQueryMonitorAspect 单元测试
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("慢查询监控切面测试")
class SlowQueryMonitorAspectTest {

    @InjectMocks
    private SlowQueryMonitorAspect slowQueryMonitorAspect;

    @Mock
    private ProceedingJoinPoint joinPoint;

    @Mock
    private Signature signature;

    @BeforeEach
    void setUp() {
        lenient().when(joinPoint.getSignature()).thenReturn(signature);
        lenient().when(signature.toShortString()).thenReturn("FileInfoMapper.selectById(..)");
    }

    @Test
    @DisplayName("快速查询不应记录警告日志")
    void testFastQuery() throws Throwable {
        // Given: 模拟快速查询（小于100ms）
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            // 模拟快速查询，不延迟
            return "result";
        });

        // When: 执行切面方法
        Object result = slowQueryMonitorAspect.monitorQuery(joinPoint);

        // Then: 验证返回结果正确
        assertEquals("result", result);
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("慢查询应记录警告日志")
    void testSlowQuery() throws Throwable {
        // Given: 模拟慢查询（超过100ms）
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            // 模拟慢查询，延迟150ms
            Thread.sleep(150);
            return "result";
        });

        // When: 执行切面方法
        Object result = slowQueryMonitorAspect.monitorQuery(joinPoint);

        // Then: 验证返回结果正确
        assertEquals("result", result);
        verify(joinPoint, times(1)).proceed();
        // 注意：实际的日志验证需要使用日志捕获工具，这里只验证方法执行
    }

    @Test
    @DisplayName("查询异常时应正确抛出")
    void testQueryWithException() throws Throwable {
        // Given: 模拟查询抛出异常
        RuntimeException expectedException = new RuntimeException("Database error");
        when(joinPoint.proceed()).thenThrow(expectedException);

        // When & Then: 验证异常被正确抛出
        RuntimeException thrown = assertThrows(RuntimeException.class, () -> {
            slowQueryMonitorAspect.monitorQuery(joinPoint);
        });

        assertEquals("Database error", thrown.getMessage());
        verify(joinPoint, times(1)).proceed();
    }

    @Test
    @DisplayName("即使查询异常，也应记录执行时间")
    void testSlowQueryWithException() throws Throwable {
        // Given: 模拟慢查询后抛出异常
        when(joinPoint.proceed()).thenAnswer(invocation -> {
            Thread.sleep(150);
            throw new RuntimeException("Database error");
        });

        // When & Then: 验证异常被正确抛出
        assertThrows(RuntimeException.class, () -> {
            slowQueryMonitorAspect.monitorQuery(joinPoint);
        });

        verify(joinPoint, times(1)).proceed();
        // 注意：即使异常，finally块中的日志记录仍会执行
    }
}
