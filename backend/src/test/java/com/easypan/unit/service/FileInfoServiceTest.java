package com.easypan.unit.service;

import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.enums.FileStatusEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.service.MultiLevelCacheService;
import com.easypan.service.impl.FileInfoServiceImpl;
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

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileInfoService 单元测试")
class FileInfoServiceTest {

    @Mock
    private FileInfoMapper fileInfoMapper;

    @Mock
    private MultiLevelCacheService multiLevelCacheService;

    @InjectMocks
    private FileInfoServiceImpl fileInfoService;

    private static final String TEST_USER_ID = "user123";
    private static final String TEST_FOLDER_NAME = "测试文件夹";
    private static final String TEST_FILE_PID = "0";
    private static final String TEST_FILE_ID = "file123";

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("创建文件夹 - 正常场景")
    void testCreateFolder_Success() {
        when(fileInfoMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 1L);
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(10))
                .thenReturn(TEST_FILE_ID);

            FileInfo result = fileInfoService.newFolder(TEST_FILE_PID, TEST_USER_ID, TEST_FOLDER_NAME);

            assertNotNull(result);
            assertEquals(TEST_FOLDER_NAME, result.getFileName());
            assertEquals(TEST_USER_ID, result.getUserId());
            assertEquals(TEST_FILE_PID, result.getFilePid());
            assertEquals(FileFolderTypeEnums.FOLDER.getType(), result.getFolderType());
            assertEquals(FileStatusEnums.USING.getStatus(), result.getStatus());
            assertEquals(FileDelFlagEnums.USING.getFlag(), result.getDelFlag());
            assertNotNull(result.getCreateTime());
            assertNotNull(result.getLastUpdateTime());

            ArgumentCaptor<FileInfo> fileCaptor = ArgumentCaptor.forClass(FileInfo.class);
            verify(fileInfoMapper).insert(fileCaptor.capture());

            FileInfo insertedFile = fileCaptor.getValue();
            assertEquals(TEST_FILE_ID, insertedFile.getFileId());
            assertEquals(TEST_FOLDER_NAME, insertedFile.getFileName());
            assertEquals(FileFolderTypeEnums.FOLDER.getType(), insertedFile.getFolderType());
        }
    }

    @Test
    @DisplayName("创建文件夹 - 文件夹名称已存在")
    void testCreateFolder_DuplicateName() {
        when(fileInfoMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(1L);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileInfoService.newFolder(TEST_FILE_PID, TEST_USER_ID, TEST_FOLDER_NAME);
        });

        assertEquals("此目录下已存在同名文件，请修改名称", exception.getMessage());
        verify(fileInfoMapper, never()).insert(any(FileInfo.class));
    }

    @Test
    @DisplayName("创建文件夹 - 空文件夹名称")
    void testCreateFolder_EmptyName() {
        String emptyName = "";

        when(fileInfoMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 1L);
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(10))
                .thenReturn(TEST_FILE_ID);

            FileInfo result = fileInfoService.newFolder(TEST_FILE_PID, TEST_USER_ID, emptyName);

            assertNotNull(result);
            assertEquals(emptyName, result.getFileName());
        }
    }

    @Test
    @DisplayName("创建文件夹 - 验证文件夹类型正确")
    void testCreateFolder_VerifyFolderType() {
        when(fileInfoMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 1L);
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(10))
                .thenReturn(TEST_FILE_ID);

            FileInfo result = fileInfoService.newFolder(TEST_FILE_PID, TEST_USER_ID, TEST_FOLDER_NAME);

            assertEquals(1, result.getFolderType());
            verify(fileInfoMapper, atLeastOnce()).selectCountByQuery(any(QueryWrapper.class));
        }
    }

    @Test
    @DisplayName("删除文件 - 正常场景")
    void testDeleteFile_Success() {
        when(fileInfoMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

        Integer result = fileInfoService.deleteFileInfoByFileIdAndUserId(TEST_FILE_ID, TEST_USER_ID);

        assertEquals(1, result);
        verify(fileInfoMapper).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("删除文件 - 文件不存在")
    void testDeleteFile_FileNotExists() {
        when(fileInfoMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(0);

        Integer result = fileInfoService.deleteFileInfoByFileIdAndUserId(TEST_FILE_ID, TEST_USER_ID);

        assertEquals(0, result);
        verify(fileInfoMapper).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("删除文件 - 验证用户隔离")
    void testDeleteFile_UserIsolation() {
        String otherUserId = "user456";
        when(fileInfoMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(0);

        Integer result = fileInfoService.deleteFileInfoByFileIdAndUserId(TEST_FILE_ID, otherUserId);

        assertEquals(0, result);
        verify(fileInfoMapper).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("移动文件 - 移动到自身")
    void testMoveFile_MoveToSelf() {
        String fileId = TEST_FILE_ID;

        assertThrows(BusinessException.class, () -> {
            fileInfoService.changeFileFolder(fileId, fileId, TEST_USER_ID);
        });

        verify(fileInfoMapper, never()).updateByQuery(any(FileInfo.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("移动文件 - 验证文件ID和目标ID相同时抛出异常")
    void testMoveFile_SameIdValidation() {
        String sameId = "same123";

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileInfoService.changeFileFolder(sameId, sameId, TEST_USER_ID);
        });

        assertNotNull(exception);
        verify(fileInfoMapper, never()).updateByQuery(any(FileInfo.class), any(QueryWrapper.class));
        verify(fileInfoMapper, never()).selectCountByQuery(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("根据文件ID和用户ID查询文件")
    void testGetFileInfoByFileIdAndUserId() {
        FileInfo file = createTestFile(TEST_FILE_ID, TEST_USER_ID, "test.txt", TEST_FILE_PID);
        when(multiLevelCacheService.getFileInfo(TEST_FILE_ID, TEST_USER_ID)).thenReturn(file);

        FileInfo result = fileInfoService.getFileInfoByFileIdAndUserId(TEST_FILE_ID, TEST_USER_ID);

        assertNotNull(result);
        assertEquals(TEST_FILE_ID, result.getFileId());
        assertEquals(TEST_USER_ID, result.getUserId());
        verify(multiLevelCacheService).getFileInfo(TEST_FILE_ID, TEST_USER_ID);
    }

    @Test
    @DisplayName("根据文件ID和用户ID查询文件 - 文件不存在")
    void testGetFileInfoByFileIdAndUserId_NotFound() {
        when(multiLevelCacheService.getFileInfo(TEST_FILE_ID, TEST_USER_ID)).thenReturn(null);

        FileInfo result = fileInfoService.getFileInfoByFileIdAndUserId(TEST_FILE_ID, TEST_USER_ID);

        assertNull(result);
        verify(multiLevelCacheService).getFileInfo(TEST_FILE_ID, TEST_USER_ID);
    }

    @Test
    @DisplayName("更新文件信息")
    void testUpdateFileInfoByFileIdAndUserId() {
        FileInfo updateInfo = new FileInfo();
        updateInfo.setFileName("new_name.txt");
        when(fileInfoMapper.updateByQuery(any(FileInfo.class), any(QueryWrapper.class))).thenReturn(1);

        Integer result = fileInfoService.updateFileInfoByFileIdAndUserId(updateInfo, TEST_FILE_ID, TEST_USER_ID);

        assertEquals(1, result);
        verify(multiLevelCacheService).evictFileInfo(TEST_FILE_ID, TEST_USER_ID);
        verify(fileInfoMapper).updateByQuery(any(FileInfo.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("新增文件")
    void testAdd() {
        FileInfo file = createTestFile(TEST_FILE_ID, TEST_USER_ID, "test.txt", TEST_FILE_PID);
        when(fileInfoMapper.insert(any(FileInfo.class))).thenReturn(1);

        Integer result = fileInfoService.add(file);

        assertEquals(1, result);
        verify(fileInfoMapper).insert(file);
    }

    @Test
    @DisplayName("重命名文件 - 成功")
    void testRename_Success() {
        FileInfo existingFile = createTestFile(TEST_FILE_ID, TEST_USER_ID, "old_name.txt", TEST_FILE_PID);
        when(multiLevelCacheService.getFileInfo(TEST_FILE_ID, TEST_USER_ID)).thenReturn(existingFile);
        when(fileInfoMapper.selectCountByQuery(any(QueryWrapper.class))).thenReturn(0L, 1L);
        when(fileInfoMapper.updateByQuery(any(FileInfo.class), any(QueryWrapper.class))).thenReturn(1);

        FileInfo result = fileInfoService.rename(TEST_FILE_ID, TEST_USER_ID, "new_name");

        assertNotNull(result);
        verify(fileInfoMapper).updateByQuery(any(FileInfo.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("重命名文件 - 文件不存在")
    void testRename_FileNotFound() {
        when(multiLevelCacheService.getFileInfo(TEST_FILE_ID, TEST_USER_ID)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileInfoService.rename(TEST_FILE_ID, TEST_USER_ID, "new_name");
        });

        assertEquals("文件不存在", exception.getMessage());
    }

    @Test
    @DisplayName("重命名文件 - 名称相同")
    void testRename_SameName() {
        FileInfo existingFile = createTestFile(TEST_FILE_ID, TEST_USER_ID, "same_name", TEST_FILE_PID);
        existingFile.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        when(multiLevelCacheService.getFileInfo(TEST_FILE_ID, TEST_USER_ID)).thenReturn(existingFile);

        FileInfo result = fileInfoService.rename(TEST_FILE_ID, TEST_USER_ID, "same_name");

        assertNotNull(result);
        assertEquals("same_name", result.getFileName());
        verify(fileInfoMapper, never()).updateByQuery(any(FileInfo.class), any(QueryWrapper.class));
    }

    private FileInfo createTestFile(String fileId, String userId, String fileName, String filePid) {
        FileInfo file = new FileInfo();
        file.setFileId(fileId);
        file.setUserId(userId);
        file.setFileName(fileName);
        file.setFilePid(filePid);
        file.setFolderType(0);
        file.setFileSize(1024L);
        file.setFileCategory(4);
        file.setFileType(7);
        file.setStatus(FileStatusEnums.USING.getStatus());
        file.setDelFlag(FileDelFlagEnums.USING.getFlag());
        file.setCreateTime(new Date());
        file.setLastUpdateTime(new Date());
        return file;
    }
}
