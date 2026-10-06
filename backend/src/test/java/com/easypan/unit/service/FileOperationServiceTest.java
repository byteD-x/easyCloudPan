package com.easypan.unit.service;

import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.FileInfoService;
import com.easypan.service.FileOperationService;
import com.easypan.strategy.StorageStrategy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.task.AsyncTaskExecutor;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileOperationService 鍗曞厓娴嬭瘯")
class FileOperationServiceTest {

    @Mock
    private AsyncTaskExecutor virtualThreadExecutor;

    @Mock
    private FileInfoService fileInfoService;

    @Mock
    private FileInfoMapper fileInfoMapper;

    @Mock
    private StorageStrategy storageStrategy;

    @InjectMocks
    private FileOperationService fileOperationService;

    @Test
    @DisplayName("鎵归噺涓嬭浇: 瀛樺湪瓒婃潈 fileId 鏃跺簲鎷掔粷")
    void downloadMultipleFiles_shouldRejectUnauthorizedFileIds() throws Exception {
        FileInfo authorized = buildFile("f1", "u1", "a.txt", "path/a.txt");
        when(fileInfoMapper.selectListByQuery(any())).thenReturn(List.of(authorized));

        ByteArrayOutputStream out = new ByteArrayOutputStream();

        assertThrows(BusinessException.class,
                () -> fileOperationService.downloadMultipleFiles("u1", List.of("f1", "f2"), out));
    }

    @Test
    @DisplayName("鎵归噺涓嬭浇: 鍚屽悕鏂囦欢搴旇鑷姩鍘婚噸 entry")
    void downloadMultipleFiles_shouldZipAuthorizedFilesWithUniqueNames() throws Exception {
        FileInfo file1 = buildFile("f1", "u1", "same.txt", "path/1");
        FileInfo file2 = buildFile("f2", "u1", "same.txt", "path/2");
        when(fileInfoMapper.selectListByQuery(any())).thenReturn(List.of(file1, file2));
        when(storageStrategy.download("path/1"))
                .thenReturn(new ByteArrayInputStream("A".getBytes(StandardCharsets.UTF_8)));
        when(storageStrategy.download("path/2"))
                .thenReturn(new ByteArrayInputStream("B".getBytes(StandardCharsets.UTF_8)));

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        fileOperationService.downloadMultipleFiles("u1", List.of("f1", "f2"), out);

        try (ZipInputStream zipIn = new ZipInputStream(new ByteArrayInputStream(out.toByteArray()))) {
            ZipEntry first = zipIn.getNextEntry();
            assertNotNull(first);
            assertEquals("same.txt", first.getName());
            assertEquals("A", new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));

            ZipEntry second = zipIn.getNextEntry();
            assertNotNull(second);
            assertEquals("same(1).txt", second.getName());
            assertEquals("B", new String(zipIn.readAllBytes(), StandardCharsets.UTF_8));
        }
    }

    private FileInfo buildFile(String fileId, String userId, String fileName, String filePath) {
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(fileId);
        fileInfo.setUserId(userId);
        fileInfo.setFileName(fileName);
        fileInfo.setFilePath(filePath);
        fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        return fileInfo;
    }
}
