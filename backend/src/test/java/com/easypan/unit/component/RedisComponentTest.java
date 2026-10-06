package com.easypan.unit.component;

import com.easypan.component.RedisComponent;
import com.easypan.component.RedisUtils;
import com.easypan.entity.constants.CacheTTL;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.DownloadFileDto;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.mappers.UserInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("RedisComponent 单元测试")
class RedisComponentTest {

    @Mock
    private RedisUtils<Object> redisUtils;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private FileInfoMapper fileInfoMapper;

    @InjectMocks
    private RedisComponent redisComponent;

    private String testUserId;
    private String testFileId;
    private SysSettingsDto testSysSettings;
    private UserSpaceDto testUserSpace;
    private UserInfo testUserInfo;

    @BeforeEach
    void setUp() {
        testUserId = "user_123";
        testFileId = "file_456";

        testSysSettings = new SysSettingsDto();
        testSysSettings.setUserInitUseSpace(5);

        testUserSpace = new UserSpaceDto();
        testUserSpace.setUseSpace(1024L * 1024L);
        testUserSpace.setTotalSpace(5L * 1024L * 1024L);

        testUserInfo = new UserInfo();
        testUserInfo.setUserId(testUserId);
        testUserInfo.setTotalSpace(10L * 1024L * 1024L);
    }

    @Test
    @DisplayName("获取系统设置 - 缓存命中")
    void testGetSysSettingsDto_CacheHit() {
        when(redisUtils.get(Constants.REDIS_KEY_SYS_SETTING)).thenReturn(testSysSettings);

        SysSettingsDto result = redisComponent.getSysSettingsDto();

        assertNotNull(result);
        assertEquals(testSysSettings.getUserInitUseSpace(), result.getUserInitUseSpace());
        verify(redisUtils, times(1)).get(Constants.REDIS_KEY_SYS_SETTING);
        verify(redisUtils, never()).setex(anyString(), any(), anyLong());
    }

    @Test
    @DisplayName("获取系统设置 - 缓存未命中，创建默认值")
    void testGetSysSettingsDto_CacheMiss() {
        when(redisUtils.get(Constants.REDIS_KEY_SYS_SETTING)).thenReturn(null);

        SysSettingsDto result = redisComponent.getSysSettingsDto();

        assertNotNull(result);
        verify(redisUtils, times(1)).get(Constants.REDIS_KEY_SYS_SETTING);
        verify(redisUtils, times(1)).setex(eq(Constants.REDIS_KEY_SYS_SETTING), any(SysSettingsDto.class), eq((long) CacheTTL.SYS_CONFIG));
    }

    @Test
    @DisplayName("保存系统设置")
    void testSaveSysSettingsDto() {
        redisComponent.saveSysSettingsDto(testSysSettings);

        verify(redisUtils, times(1)).setex(Constants.REDIS_KEY_SYS_SETTING, testSysSettings, CacheTTL.SYS_CONFIG);
    }

    @Test
    @DisplayName("保存下载码")
    void testSaveDownloadCode() {
        String code = "download_123";
        DownloadFileDto downloadDto = new DownloadFileDto();
        downloadDto.setFileName("test.txt");
        downloadDto.setFilePath("/files/test.txt");

        redisComponent.saveDownloadCode(code, downloadDto);

        verify(redisUtils, times(1)).setex(
                eq(Constants.REDIS_KEY_DOWNLOAD + code),
                eq(downloadDto),
                eq((long) Constants.REDIS_KEY_EXPIRES_FIVE_MIN)
        );
    }

    @Test
    @DisplayName("获取下载码")
    void testGetDownloadCode() {
        String code = "download_123";
        DownloadFileDto downloadDto = new DownloadFileDto();
        downloadDto.setFileName("test.txt");
        when(redisUtils.get(Constants.REDIS_KEY_DOWNLOAD + code)).thenReturn(downloadDto);

        DownloadFileDto result = redisComponent.getDownloadCode(code);

        assertNotNull(result);
        assertEquals("test.txt", result.getFileName());
        verify(redisUtils, times(1)).get(Constants.REDIS_KEY_DOWNLOAD + code);
    }

    @Test
    @DisplayName("获取下载码 - 不存在")
    void testGetDownloadCode_NotFound() {
        String code = "invalid_code";
        when(redisUtils.get(Constants.REDIS_KEY_DOWNLOAD + code)).thenReturn(null);

        DownloadFileDto result = redisComponent.getDownloadCode(code);

        assertNull(result);
    }

    @Test
    @DisplayName("获取用户空间使用情况 - 缓存命中")
    void testGetUserSpaceUse_CacheHit() {
        when(redisUtils.get(Constants.REDIS_KEY_USER_SPACE_USE + testUserId)).thenReturn(testUserSpace);

        UserSpaceDto result = redisComponent.getUserSpaceUse(testUserId);

        assertNotNull(result);
        assertEquals(testUserSpace.getUseSpace(), result.getUseSpace());
        assertEquals(testUserSpace.getTotalSpace(), result.getTotalSpace());
        verify(fileInfoMapper, never()).selectUseSpace(anyString());
    }

    @Test
    @DisplayName("获取用户空间使用情况 - 缓存未命中")
    void testGetUserSpaceUse_CacheMiss() {
        Long usedSpace = 2L * 1024L * 1024L;
        when(redisUtils.get(Constants.REDIS_KEY_USER_SPACE_USE + testUserId)).thenReturn(null);
        when(fileInfoMapper.selectUseSpace(testUserId)).thenReturn(usedSpace);
        when(redisUtils.get(Constants.REDIS_KEY_SYS_SETTING)).thenReturn(testSysSettings);

        UserSpaceDto result = redisComponent.getUserSpaceUse(testUserId);

        assertNotNull(result);
        assertEquals(usedSpace, result.getUseSpace());
        assertEquals(5L * Constants.MB, result.getTotalSpace());
        verify(fileInfoMapper, times(1)).selectUseSpace(testUserId);
        verify(redisUtils, times(1)).setex(
                eq(Constants.REDIS_KEY_USER_SPACE_USE + testUserId),
                any(UserSpaceDto.class),
                eq((long) CacheTTL.WARM_DATA)
        );
    }

    @Test
    @DisplayName("保存用户空间使用情况")
    void testSaveUserSpaceUse() {
        redisComponent.saveUserSpaceUse(testUserId, testUserSpace);

        verify(redisUtils, times(1)).setex(
                eq(Constants.REDIS_KEY_USER_SPACE_USE + testUserId),
                eq(testUserSpace),
                eq((long) CacheTTL.WARM_DATA)
        );
    }

    @Test
    @DisplayName("重置用户空间使用情况")
    void testResetUserSpaceUse() {
        Long usedSpace = 3L * 1024L * 1024L;
        when(fileInfoMapper.selectUseSpace(testUserId)).thenReturn(usedSpace);
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testUserInfo);

        UserSpaceDto result = redisComponent.resetUserSpaceUse(testUserId);

        assertNotNull(result);
        assertEquals(usedSpace, result.getUseSpace());
        assertEquals(testUserInfo.getTotalSpace(), result.getTotalSpace());
        verify(fileInfoMapper, times(1)).selectUseSpace(testUserId);
        verify(userInfoMapper, times(1)).selectOneByQuery(any(QueryWrapper.class));
        verify(redisUtils, times(1)).setex(
                eq(Constants.REDIS_KEY_USER_SPACE_USE + testUserId),
                any(UserSpaceDto.class),
                eq((long) CacheTTL.WARM_DATA)
        );
    }

    @Test
    @DisplayName("保存文件临时大小 - 首次保存")
    void testSaveFileTempSize_FirstTime() {
        Long fileSize = 1024L * 1024L;
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(null);

        redisComponent.saveFileTempSize(testUserId, testFileId, fileSize);

        verify(redisUtils, times(1)).get(key);
        verify(redisUtils, times(1)).setex(
                eq(key),
                eq(fileSize),
                eq((long) Constants.REDIS_KEY_EXPIRES_ONE_HOUR)
        );
    }

    @Test
    @DisplayName("保存文件临时大小 - 累加")
    void testSaveFileTempSize_Accumulate() {
        Long existingSize = 1024L * 1024L;
        Long additionalSize = 512L * 1024L;
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(existingSize);

        redisComponent.saveFileTempSize(testUserId, testFileId, additionalSize);

        verify(redisUtils, times(1)).get(key);
        verify(redisUtils, times(1)).setex(
                eq(key),
                eq(existingSize + additionalSize),
                eq((long) Constants.REDIS_KEY_EXPIRES_ONE_HOUR)
        );
    }

    @Test
    @DisplayName("获取文件临时大小 - 存在")
    void testGetFileTempSize_Exists() {
        Long fileSize = 2L * 1024L * 1024L;
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(fileSize);

        Long result = redisComponent.getFileTempSize(testUserId, testFileId);

        assertEquals(fileSize, result);
    }

    @Test
    @DisplayName("获取文件临时大小 - 不存在")
    void testGetFileTempSize_NotExists() {
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(null);

        Long result = redisComponent.getFileTempSize(testUserId, testFileId);

        assertEquals(0L, result);
    }

    @Test
    @DisplayName("获取文件临时大小 - Integer类型转换")
    void testGetFileTempSize_IntegerType() {
        Integer fileSize = 1024 * 1024;
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(fileSize);

        Long result = redisComponent.getFileTempSize(testUserId, testFileId);

        assertEquals(fileSize.longValue(), result);
    }

    @Test
    @DisplayName("获取用户空间 - 数据库返回null")
    void testGetUserSpaceUse_DatabaseReturnsNull() {
        when(redisUtils.get(Constants.REDIS_KEY_USER_SPACE_USE + testUserId)).thenReturn(null);
        when(fileInfoMapper.selectUseSpace(testUserId)).thenReturn(null);
        when(redisUtils.get(Constants.REDIS_KEY_SYS_SETTING)).thenReturn(testSysSettings);

        UserSpaceDto result = redisComponent.getUserSpaceUse(testUserId);

        assertNotNull(result);
        assertNull(result.getUseSpace());
        assertEquals(5L * Constants.MB, result.getTotalSpace());
    }

    @Test
    @DisplayName("保存文件临时大小 - 零大小")
    void testSaveFileTempSize_ZeroSize() {
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(null);

        redisComponent.saveFileTempSize(testUserId, testFileId, 0L);

        verify(redisUtils, times(1)).setex(
                eq(key),
                eq(0L),
                eq((long) Constants.REDIS_KEY_EXPIRES_ONE_HOUR)
        );
    }

    @Test
    @DisplayName("保存文件临时大小 - 大文件")
    void testSaveFileTempSize_LargeFile() {
        Long largeSize = 5L * 1024L * 1024L * 1024L;
        String key = Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + testUserId + testFileId;
        when(redisUtils.get(key)).thenReturn(null);

        redisComponent.saveFileTempSize(testUserId, testFileId, largeSize);

        verify(redisUtils, times(1)).setex(
                eq(key),
                eq(largeSize),
                eq((long) Constants.REDIS_KEY_EXPIRES_ONE_HOUR)
        );
    }

    @Test
    @DisplayName("添加 Token 到黑名单")
    void testAddBlacklistToken() {
        String token = "test_token";
        long expirationTime = 3600L;

        redisComponent.addBlacklistToken(token, expirationTime);

        verify(redisUtils).setex(Constants.REDIS_KEY_JWT_BLACKLIST + token, "", expirationTime);
    }

    @Test
    @DisplayName("检查 Token 是否在黑名单 - 已存在")
    void testIsTokenBlacklisted_Exists() {
        String token = "test_token";
        when(redisUtils.get(Constants.REDIS_KEY_JWT_BLACKLIST + token)).thenReturn("");

        boolean result = redisComponent.isTokenBlacklisted(token);

        assertTrue(result);
    }

    @Test
    @DisplayName("检查 Token 是否在黑名单 - 不存在")
    void testIsTokenBlacklisted_NotExists() {
        String token = "test_token";
        when(redisUtils.get(Constants.REDIS_KEY_JWT_BLACKLIST + token)).thenReturn(null);

        boolean result = redisComponent.isTokenBlacklisted(token);

        assertFalse(result);
    }

    @Test
    @DisplayName("保存刷新 Token")
    void testSaveRefreshToken() {
        String refreshToken = "refresh_token_123";
        long expirationTime = 86400L;

        redisComponent.saveRefreshToken(testUserId, refreshToken, expirationTime);

        verify(redisUtils).setex(Constants.REDIS_KEY_REFRESH_TOKEN + testUserId, refreshToken, expirationTime);
    }

    @Test
    @DisplayName("获取刷新 Token")
    void testGetRefreshToken() {
        String refreshToken = "refresh_token_123";
        when(redisUtils.get(Constants.REDIS_KEY_REFRESH_TOKEN + testUserId)).thenReturn(refreshToken);

        String result = redisComponent.getRefreshToken(testUserId);

        assertEquals(refreshToken, result);
    }

    @Test
    @DisplayName("验证刷新 Token - 成功")
    void testValidateRefreshToken_Success() {
        String refreshToken = "refresh_token_123";
        when(redisUtils.get(Constants.REDIS_KEY_REFRESH_TOKEN + testUserId)).thenReturn(refreshToken);

        boolean result = redisComponent.validateRefreshToken(testUserId, refreshToken);

        assertTrue(result);
    }

    @Test
    @DisplayName("验证刷新 Token - 失败")
    void testValidateRefreshToken_Failure() {
        String refreshToken = "refresh_token_123";
        String storedToken = "different_token";
        when(redisUtils.get(Constants.REDIS_KEY_REFRESH_TOKEN + testUserId)).thenReturn(storedToken);

        boolean result = redisComponent.validateRefreshToken(testUserId, refreshToken);

        assertFalse(result);
    }

    @Test
    @DisplayName("删除刷新 Token")
    void testDeleteRefreshToken() {
        redisComponent.deleteRefreshToken(testUserId);

        verify(redisUtils).delete(Constants.REDIS_KEY_REFRESH_TOKEN + testUserId);
    }
}
