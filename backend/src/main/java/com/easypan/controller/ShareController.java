package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.entity.po.FileShare;
import com.easypan.entity.query.FileShareQuery;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.service.FileShareService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.RequestMapping;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.web.bind.annotation.RestController;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;

/**
 * 分享控制器类，处理文件分享相关操作.
 */
@Slf4j
@RestController("shareController")
@RequestMapping("/share")
@Tag(name = "Share Management", description = "File sharing operations")
public class ShareController extends ABaseController {
    @Resource
    private FileShareService fileShareService;

    /**
     * 加载分享列表.
     *
     * @param session HTTP 会话
     * @param query 查询条件
     * @return 分页结果
     */
    @RequestMapping("/loadShareList")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Load Share List", description = "Pagination query for shared files")
    public ResponseVO<PaginationResultVO<FileShare>> loadShareList(HttpSession session, FileShareQuery query) {
        query.setOrderBy("share_time desc");
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        query.setUserId(userDto.getUserId());
        query.setQueryFileName(true);
        PaginationResultVO<FileShare> resultVO = this.fileShareService.findListByPage(query);
        return getSuccessResponseVO(resultVO);
    }

    /**
     * 分享文件.
     *
     * @param session HTTP 会话
     * @param fileId 文件ID
     * @param validType 有效期类型
     * @param code 分享码
     * @return 分享信息
     */
    @RequestMapping("/shareFile")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Share File", description = "Create a new share link")
    public ResponseVO<FileShare> shareFile(HttpSession session,
            @VerifyParam(required = true) String fileId,
            @VerifyParam(required = true) Integer validType,
            String code) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileShare share = new FileShare();
        share.setFileId(fileId);
        share.setValidType(validType);
        share.setCode(code);
        share.setUserId(userDto.getUserId());

        log.info("[SHARE] User {} creating share for file: {}, validType: {}",
                userDto.getUserId(), fileId, validType);

        fileShareService.saveShare(share);

        log.info("[SHARE] Share created successfully: shareId={}", share.getShareId());
        return getSuccessResponseVO(share);
    }

    /**
     * 取消分享.
     *
     * @param session HTTP 会话
     * @param shareIds 分享ID列表
     * @return 响应结果
     */
    @RequestMapping("/cancelShare")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Cancel Share", description = "Cancel shared files")
    public ResponseVO<Void> cancelShare(HttpSession session, @VerifyParam(required = true) String shareIds) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);

        log.info("[SHARE] User {} cancelling shares: {}", userDto.getUserId(), shareIds);

        fileShareService.deleteFileShareBatch(shareIds.split(","), userDto.getUserId());

        log.info("[SHARE] Shares cancelled successfully");
        return getSuccessResponseVO(null);
    }

    /**
     * 获取分享链接.
     *
     * @param session HTTP 会话
     * @param shareId 分享ID
     * @return 分享链接信息
     */
    @RequestMapping("/getShareUrl")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Get Share URL", description = "Get share link URL")
    public ResponseVO<Map<String, String>> getShareUrl(HttpSession session,
            @VerifyParam(required = true) String shareId) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileShare share = fileShareService.getFileShareByShareId(shareId);

        if (share == null || !share.getUserId().equals(userDto.getUserId())) {
            ResponseVO<Map<String, String>> errorResponse = new ResponseVO<>();
            errorResponse.setStatus(STATUC_ERROR);
            errorResponse.setCode(ResponseCodeEnum.CODE_600.getCode());
            errorResponse.setInfo("分享不存在");
            return errorResponse;
        }

        // 构建分享链接（实际项目中应该从配置读取域名）
        String shareUrl = "http://localhost:8080/share/" + shareId;

        Map<String, String> result = new HashMap<>();
        result.put("shareUrl", shareUrl);
        result.put("code", share.getCode());
        result.put("shareId", shareId);

        log.info("[SHARE] Generated share URL for shareId: {}", shareId);
        return getSuccessResponseVO(result);
    }

    /**
     * 检查分享状态.
     *
     * @param session HTTP 会话
     * @param shareId 分享ID
     * @return 分享状态信息
     */
    @RequestMapping("/checkShareStatus")
    @GlobalInterceptor(checkParams = true)
    @Operation(summary = "Check Share Status", description = "Check if share is valid or expired")
    public ResponseVO<Map<String, Object>> checkShareStatus(HttpSession session,
            @VerifyParam(required = true) String shareId) {
        SessionWebUserDto userDto = getUserInfoFromSession(session);
        FileShare share = fileShareService.getFileShareByShareId(shareId);

        if (share == null || !share.getUserId().equals(userDto.getUserId())) {
            ResponseVO<Map<String, Object>> errorResponse = new ResponseVO<>();
            errorResponse.setStatus(STATUC_ERROR);
            errorResponse.setCode(ResponseCodeEnum.CODE_600.getCode());
            errorResponse.setInfo("分享不存在");
            return errorResponse;
        }

        Map<String, Object> result = new HashMap<>();
        result.put("shareId", shareId);
        result.put("fileName", share.getFileName());
        result.put("shareTime", share.getShareTime());
        result.put("expireTime", share.getExpireTime());
        result.put("showCount", share.getShowCount());
        result.put("validType", share.getValidType());

        // 判断分享状态
        String status = "valid";
        if (share.getExpireTime() != null && share.getExpireTime().before(new java.util.Date())) {
            status = "expired";
        }
        result.put("status", status);

        return getSuccessResponseVO(result);
    }
}
