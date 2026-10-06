package com.easypan.unit.controller;

import com.easypan.controller.WebShareController;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionShareDto;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.FileShare;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.entity.vo.ShareInfoVO;
import com.easypan.metrics.CustomMetrics;
import com.easypan.service.FileInfoService;
import com.easypan.service.FileShareService;
import com.easypan.service.ShareAccessLogService;
import com.easypan.service.UserInfoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("WebShareController 鍗曞厓娴嬭瘯")
class WebShareControllerTest {

    @InjectMocks
    private WebShareController webShareController;

    @Mock
    private FileShareService fileShareService;

    @Mock
    private FileInfoService fileInfoService;

    @Mock
    private UserInfoService userInfoService;

    @Mock
    private ShareAccessLogService shareAccessLogService;

    @Mock
    private CustomMetrics customMetrics;

    @AfterEach
    void cleanupRequestContext() {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    @DisplayName("匿名访问 getShareLoginInfo 不应抛登录异常")
    void getShareLoginInfo_shouldWorkForAnonymousVisitor() {
        String shareId = "SHARE_001";
        String ownerId = "OWNER_001";
        String fileId = "FILE_001";

        MockHttpServletRequest request = new MockHttpServletRequest();
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        MockHttpSession session = new MockHttpSession();
        SessionShareDto sessionShareDto = new SessionShareDto();
        sessionShareDto.setShareId(shareId);
        sessionShareDto.setShareUserId(ownerId);
        sessionShareDto.setFileId(fileId);
        session.setAttribute(Constants.SESSION_SHARE_KEY + shareId, sessionShareDto);

        FileShare fileShare = new FileShare();
        fileShare.setShareId(shareId);
        fileShare.setUserId(ownerId);
        fileShare.setFileId(fileId);
        when(fileShareService.getFileShareByShareId(shareId)).thenReturn(fileShare);

        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(fileId);
        fileInfo.setFileName("demo.txt");
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        when(fileInfoService.getFileInfoByFileIdAndUserId(fileId, ownerId)).thenReturn(fileInfo);

        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(ownerId);
        userInfo.setNickName("owner");
        when(userInfoService.getUserInfoByUserId(ownerId)).thenReturn(userInfo);

        ResponseVO<ShareInfoVO> response = webShareController.getShareLoginInfo(session, shareId);

        assertNotNull(response);
        assertNotNull(response.getData());
        assertFalse(Boolean.TRUE.equals(response.getData().getCurrentUser()));
    }
}
