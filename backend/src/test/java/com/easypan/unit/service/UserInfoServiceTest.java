package com.easypan.unit.service;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.EmailCodeService;
import com.easypan.service.FileInfoService;
import com.easypan.service.TenantQuotaService;
import com.easypan.service.impl.UserInfoServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("UserInfoService 单元测试")
class UserInfoServiceTest {

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private EmailCodeService emailCodeService;

    @Mock
    private FileInfoService fileInfoService;

    @Mock
    private RedisComponent redisComponent;

    @Mock
    private AppConfig appConfig;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private TenantQuotaService tenantQuotaService;

    @InjectMocks
    private UserInfoServiceImpl userInfoService;

    private static final String TEST_EMAIL = "test@example.com";
    private static final String TEST_NICKNAME = "TestUser";
    private static final String TEST_PASSWORD = "password123";
    private static final String TEST_EMAIL_CODE = "123456";
    private static final String TEST_USER_ID = "1234567890";

    private UserInfo createTestUser() {
        UserInfo user = new UserInfo();
        user.setUserId(TEST_USER_ID);
        user.setEmail(TEST_EMAIL);
        user.setNickName(TEST_NICKNAME);
        user.setPassword("$2a$10$encoded_bcrypt_password");
        user.setStatus(UserStatusEnum.ENABLE.getStatus());
        user.setTotalSpace(5 * 1024 * 1024L);
        user.setUseSpace(0L);
        user.setJoinTime(new Date());
        return user;
    }

    @Test
    @DisplayName("修改密码使用密码编码器并失效用户缓存")
    void testUpdatePassword_UsesPasswordEncoder() {
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("$2a$10$strong-hash");
        when(userInfoMapper.updateByQuery(any(UserInfo.class), any(QueryWrapper.class))).thenReturn(1);

        Integer updated = userInfoService.updatePassword(TEST_USER_ID, TEST_PASSWORD);

        assertEquals(1, updated);
        ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoMapper).updateByQuery(userCaptor.capture(), any(QueryWrapper.class));
        assertEquals("$2a$10$strong-hash", userCaptor.getValue().getPassword());
        verify(passwordEncoder).encode(TEST_PASSWORD);
        verify(redisComponent).deleteUserInfo(TEST_USER_ID);
    }

    @Test
    @DisplayName("用户注册成功")
    void testRegister_Success() {
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
        doNothing().when(emailCodeService).checkCode(TEST_EMAIL, TEST_EMAIL_CODE);
        doNothing().when(tenantQuotaService).checkUserQuota();
        
        SysSettingsDto sysSettings = new SysSettingsDto();
        sysSettings.setUserInitUseSpace(5);
        when(redisComponent.getSysSettingsDto()).thenReturn(sysSettings);
        
        when(passwordEncoder.encode(TEST_PASSWORD)).thenReturn("encoded_password");
        when(userInfoMapper.insert(any(UserInfo.class))).thenReturn(1);

        userInfoService.register(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD, TEST_EMAIL_CODE);

        verify(emailCodeService).checkCode(TEST_EMAIL, TEST_EMAIL_CODE);
        ArgumentCaptor<UserInfo> userCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoMapper).insert(userCaptor.capture());
        
        UserInfo insertedUser = userCaptor.getValue();
        assertNotNull(insertedUser.getUserId());
        assertEquals(TEST_NICKNAME, insertedUser.getNickName());
        assertEquals(TEST_EMAIL, insertedUser.getEmail());
        assertEquals("encoded_password", insertedUser.getPassword());
        assertEquals(UserStatusEnum.ENABLE.getStatus(), insertedUser.getStatus());
        assertEquals(5 * 1024 * 1024L, insertedUser.getTotalSpace());
        assertEquals(0L, insertedUser.getUseSpace());
        assertNotNull(insertedUser.getJoinTime());
    }

    @Test
    @DisplayName("用户注册失败 - 邮箱已存在")
    void testRegister_EmailExists() {
        doNothing().when(tenantQuotaService).checkUserQuota();
        UserInfo existingUser = createTestUser();
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existingUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userInfoService.register(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD, TEST_EMAIL_CODE);
        });
        
        assertEquals("邮箱账号已经存在", exception.getMessage());
        verify(userInfoMapper, never()).insert(any(UserInfo.class));
    }

    @Test
    @DisplayName("用户注册失败 - 昵称已存在")
    void testRegister_NicknameExists() {
        doNothing().when(tenantQuotaService).checkUserQuota();
        
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class)))
            .thenReturn(null)
            .thenReturn(createTestUser());

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userInfoService.register(TEST_EMAIL, TEST_NICKNAME, TEST_PASSWORD, TEST_EMAIL_CODE);
        });
        
        assertEquals("昵称已经存在", exception.getMessage());
        verify(userInfoMapper, never()).insert(any(UserInfo.class));
    }

    @Test
    @DisplayName("用户登录成功 - BCrypt 密码")
    void testLogin_Success_BCrypt() {
        UserInfo userInfo = createTestUser();
        
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(userInfo);
        when(passwordEncoder.matches(TEST_PASSWORD, userInfo.getPassword())).thenReturn(true);
        when(userInfoMapper.updateByQuery(any(UserInfo.class), any(QueryWrapper.class))).thenReturn(1);
        when(appConfig.getAdminEmails()).thenReturn("admin@example.com");
        when(fileInfoService.getUserUseSpace(TEST_USER_ID)).thenReturn(1024L);
        doNothing().when(redisComponent).saveUserSpaceUse(eq(TEST_USER_ID), any(UserSpaceDto.class));

        SessionWebUserDto session = userInfoService.login(TEST_EMAIL, TEST_PASSWORD);

        assertNotNull(session);
        assertEquals(TEST_NICKNAME, session.getNickName());
        assertEquals(TEST_USER_ID, session.getUserId());
        assertFalse(session.getAdmin());

        ArgumentCaptor<UserInfo> updateCaptor = ArgumentCaptor.forClass(UserInfo.class);
        verify(userInfoMapper).updateByQuery(updateCaptor.capture(), any(QueryWrapper.class));
        assertNotNull(updateCaptor.getValue().getLastLoginTime());
        
        ArgumentCaptor<UserSpaceDto> spaceCaptor = ArgumentCaptor.forClass(UserSpaceDto.class);
        verify(redisComponent).saveUserSpaceUse(eq(TEST_USER_ID), spaceCaptor.capture());
        assertEquals(1024L, spaceCaptor.getValue().getUseSpace());
        assertEquals(5 * 1024 * 1024L, spaceCaptor.getValue().getTotalSpace());
    }

    @Test
    @DisplayName("用户登录失败 - 账号不存在")
    void testLogin_Fail_UserNotExists() {
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userInfoService.login(TEST_EMAIL, TEST_PASSWORD);
        });
        
        assertEquals("账号或者密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("用户登录失败 - 密码错误")
    void testLogin_Fail_WrongPassword() {
        UserInfo userInfo = createTestUser();
        
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(userInfo);
        when(passwordEncoder.matches(TEST_PASSWORD, userInfo.getPassword())).thenReturn(false);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userInfoService.login(TEST_EMAIL, TEST_PASSWORD);
        });
        
        assertEquals("账号或者密码错误", exception.getMessage());
    }

    @Test
    @DisplayName("用户登录失败 - 账号已禁用")
    void testLogin_Fail_AccountDisabled() {
        UserInfo userInfo = createTestUser();
        userInfo.setStatus(UserStatusEnum.DISABLE.getStatus());
        
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(userInfo);
        when(passwordEncoder.matches(TEST_PASSWORD, userInfo.getPassword())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            userInfoService.login(TEST_EMAIL, TEST_PASSWORD);
        });
        
        assertEquals("账号已禁用", exception.getMessage());
    }

    @Test
    @DisplayName("修改用户空间成功")
    void testChangeUserSpace_Success() {
        Integer changeSpace = 10;
        UserInfo userInfo = createTestUser();
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(userInfo);
        when(userInfoMapper.updateTotalSpace(eq(TEST_USER_ID), anyLong())).thenReturn(1);

        userInfoService.changeUserSpace(TEST_USER_ID, changeSpace);

        verify(userInfoMapper).selectOneByQuery(any(QueryWrapper.class));
        verify(userInfoMapper).updateTotalSpace(eq(TEST_USER_ID), anyLong());
        verify(redisComponent).resetUserSpaceUse(TEST_USER_ID);
    }

    @Test
    @DisplayName("根据用户ID查询用户")
    void testGetUserInfoByUserId() {
        UserInfo user = createTestUser();
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);

        UserInfo result = userInfoService.getUserInfoByUserId(TEST_USER_ID);

        assertNotNull(result);
        assertEquals(TEST_USER_ID, result.getUserId());
        assertEquals(TEST_EMAIL, result.getEmail());
    }

    @Test
    @DisplayName("根据邮箱查询用户")
    void testGetUserInfoByEmail() {
        UserInfo user = createTestUser();
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(user);

        UserInfo result = userInfoService.getUserInfoByEmail(TEST_EMAIL);

        assertNotNull(result);
        assertEquals(TEST_EMAIL, result.getEmail());
    }

    @Test
    @DisplayName("更新用户信息")
    void testUpdateUserInfoByUserId() {
        UserInfo updateInfo = new UserInfo();
        updateInfo.setNickName("NewNickName");
        when(userInfoMapper.updateByQuery(any(UserInfo.class), any(QueryWrapper.class))).thenReturn(1);

        Integer result = userInfoService.updateUserInfoByUserId(updateInfo, TEST_USER_ID);

        assertEquals(1, result);
        verify(userInfoMapper).updateByQuery(any(UserInfo.class), any(QueryWrapper.class));
    }

    @Test
    @DisplayName("删除用户")
    void testDeleteUserInfoByUserId() {
        when(userInfoMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

        Integer result = userInfoService.deleteUserInfoByUserId(TEST_USER_ID);

        assertEquals(1, result);
        verify(userInfoMapper).deleteByQuery(any(QueryWrapper.class));
    }
}
