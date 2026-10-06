package com.easypan.unit.service;

import com.easypan.component.RedisComponent;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.po.EmailCode;
import com.easypan.entity.po.UserInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.EmailCodeMapper;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.impl.EmailCodeServiceImpl;
import com.mybatisflex.core.query.QueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import jakarta.mail.internet.MimeMessage;
import java.util.Date;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EmailCodeService 单元测试")
class EmailCodeServiceTest {

    @Mock
    private EmailCodeMapper emailCodeMapper;

    @Mock
    private JavaMailSender javaMailSender;

    @Mock
    private AppConfig appConfig;

    @Mock
    private UserInfoMapper userInfoMapper;

    @Mock
    private RedisComponent redisComponent;

    @Mock
    private MimeMessage mimeMessage;

    @InjectMocks
    private EmailCodeServiceImpl emailCodeService;

    private String testEmail;
    private String testCode;
    private EmailCode testEmailCode;
    private SysSettingsDto sysSettings;

    @BeforeEach
    void setUp() {
        testEmail = "test@example.com";
        testCode = "12345";

        testEmailCode = new EmailCode();
        testEmailCode.setEmail(testEmail);
        testEmailCode.setCode(testCode);
        testEmailCode.setStatus(Constants.ZERO);
        testEmailCode.setCreateTime(new Date());

        sysSettings = new SysSettingsDto();
        sysSettings.setRegisterEmailTitle("EasyCloudPan 注册验证码");
        sysSettings.setRegisterEmailContent("您的验证码是：%s，15分钟内有效");
    }

    @Test
    @DisplayName("发送注册验证码 - 成功")
    void testSendEmailCode_RegisterSuccess() {
        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomNumber(Constants.LENGTH_5))
                    .thenReturn(testCode);

            when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(appConfig.getSendUserName()).thenReturn("noreply@easypan.com");
            when(redisComponent.getSysSettingsDto()).thenReturn(sysSettings);
            when(emailCodeMapper.insert(any(EmailCode.class))).thenReturn(1);

            emailCodeService.sendEmailCode(testEmail, Constants.ZERO);

            verify(emailCodeMapper, times(1)).disableEmailCode(testEmail);
            verify(emailCodeMapper, times(1)).insert(any(EmailCode.class));
            verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    @Test
    @DisplayName("发送注册验证码 - 邮箱已存在")
    void testSendEmailCode_EmailAlreadyExists() {
        UserInfo existingUser = new UserInfo();
        existingUser.setEmail(testEmail);
        when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(existingUser);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            emailCodeService.sendEmailCode(testEmail, Constants.ZERO);
        });

        assertEquals("邮箱已经存在", exception.getMessage());
        verify(emailCodeMapper, never()).insert(any(EmailCode.class));
        verify(javaMailSender, never()).send(any(MimeMessage.class));
    }

    @Test
    @DisplayName("发送找回密码验证码 - 成功")
    void testSendEmailCode_ResetPasswordSuccess() {
        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomNumber(Constants.LENGTH_5))
                    .thenReturn(testCode);

            when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(appConfig.getSendUserName()).thenReturn("noreply@easypan.com");
            when(redisComponent.getSysSettingsDto()).thenReturn(sysSettings);
            when(emailCodeMapper.insert(any(EmailCode.class))).thenReturn(1);

            emailCodeService.sendEmailCode(testEmail, 1);

            verify(userInfoMapper, never()).selectOneByQuery(any(QueryWrapper.class));
            verify(emailCodeMapper, times(1)).disableEmailCode(testEmail);
            verify(emailCodeMapper, times(1)).insert(any(EmailCode.class));
            verify(javaMailSender, times(1)).send(any(MimeMessage.class));
        }
    }

    @Test
    @DisplayName("发送验证码 - 邮件发送失败")
    void testSendEmailCode_EmailSendFailure() {
        try (MockedStatic<com.easypan.utils.StringTools> stringToolsMock = mockStatic(com.easypan.utils.StringTools.class)) {
            stringToolsMock.when(() -> com.easypan.utils.StringTools.getRandomNumber(Constants.LENGTH_5))
                    .thenReturn(testCode);

            when(userInfoMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);
            when(javaMailSender.createMimeMessage()).thenReturn(mimeMessage);
            when(appConfig.getSendUserName()).thenReturn("noreply@easypan.com");
            when(redisComponent.getSysSettingsDto()).thenReturn(sysSettings);
            doThrow(new RuntimeException("SMTP connection failed"))
                    .when(javaMailSender).send(any(MimeMessage.class));

            BusinessException exception = assertThrows(BusinessException.class, () -> {
                emailCodeService.sendEmailCode(testEmail, Constants.ZERO);
            });

            assertEquals("邮件发送失败", exception.getMessage());
            verify(emailCodeMapper, never()).insert(any(EmailCode.class));
        }
    }

    @Test
    @DisplayName("校验验证码 - 成功")
    void testCheckCode_Success() {
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testEmailCode);

        emailCodeService.checkCode(testEmail, testCode);

        verify(emailCodeMapper, times(1)).disableEmailCode(testEmail);
    }

    @Test
    @DisplayName("校验验证码 - 验证码不存在")
    void testCheckCode_CodeNotFound() {
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            emailCodeService.checkCode(testEmail, testCode);
        });

        assertEquals("邮箱验证码不正确", exception.getMessage());
        verify(emailCodeMapper, never()).disableEmailCode(anyString());
    }

    @Test
    @DisplayName("校验验证码 - 验证码已使用")
    void testCheckCode_CodeAlreadyUsed() {
        testEmailCode.setStatus(1);
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testEmailCode);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            emailCodeService.checkCode(testEmail, testCode);
        });

        assertEquals("邮箱验证码已失效", exception.getMessage());
        verify(emailCodeMapper, never()).disableEmailCode(anyString());
    }

    @Test
    @DisplayName("校验验证码 - 验证码已过期（超过15分钟）")
    void testCheckCode_CodeExpired() {
        Date expiredTime = new Date(System.currentTimeMillis() - 16 * 60 * 1000);
        testEmailCode.setCreateTime(expiredTime);
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testEmailCode);

        BusinessException exception = assertThrows(BusinessException.class, () -> {
            emailCodeService.checkCode(testEmail, testCode);
        });

        assertEquals("邮箱验证码已失效", exception.getMessage());
        verify(emailCodeMapper, never()).disableEmailCode(anyString());
    }

    @Test
    @DisplayName("校验验证码 - 验证码在有效期内（14分钟）")
    void testCheckCode_WithinValidPeriod() {
        Date recentTime = new Date(System.currentTimeMillis() - 14 * 60 * 1000);
        testEmailCode.setCreateTime(recentTime);
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testEmailCode);

        emailCodeService.checkCode(testEmail, testCode);

        verify(emailCodeMapper, times(1)).disableEmailCode(testEmail);
    }

    @Test
    @DisplayName("根据邮箱和验证码查询")
    void testGetEmailCodeByEmailAndCode() {
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testEmailCode);

        EmailCode result = emailCodeService.getEmailCodeByEmailAndCode(testEmail, testCode);

        assertNotNull(result);
        assertEquals(testEmail, result.getEmail());
        assertEquals(testCode, result.getCode());
    }

    @Test
    @DisplayName("根据邮箱和验证码删除")
    void testDeleteEmailCodeByEmailAndCode() {
        when(emailCodeMapper.deleteByQuery(any(QueryWrapper.class))).thenReturn(1);

        Integer result = emailCodeService.deleteEmailCodeByEmailAndCode(testEmail, testCode);

        assertEquals(1, result);
        verify(emailCodeMapper, times(1)).deleteByQuery(any(QueryWrapper.class));
    }

    @Test
    @DisplayName("新增验证码")
    void testAdd() {
        when(emailCodeMapper.insert(testEmailCode)).thenReturn(1);

        Integer result = emailCodeService.add(testEmailCode);

        assertEquals(1, result);
        verify(emailCodeMapper, times(1)).insert(testEmailCode);
    }

    @Test
    @DisplayName("校验验证码 - 刚好15分钟边界")
    void testCheckCode_ExactlyFifteenMinutes() {
        Date exactTime = new Date(System.currentTimeMillis() - 15 * 60 * 1000 + 1000);
        testEmailCode.setCreateTime(exactTime);
        when(emailCodeMapper.selectOneByQuery(any(QueryWrapper.class))).thenReturn(testEmailCode);

        emailCodeService.checkCode(testEmail, testCode);

        verify(emailCodeMapper, times(1)).disableEmailCode(testEmail);
    }

    @Test
    @DisplayName("更新验证码")
    void testUpdateEmailCodeByEmailAndCode() {
        EmailCode updateCode = new EmailCode();
        updateCode.setStatus(1);
        when(emailCodeMapper.updateByQuery(any(EmailCode.class), any(QueryWrapper.class))).thenReturn(1);

        Integer result = emailCodeService.updateEmailCodeByEmailAndCode(updateCode, testEmail, testCode);

        assertEquals(1, result);
        verify(emailCodeMapper).updateByQuery(any(EmailCode.class), any(QueryWrapper.class));
    }
}
