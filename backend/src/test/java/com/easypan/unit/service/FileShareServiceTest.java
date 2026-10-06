package com.easypan.unit.service;

import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionShareDto;
import com.easypan.entity.po.FileShare;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.FileShareMapper;
import com.easypan.service.impl.FileShareServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;
import java.util.Date;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FileShareService 单元测试")
class FileShareServiceTest {

    @Mock
    private FileShareMapper fileShareMapper;

    @InjectMocks
    private FileShareServiceImpl fileShareService;

    private FileShare testShare;
    private String testShareId;
    private String testUserId;
    private String testFileId;

    @BeforeEach
    void setUp() {
        testShareId = "test_share_12345";
        testUserId = "user_123";
        testFileId = "file_456";

        testShare = new FileShare();
        testShare.setShareId(testShareId);
        testShare.setUserId(testUserId);
        testShare.setFileId(testFileId);
        testShare.setCode("12345");
        testShare.setValidType(0);
        testShare.setShareTime(new Date());
        testShare.setExpireTime(new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000));
        testShare.setShowCount(0);
    }

    @Test
    @DisplayName("创建分享 - 1天有效期")
    void testSaveShare_OneDayValidity() {
        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class);
             MockedStatic<com.easypan.utils.DateUtil> dateUtilMock = mockStatic(com.easypan.utils.DateUtil.class)) {

            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(Constants.LENGTH_5))
                    .thenReturn("12345");
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(Constants.LENGTH_20))
                    .thenReturn(testShareId);
            stringToolsMock.when(() -> com.easypan.utils.StringTools.isEmpty(any()))
                    .thenReturn(true);

            Date expectedExpireTime = new Date(System.currentTimeMillis() + 24 * 60 * 60 * 1000);
            dateUtilMock.when(() -> com.easypan.utils.DateUtil.getAfterDate(1))
                    .thenReturn(expectedExpireTime);

            FileShare newShare = new FileShare();
            newShare.setUserId(testUserId);
            newShare.setFileId(testFileId);
            newShare.setValidType(0);

            when(fileShareMapper.insert(any(FileShare.class))).thenReturn(1);

            fileShareService.saveShare(newShare);

            verify(fileShareMapper, times(1)).insert(any(FileShare.class));
            assertEquals(testShareId, newShare.getShareId());
            assertEquals("12345", newShare.getCode());
            assertNotNull(newShare.getShareTime());
            assertNotNull(newShare.getExpireTime());
        }
    }

    @Test
    @DisplayName("创建分享 - 永久有效")
    void testSaveShare_ForeverValidity() {
        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {

            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(Constants.LENGTH_5))
                    .thenReturn("12345");
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomString(Constants.LENGTH_20))
                    .thenReturn(testShareId);
            stringToolsMock.when(() -> com.easypan.utils.StringTools.isEmpty(any()))
                    .thenReturn(true);

            FileShare newShare = new FileShare();
            newShare.setUserId(testUserId);
            newShare.setFileId(testFileId);
            newShare.setValidType(3);

            when(fileShareMapper.insert(any(FileShare.class))).thenReturn(1);

            fileShareService.saveShare(newShare);

            verify(fileShareMapper, times(1)).insert(any(FileShare.class));
            assertNull(newShare.getExpireTime());
        }
    }

    @Test
    @DisplayName("创建分享 - 无效的有效期类型")
    void testSaveShare_InvalidValidityType() {
        FileShare newShare = new FileShare();
        newShare.setUserId(testUserId);
        newShare.setFileId(testFileId);
        newShare.setValidType(999);

        assertThrows(BusinessException.class, () -> {
            fileShareService.saveShare(newShare);
        });

        verify(fileShareMapper, never()).insert(any(FileShare.class));
    }

    @Test
    @DisplayName("验证分享码 - 成功")
    void testCheckShareCode_Success() {
        when(fileShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testShare);

        SessionShareDto result = fileShareService.checkShareCode(testShareId, "12345");

        assertNotNull(result);
        assertEquals(testShareId, result.getShareId());
        assertEquals(testUserId, result.getShareUserId());
        assertEquals(testFileId, result.getFileId());
        verify(fileShareMapper, times(1)).updateShareShowCount(testShareId);
    }

    @Test
    @DisplayName("验证分享码 - 分享不存在")
    void testCheckShareCode_ShareNotFound() {
        when(fileShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        assertThrows(BusinessException.class, () -> {
            fileShareService.checkShareCode(testShareId, "12345");
        });

        verify(fileShareMapper, never()).updateShareShowCount(anyString());
    }

    @Test
    @DisplayName("验证分享码 - 分享已过期")
    void testCheckShareCode_Expired() {
        testShare.setExpireTime(new Date(System.currentTimeMillis() - 24 * 60 * 60 * 1000));
        when(fileShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testShare);

        assertThrows(BusinessException.class, () -> {
            fileShareService.checkShareCode(testShareId, "12345");
        });

        verify(fileShareMapper, never()).updateShareShowCount(anyString());
    }

    @Test
    @DisplayName("验证分享码 - 提取码错误")
    void testCheckShareCode_WrongCode() {
        when(fileShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testShare);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            fileShareService.checkShareCode(testShareId, "wrong_code");
        });

        assertEquals("提取码错误", exception.getMessage());
        verify(fileShareMapper, never()).updateShareShowCount(anyString());
    }

    @Test
    @DisplayName("批量删除分享 - 成功")
    void testDeleteFileShareBatch_Success() {
        String[] shareIds = { "share1", "share2", "share3" };
        when(fileShareMapper.deleteFileShareBatch(shareIds, testUserId)).thenReturn(3);

        fileShareService.deleteFileShareBatch(shareIds, testUserId);

        verify(fileShareMapper, times(1)).deleteFileShareBatch(shareIds, testUserId);
    }

    @Test
    @DisplayName("批量删除分享 - 部分失败")
    void testDeleteFileShareBatch_PartialFailure() {
        String[] shareIds = { "share1", "share2", "share3" };
        when(fileShareMapper.deleteFileShareBatch(shareIds, testUserId)).thenReturn(2);

        assertThrows(BusinessException.class, () -> {
            fileShareService.deleteFileShareBatch(shareIds, testUserId);
        });

        verify(fileShareMapper, times(1)).deleteFileShareBatch(shareIds, testUserId);
    }

    @Test
    @DisplayName("根据ShareId查询分享")
    void testGetFileShareByShareId() {
        when(fileShareMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testShare);

        FileShare result = fileShareService.getFileShareByShareId(testShareId);

        assertNotNull(result);
        assertEquals(testShareId, result.getShareId());
        assertEquals(testUserId, result.getUserId());
    }

    @Test
    @DisplayName("根据ShareId删除分享")
    void testDeleteFileShareByShareId() {
        when(fileShareMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

        Integer result = fileShareService.deleteFileShareByShareId(testShareId);

        assertEquals(1, result);
        verify(fileShareMapper).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("批量新增分享 - 空列表")
    void testAddBatch_EmptyList() {
        Integer result = fileShareService.addBatch(null);

        assertEquals(Integer.valueOf(0), result);
        verify(fileShareMapper, never()).insertBatch(anyList());
    }

    @Test
    @DisplayName("批量新增分享 - 成功")
    void testAddBatch_Success() {
        List<FileShare> shareList = Arrays.asList(testShare);
        when(fileShareMapper.insertBatch(shareList)).thenReturn(1);

        Integer result = fileShareService.addBatch(shareList);

        assertEquals(1, result);
        verify(fileShareMapper, times(1)).insertBatch(shareList);
    }

    @Test
    @DisplayName("新增分享")
    void testAdd() {
        when(fileShareMapper.insert(testShare)).thenReturn(1);

        Integer result = fileShareService.add(testShare);

        assertEquals(1, result);
        verify(fileShareMapper).insert(testShare);
    }

    @Test
    @DisplayName("更新分享")
    void testUpdateFileShareByShareId() {
        FileShare updateShare = new FileShare();
        updateShare.setShowCount(10);
        when(fileShareMapper.updateByQuery(any(FileShare.class), any(QueryWrapper.class))).thenReturn(1);

        Integer result = fileShareService.updateFileShareByShareId(updateShare, testShareId);

        assertEquals(1, result);
        verify(fileShareMapper).updateByQuery(any(FileShare.class), any(QueryWrapper.class));
    }
}
