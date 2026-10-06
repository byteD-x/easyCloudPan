package com.easypan.controller;

import com.easypan.entity.vo.ResponseVO;
import com.easypan.exception.BusinessException;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Locale;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * 全局异常处理器测试
 * 验证需求：3.1.3 - 生产环境异常脱敏
 */
class AGlobalExceptionHandlerControllerTest {

    private AGlobalExceptionHandlerController exceptionHandler;
    private HttpServletRequest mockRequest;
    private MessageSource mockMessageSource;

    @BeforeEach
    void setUp() {
        exceptionHandler = new AGlobalExceptionHandlerController();
        mockRequest = mock(HttpServletRequest.class);
        mockMessageSource = mock(MessageSource.class);
        
        when(mockRequest.getRequestURI()).thenReturn("/api/test");
        when(mockRequest.getMethod()).thenReturn("GET");
        
        // Mock MessageSource to return the key itself (simulating no i18n message found)
        when(mockMessageSource.getMessage(anyString(), any(), anyString(), any(Locale.class)))
            .thenAnswer(invocation -> invocation.getArgument(2)); // Return default message
        
        // Inject mocked MessageSource
        ReflectionTestUtils.setField(exceptionHandler, "messageSource", mockMessageSource);
    }

    @Test
    @DisplayName("生产环境不应返回堆栈信息")
    void testProductionEnvironmentSanitizesStackTrace() {
        // Given: 生产环境配置 (dev=false, profile=prod)
        ReflectionTestUtils.setField(exceptionHandler, "isDevelopmentMode", false);
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");
        Exception exception = new RuntimeException("Internal error");

        // When: 处理异常
        Object response = exceptionHandler.handleException(exception, mockRequest);

        // Then: 响应不包含详细错误信息
        assertNotNull(response);
        assertTrue(response instanceof ResponseVO);
        ResponseVO<?> responseVO = (ResponseVO<?>) response;
        
        assertEquals("error", responseVO.getStatus());
        assertEquals(500, responseVO.getCode());
        assertNotNull(responseVO.getInfo());
        assertNull(responseVO.getData(), "生产环境不应返回详细错误信息（堆栈跟踪）");
    }

    @Test
    @DisplayName("开发环境应返回详细错误信息和堆栈跟踪")
    void testDevelopmentEnvironmentReturnsDetailedError() {
        // Given: 开发环境配置 (dev=true)
        ReflectionTestUtils.setField(exceptionHandler, "isDevelopmentMode", true);
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "dev");
        Exception exception = new RuntimeException("Test error");

        // When: 处理异常
        Object response = exceptionHandler.handleException(exception, mockRequest);

        // Then: 响应包含详细错误信息
        assertNotNull(response);
        assertTrue(response instanceof ResponseVO);
        ResponseVO<?> responseVO = (ResponseVO<?>) response;
        
        assertEquals("error", responseVO.getStatus());
        assertEquals(500, responseVO.getCode());
        assertNotNull(responseVO.getData(), "开发环境应返回详细错误信息（包含堆栈跟踪）");
    }

    @Test
    @DisplayName("业务异常在生产环境不返回堆栈信息")
    void testBusinessExceptionInProduction() {
        // Given: 生产环境配置
        ReflectionTestUtils.setField(exceptionHandler, "isDevelopmentMode", false);
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "prod");
        BusinessException exception = new BusinessException("业务错误");

        // When: 处理异常
        Object response = exceptionHandler.handleException(exception, mockRequest);

        // Then: 只返回业务错误信息，不返回堆栈
        assertNotNull(response);
        assertTrue(response instanceof ResponseVO);
        ResponseVO<?> responseVO = (ResponseVO<?>) response;
        
        assertEquals("error", responseVO.getStatus());
        assertNotNull(responseVO.getInfo());
        assertNull(responseVO.getData(), "生产环境不应返回详细错误信息");
    }

    @Test
    @DisplayName("开发环境返回详细信息（验证dev配置生效）")
    void testDevelopmentModeWithDevConfig() {
        // Given: 开发环境配置 (dev=true, 即使profile不是dev)
        ReflectionTestUtils.setField(exceptionHandler, "isDevelopmentMode", true);
        ReflectionTestUtils.setField(exceptionHandler, "activeProfile", "test");
        Exception exception = new RuntimeException("Test error");

        // When: 处理异常
        Object response = exceptionHandler.handleException(exception, mockRequest);

        // Then: 返回详细错误信息
        assertNotNull(response);
        assertTrue(response instanceof ResponseVO);
        ResponseVO<?> responseVO = (ResponseVO<?>) response;
        
        assertEquals("error", responseVO.getStatus());
        assertEquals(500, responseVO.getCode());
        assertNotNull(responseVO.getData(), "dev=true时应返回详细错误信息");
    }
}
