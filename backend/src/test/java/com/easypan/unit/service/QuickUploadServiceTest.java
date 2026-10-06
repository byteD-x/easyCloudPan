package com.easypan.unit.service;

import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileStatusEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.QuickUploadService;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("QuickUploadService 单元测试")
class QuickUploadServiceTest {

    @Mock
    private FileInfoMapper fileInfoMapper;

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    @InjectMocks
    private QuickUploadService quickUploadService;

    private FileInfo existingFile;
    private static final String TEST_USER_ID = "user123";
    private static final String TEST_FILE_MD5 = "d41d8cd98f00b204e9800998ecf8427e";
    private static final String TEST_FILE_NAME = "test.txt";
    private static final String TEST_FILE_PID = "0";

    @BeforeEach
    void setUp() {
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        existingFile = new FileInfo();
        existingFile.setFileId("file123");
        existingFile.setUserId("user999");
        existingFile.setFileMd5(TEST_FILE_MD5);
        existingFile.setFileName("original.txt");
        existingFile.setFilePath("/path/to/file");
        existingFile.setFileSize(1024L);
        existingFile.setFileCover("cover.jpg");
        existingFile.setFileCategory(4);
        existingFile.setFileType(7);
        existingFile.setFolderType(0);
        existingFile.setStatus(FileStatusEnums.USING.getStatus());
        existingFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
        existingFile.setCreateTime(new Date());
    }

    @Test
    @DisplayName("秒传成功 - Redis 缓存命中")
    void testQuickUpload_RedisCacheHit() {
        when(valueOperations.get("file:md5:" + TEST_FILE_MD5)).thenReturn("file123");
        when(fileInfoMapper.selectListByQuery(any(QueryWrapper.class)))
            .thenReturn(List.of(existingFile));
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(10))
                .thenReturn("newfile123");

            UploadResultDto result = quickUploadService.checkQuickUpload(
                TEST_USER_ID, TEST_FILE_MD5, TEST_FILE_NAME, TEST_FILE_PID);

            assertNotNull(result);
            assertEquals("upload_seconds", result.getStatus());
            assertNotNull(result.getFileId());

            verify(valueOperations).get("file:md5:" + TEST_FILE_MD5);
            
            ArgumentCaptor<FileInfo> fileCaptor = ArgumentCaptor.forClass(FileInfo.class);
            verify(fileInfoMapper).insert(fileCaptor.capture());
            
            FileInfo insertedFile = fileCaptor.getValue();
            assertEquals(TEST_USER_ID, insertedFile.getUserId());
            assertEquals(TEST_FILE_NAME, insertedFile.getFileName());
            assertEquals(TEST_FILE_MD5, insertedFile.getFileMd5());
            assertEquals(existingFile.getFilePath(), insertedFile.getFilePath());
            assertEquals(existingFile.getFileSize(), insertedFile.getFileSize());
        }
    }

    @Test
    @DisplayName("秒传成功 - Redis 缓存未命中，数据库查询命中")
    void testQuickUpload_DatabaseHit() {
        when(valueOperations.get("file:md5:" + TEST_FILE_MD5)).thenReturn(null);
        when(fileInfoMapper.selectListByQuery(any(QueryWrapper.class)))
            .thenReturn(List.of(existingFile));
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(10))
                .thenReturn("newfile123");

            UploadResultDto result = quickUploadService.checkQuickUpload(
                TEST_USER_ID, TEST_FILE_MD5, TEST_FILE_NAME, TEST_FILE_PID);

            assertNotNull(result);
            assertEquals("upload_seconds", result.getStatus());

            verify(valueOperations).set(
                eq("file:md5:" + TEST_FILE_MD5),
                eq(existingFile.getFileId()),
                eq(7L),
                eq(TimeUnit.DAYS)
            );

            verify(fileInfoMapper).insert(any(FileInfo.class));
        }
    }

    @Test
    @DisplayName("秒传失败 - 文件不存在")
    void testQuickUpload_FileNotExists() {
        when(valueOperations.get("file:md5:" + TEST_FILE_MD5)).thenReturn(null);
        when(fileInfoMapper.selectListByQuery(any(QueryWrapper.class)))
            .thenReturn(Collections.emptyList());

        UploadResultDto result = quickUploadService.checkQuickUpload(
            TEST_USER_ID, TEST_FILE_MD5, TEST_FILE_NAME, TEST_FILE_PID);

        assertNull(result);
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("缓存 MD5 映射")
    void testCacheMd5Mapping() {
        String fileId = "file123";

        quickUploadService.cacheMd5Mapping(TEST_FILE_MD5, fileId);

        verify(valueOperations).set(
            eq("file:md5:" + TEST_FILE_MD5),
            eq(fileId),
            eq(7L),
            eq(TimeUnit.DAYS)
        );
    }

    @Test
    @DisplayName("缓存 MD5 映射 - 参数为空时跳过")
    void testCacheMd5Mapping_SkipWhenEmpty() {
        quickUploadService.cacheMd5Mapping(null, "file123");
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));

        quickUploadService.cacheMd5Mapping(TEST_FILE_MD5, null);
        verify(valueOperations, never()).set(anyString(), any(), anyLong(), any(TimeUnit.class));
    }

    @Test
    @DisplayName("清除 MD5 缓存")
    void testClearMd5Cache() {
        quickUploadService.clearMd5Cache(TEST_FILE_MD5);

        verify(redisTemplate).delete("file:md5:" + TEST_FILE_MD5);
    }

    @Test
    @DisplayName("清除 MD5 缓存 - MD5 为空时跳过")
    void testClearMd5Cache_SkipWhenEmpty() {
        quickUploadService.clearMd5Cache(null);

        verify(redisTemplate, never()).delete(anyString());
    }

    @Test
    @DisplayName("文件引用创建 - 验证所有字段正确复制")
    void testCreateFileReference_AllFieldsCopied() {
        when(valueOperations.get("file:md5:" + TEST_FILE_MD5)).thenReturn(null);
        when(fileInfoMapper.selectListByQuery(any(QueryWrapper.class)))
            .thenReturn(List.of(existingFile));
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(10))
                .thenReturn("newfile123");

            quickUploadService.checkQuickUpload(
                TEST_USER_ID, TEST_FILE_MD5, TEST_FILE_NAME, TEST_FILE_PID);

            ArgumentCaptor<FileInfo> fileCaptor = ArgumentCaptor.forClass(FileInfo.class);
            verify(fileInfoMapper).insert(fileCaptor.capture());
            
            FileInfo newFile = fileCaptor.getValue();
            
            assertEquals(TEST_USER_ID, newFile.getUserId());
            assertEquals(TEST_FILE_NAME, newFile.getFileName());
            assertEquals(TEST_FILE_PID, newFile.getFilePid());
            assertEquals(existingFile.getFileMd5(), newFile.getFileMd5());
            assertEquals(existingFile.getFilePath(), newFile.getFilePath());
            assertEquals(existingFile.getFileSize(), newFile.getFileSize());
            assertEquals(existingFile.getFileCover(), newFile.getFileCover());
            assertEquals(existingFile.getFileCategory(), newFile.getFileCategory());
            assertEquals(existingFile.getFileType(), newFile.getFileType());
            assertEquals(existingFile.getFolderType(), newFile.getFolderType());
            assertEquals(FileStatusEnums.USING.getStatus(), newFile.getStatus());
            assertEquals(FileDelFlagEnums.USING.getFlag(), newFile.getDelFlag());
            assertNotNull(newFile.getCreateTime());
            assertNotNull(newFile.getLastUpdateTime());
            assertNotNull(newFile.getFileId());
            assertNotEquals(existingFile.getFileId(), newFile.getFileId());
        }
    }
}
