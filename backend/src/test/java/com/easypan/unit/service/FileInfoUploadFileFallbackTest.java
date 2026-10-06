package com.easypan.unit.service;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.UploadStatusEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.TenantQuotaService;
import com.easypan.service.impl.FileInfoServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FileInfoUploadFileFallbackTest {

        @Mock
        private AppConfig appConfig;

        @Mock
        private FileInfoMapper fileInfoMapper;

        @Mock
        private RedisComponent redisComponent;

        @Mock
        private com.easypan.component.UploadRateLimiter uploadRateLimiter;

        @Mock
        private TenantQuotaService tenantQuotaService;

        @Mock
        private com.easypan.service.UploadProgressService uploadProgressService;

        @InjectMocks
        private FileInfoServiceImpl fileInfoService;

        @Test
        void uploadFile_shouldFallbackToNormalUpload_whenQuickUploadSourceFileSizeIsNull(
                        @org.junit.jupiter.api.io.TempDir Path tempDir) {
                String userId = "u_001";
                String fileId = "f_001";
                String filePid = "0";
                String fileMd5 = "md5_001";

                SessionWebUserDto webUserDto = new SessionWebUserDto();
                webUserDto.setUserId(userId);

                // Ensure local project folder is absolute and writable
                String projectFolder = tempDir.toString().replace("\\", "/") + "/";
                when(appConfig.getProjectFolder()).thenReturn(projectFolder);

                when(uploadRateLimiter.tryAcquire(userId)).thenReturn(true);

                UserSpaceDto spaceDto = new UserSpaceDto();
                spaceDto.setUseSpace(0L);
                spaceDto.setTotalSpace(1024L * 1024L * 1024L);
                when(redisComponent.getUserSpaceUse(userId)).thenReturn(spaceDto);

                when(redisComponent.getFileTempSize(userId, fileId)).thenReturn(0L);

                // Simulate quick-upload hit but with incomplete metadata (fileSize is null)
                when(redisComponent.mightContainFileMd5(fileMd5)).thenReturn(true);
                FileInfo dbFile = new FileInfo();
                dbFile.setFileId("source_001");
                dbFile.setFileSize(null);
                when(fileInfoMapper.selectOneByMd5AndStatus(eq(fileMd5), anyInt())).thenReturn(dbFile);

                byte[] pngHeader = new byte[] {
                                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
                                0x00, 0x00, 0x00, 0x00
                };
                MockMultipartFile file = new MockMultipartFile(
                                "file",
                                "a.png",
                                "image/png",
                                pngHeader);

                UploadResultDto result = fileInfoService.uploadFile(
                                webUserDto,
                                fileId,
                                file,
                                "a.png",
                                filePid,
                                fileMd5,
                                0,
                                2);

                assertNotNull(result);
                assertEquals(fileId, result.getFileId());
                assertEquals(UploadStatusEnums.UPLOADING.getCode(), result.getStatus());

                // If quick-upload path had been taken, it would insert a new FileInfo
                // immediately.
                verify(fileInfoMapper, never()).insert(any(FileInfo.class));
                verify(uploadRateLimiter).release(userId);
                verify(tenantQuotaService).reserveStorageQuota(eq(userId + ":" + fileId), eq("chunk:0"), anyLong());
        }
}
