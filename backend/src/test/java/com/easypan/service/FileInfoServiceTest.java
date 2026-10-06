package com.easypan.service;

import com.easypan.component.RedisComponent;
import com.easypan.component.UploadRateLimiter;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.enums.UploadStatusEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.impl.FileInfoServiceImpl;
import com.easypan.strategy.StorageStrategy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileInfoServiceTest {

    @InjectMocks
    private FileInfoServiceImpl fileInfoService;

    @Mock
    private FileInfoMapper fileInfoMapper;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private RedisComponent redisComponent;

    @Mock
    private AppConfig appConfig;

    @Mock
    private UploadRateLimiter uploadRateLimiter;

    @Mock
    private AsyncTaskExecutor virtualThreadExecutor;

    @Mock
    private StorageStrategy storageStrategy;

    @Mock
    private UploadProgressService uploadProgressService;

    @Mock
    private TenantQuotaService tenantQuotaService;

    @BeforeEach
    void setUp() {
        // Handle self-injection for transactional methods if necessary
        // For unit tests not using Spring proxy, we can just set it to itself or a spy
        ReflectionTestUtils.setField(fileInfoService, "fileInfoService", fileInfoService);
    }

    @Test
    @DisplayName("Test Instant Upload (Seconds Upload)")
    void testUploadFile_InstantUpload() {
        String userId = "user1";
        String fileMd5 = "md5_123";
        String fileId = "file1";
        String fileName = "test.txt";
        MultipartFile file = mock(MultipartFile.class);
        SessionWebUserDto userDto = new SessionWebUserDto();
        userDto.setUserId(userId);

        try {
            when(file.getInputStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[0]));
        } catch (java.io.IOException e) {
            e.printStackTrace();
        }

        // Mock DB finding existing file with same MD5
        FileInfo existingFile = new FileInfo();
        existingFile.setUserId(userId);
        existingFile.setFileMd5(fileMd5);

        // Mock Rate Limiter
        when(uploadRateLimiter.tryAcquire(anyString())).thenReturn(true);
        existingFile.setFileSize(100L);
        existingFile.setFilePath("path/to/file");

        // Mock finding list returns this file

        // Mock user space check
        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setUseSpace(0L);
        userSpaceDto.setTotalSpace(1000L);
        when(redisComponent.getUserSpaceUse(userId)).thenReturn(userSpaceDto);

        // Mock Instant Upload success path
        when(redisComponent.mightContainFileMd5(eq(fileMd5))).thenReturn(true);
        when(fileInfoMapper.selectOneByMd5AndStatus(eq(fileMd5), anyInt())).thenReturn(existingFile);

        // Mock saving new file info
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        // Mock updating user space
        when(userInfoMapper.incrementUseSpace(anyString(), anyLong())).thenReturn(1);

        UploadResultDto result = fileInfoService.uploadFile(userDto, fileId, file, fileName, "0", fileMd5, 0, 1);

        assertNotNull(result);
        assertEquals(UploadStatusEnums.UPLOAD_SECONDS.getCode(), result.getStatus());
        verify(fileInfoMapper, times(1)).insert(any(FileInfo.class));
        verify(userInfoMapper).incrementUseSpace(eq(userId), eq(100L));
    }

    @Test
    @DisplayName("Test Batch Move to Recycle Bin")
    void testRemoveFile2RecycleBatch() {
        String userId = "user1";
        String fileIds = "file1,file2";

        // Mock finding files
        FileInfo file1 = new FileInfo();
        file1.setFileId("file1");
        file1.setUserId(userId);
        file1.setFilePid("0");
        file1.setFolderType(FileFolderTypeEnums.FILE.getType());

        FileInfo file2 = new FileInfo();
        file2.setFileId("file2");
        file2.setUserId(userId);
        file2.setFilePid("0");
        file2.setFolderType(FileFolderTypeEnums.FILE.getType());

        when(fileInfoMapper.selectListByQuery(any(com.mybatisflex.core.query.QueryWrapper.class)))
                .thenReturn(List.of(file1, file2));

        // Mock batch update
        // Note: The service might do this recursively or just update status.
        // Based on previous code view, it calls updateFileDelFlagBatch
        // Void method, do nothing by default.

        assertDoesNotThrow(() -> fileInfoService.removeFile2RecycleBatch(userId, fileIds));

        // Verify mapper call
        verify(fileInfoMapper, times(1)).updateFileDelFlagBatch(any(FileInfo.class), eq(userId), any(), anyList(),
                anyInt());
    }
}
