package com.easypan.service;

import com.easypan.component.S3Component;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.exception.BusinessException;
import org.springframework.test.util.ReflectionTestUtils;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("分片上传服务测试")
class ChunkUploadServiceTest {

    @Mock
    private S3Component s3Component;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private SetOperations<String, Object> setOperations;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @Mock
    private MultipartFile chunkFile;

    @Mock
    private org.springframework.core.task.AsyncTaskExecutor virtualThreadExecutor;

    @InjectMocks
    private ChunkUploadService chunkUploadService;

    private String userId;
    private String fileMd5;

    @BeforeEach
    void setUp() {
        userId = "user123";
        fileMd5 = "abc123def456";
        lenient().when(redisTemplate.opsForSet()).thenReturn(setOperations);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        ReflectionTestUtils.setField(chunkUploadService, "virtualThreadExecutor", virtualThreadExecutor);
    }

    @Test
    @DisplayName("正常上传分片应该成功")
    void testUploadChunk_Success() throws IOException {
        Integer chunkIndex = 0;
        Integer totalChunks = 10;
        byte[] chunkData = "test chunk data".getBytes();

        when(setOperations.size(anyString())).thenReturn(0L);
        when(chunkFile.getBytes()).thenReturn(chunkData);
        when(setOperations.add(anyString(), any())).thenReturn(1L);
        when(setOperations.size(contains(":completed"))).thenReturn(1L);

        UploadResultDto result = chunkUploadService.uploadChunk(userId, fileMd5, chunkIndex, totalChunks, chunkFile);

        assertNotNull(result);
        assertEquals("uploading", result.getStatus());
        assertEquals(fileMd5, result.getFileId());

        verify(s3Component, times(1)).uploadBytes(anyString(), eq(chunkData));
        verify(setOperations, times(2)).add(anyString(), any());
        verify(setOperations, times(1)).remove(anyString(), any());
    }

    @Test
    @DisplayName("并发数超限应该抛出异常")
    void testUploadChunk_ConcurrencyLimitExceeded() throws IOException {
        Integer chunkIndex = 0;
        Integer totalChunks = 10;

        when(setOperations.size(contains(":uploading"))).thenReturn(5L);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            chunkUploadService.uploadChunk(userId, fileMd5, chunkIndex, totalChunks, chunkFile);
        });

        assertTrue(exception.getMessage().contains("并发上传数超限"));
        verify(s3Component, never()).uploadBytes(anyString(), any());
    }

    @Test
    @DisplayName("所有分片上传完成应该返回 merge_ready 状态")
    void testUploadChunk_AllChunksCompleted() throws IOException {
        Integer chunkIndex = 9;
        Integer totalChunks = 10;
        byte[] chunkData = "test chunk data".getBytes();

        when(setOperations.size(contains(":uploading"))).thenReturn(0L);
        when(chunkFile.getBytes()).thenReturn(chunkData);
        when(setOperations.add(anyString(), any())).thenReturn(1L);
        when(setOperations.size(contains(":completed"))).thenReturn(10L);

        UploadResultDto result = chunkUploadService.uploadChunk(userId, fileMd5, chunkIndex, totalChunks, chunkFile);

        assertNotNull(result);
        assertEquals("merge_ready", result.getStatus());
        assertEquals(fileMd5, result.getFileId());
    }

    @Test
    @DisplayName("检查分片是否已上传")
    void testIsChunkUploaded() {
        Integer chunkIndex = 5;

        when(setOperations.isMember(anyString(), any(Object.class))).thenReturn(Boolean.TRUE);

        boolean result = chunkUploadService.isChunkUploaded(userId, fileMd5, chunkIndex);

        assertTrue(result);
        verify(setOperations, times(1)).isMember(anyString(), any(Object.class));
    }

    @Test
    @DisplayName("获取已上传分片数量")
    void testGetCompletedChunkCount() {
        when(setOperations.size(anyString())).thenReturn(7L);

        Long count = chunkUploadService.getCompletedChunkCount(userId, fileMd5);

        assertEquals(7L, count);
    }

    @Test
    @DisplayName("清理上传进度")
    void testClearUploadProgress() {
        when(redisTemplate.delete(anyString())).thenReturn(true);

        chunkUploadService.clearUploadProgress(userId, fileMd5);

        verify(redisTemplate, times(2)).delete(anyString());
    }

    @Test
    @DisplayName("上传失败应该移除上传中标记")
    void testUploadChunk_FailureRemovesUploadingFlag() throws IOException {
        Integer chunkIndex = 0;
        Integer totalChunks = 10;

        when(setOperations.size(anyString())).thenReturn(0L);
        when(chunkFile.getBytes()).thenThrow(new IOException("读取文件失败"));
        when(setOperations.add(anyString(), any())).thenReturn(1L);

        assertThrows(IOException.class, () -> {
            chunkUploadService.uploadChunk(userId, fileMd5, chunkIndex, totalChunks, chunkFile);
        });

        verify(setOperations, times(1)).remove(contains(":uploading"), eq(chunkIndex));
    }

    @Test
    @DisplayName("合并分片应该返回 merging 状态")
    void testMergeChunks_ReturnsCorrectStatus() {
        Integer totalChunks = 3;

        UploadResultDto result = chunkUploadService.mergeChunks(userId, fileMd5, totalChunks);

        assertNotNull(result);
        assertEquals("merging", result.getStatus());
        assertEquals(fileMd5, result.getFileId());
    }
}
