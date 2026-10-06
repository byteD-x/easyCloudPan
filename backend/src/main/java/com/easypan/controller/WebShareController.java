package com.easypan.controller;

import com.easypan.annotation.GlobalInterceptor;
import com.easypan.annotation.VerifyParam;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionShareDto;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.FileShare;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.vo.FileInfoVO;
import com.easypan.entity.vo.FolderVO;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.entity.vo.ResponseVO;
import com.easypan.entity.vo.ShareInfoVO;
import com.easypan.exception.BusinessException;
import com.easypan.metrics.CustomMetrics;
import com.easypan.service.FileInfoService;
import com.easypan.service.FileShareService;
import com.easypan.service.ShareAccessLogService;
import com.easypan.service.UserInfoService;
import com.easypan.utils.CopyTools;
import com.easypan.utils.StringTools;
import com.easypan.annotation.RateLimit;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import java.util.Date;
import java.util.List;

/**
 * Web閸掑棔闊╅幒褍鍩楅崳銊ц閿涘苯顦╅悶鍡楀瀻娴滎偅鏋冩禒鍓佹畱Web鐠佸潡妫堕幙宥勭稊.
 */
@RestController("webShareController")
@RequestMapping("/showShare")
public class WebShareController extends CommonFileController {

    private static final String ACCESS_TYPE_VIEW = "VIEW";
    private static final String ACCESS_TYPE_CHECK_CODE = "CHECK_CODE";

    @Resource
    private FileShareService fileShareService;

    @Resource
    private FileInfoService fileInfoService;

    @Resource
    private UserInfoService userInfoService;

    @Resource
    private ShareAccessLogService shareAccessLogService;

    @Resource
    private CustomMetrics customMetrics;

    /**
     * 闁俺绻冮崚鍡曢煩ID閼惧嘲褰囬崚鍡曢煩閺傚洣娆㈡穱鈩冧紖.
     *
     * @param shareId 閸掑棔闊㊣D
     * @return 閸掑棔闊╂穱鈩冧紖
     */
    private ShareInfoVO getShareInfoCommon(String shareId) {
        FileShare share = fileShareService.getFileShareByShareId(shareId);
        if (null == share || (share.getExpireTime() != null && new Date().after(share.getExpireTime()))) {
            throw new BusinessException(ResponseCodeEnum.CODE_902.getMsg());
        }
        ShareInfoVO shareInfoVO = CopyTools.copy(share, ShareInfoVO.class);
        FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(share.getFileId(), share.getUserId());
        if (fileInfo == null || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())) {
            throw new BusinessException(ResponseCodeEnum.CODE_902.getMsg());
        }
        shareInfoVO.setFileName(fileInfo.getFileName());
        UserInfo userInfo = userInfoService.getUserInfoByUserId(share.getUserId());
        shareInfoVO.setNickName(userInfo.getNickName());
        shareInfoVO.setAvatar(userInfo.getQqAvatar());
        shareInfoVO.setUserId(userInfo.getUserId());
        return shareInfoVO;
    }

    /**
     * 閺嶏繝鐛欓崚鍡曢煩閺勵垰鎯佹径杈ㄦ櫏.
     *
     * @param session HTTP 娴兼俺鐦?
     * @param shareId 閸掑棔闊㊣D
     * @return 閸掑棔闊╂导姘崇樈娣団剝浼?
     */
    private SessionShareDto checkShare(HttpSession session, String shareId) {
        SessionShareDto shareSessionDto = getSessionShareFromSession(session, shareId);
        if (shareSessionDto == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_903);
        }
        if (shareSessionDto.getExpireTime() != null && new Date().after(shareSessionDto.getExpireTime())) {
            throw new BusinessException(ResponseCodeEnum.CODE_902);
        }
        return shareSessionDto;
    }

    /**
     * 閼惧嘲褰囬崚鍡曢煩閻ц缍嶆穱鈩冧紖.
     *
     * @param session HTTP 娴兼俺鐦?
     * @param shareId 閸掑棔闊㊣D
     * @return 閸掑棔闊╅惂璇茬秿娣団剝浼?
     */
    @RequestMapping("/getShareLoginInfo")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<ShareInfoVO> getShareLoginInfo(HttpSession session,
            @VerifyParam(required = true) String shareId) {
        SessionShareDto shareSessionDto = getSessionShareFromSession(session, shareId);
        if (shareSessionDto == null) {
            return getSuccessResponseVO(null);
        }
        ShareInfoVO shareInfoVO = getShareInfoCommon(shareId);
        // 閸掋倖鏌囬弰顖氭儊閺勵垰缍嬮崜宥囨暏閹村嘲鍨庢禍顐ゆ畱閺傚洣娆?
        SessionWebUserDto userDto = getCurrentUserOptional(session);
        if (userDto != null && userDto.getUserId().equals(shareSessionDto.getShareUserId())) {
            shareInfoVO.setCurrentUser(true);
        } else {
            shareInfoVO.setCurrentUser(false);
        }
        return getSuccessResponseVO(shareInfoVO);
    }

    /**
     * 閼惧嘲褰囬崚鍡曢煩娣団剝浼?
     *
     * @param shareId 閸掑棔闊㊣D
     * @return 閸掑棔闊╂穱鈩冧紖
     */
    @RequestMapping("/getShareInfo")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<ShareInfoVO> getShareInfo(@VerifyParam(required = true) String shareId) {
        return getSuccessResponseVO(getShareInfoCommon(shareId));
    }

    /**
     * 閺嶏繝鐛欓崚鍡曢煩閻?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param request HTTP 鐠囬攱鐪?
     * @param shareId 閸掑棔闊㊣D
     * @param code    閸掑棔闊╅惍?
     * @return 閸濆秴绨茬€电钖?
     */
    @RequestMapping("/checkShareCode")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<Void> checkShareCode(HttpSession session, HttpServletRequest request,
            @VerifyParam(required = true) String shareId,
            @VerifyParam(required = true) String code) {
        String visitorId = getVisitorId(session);
        String visitorIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");

        try {
            SessionShareDto shareSessionDto = fileShareService.checkShareCode(shareId, code);
            session.setAttribute(Constants.SESSION_SHARE_KEY + shareId, shareSessionDto);

            shareAccessLogService.logAccessAsync(shareId, null, visitorId, visitorIp, userAgent,
                    ACCESS_TYPE_CHECK_CODE, true, null);
            customMetrics.incrementShareAccess();
            return getSuccessResponseVO(null);
        } catch (BusinessException e) {
            shareAccessLogService.logAccessAsync(shareId, null, visitorId, visitorIp, userAgent,
                    ACCESS_TYPE_CHECK_CODE, false, e.getMessage());
            throw e;
        }
    }

    /**
     * 閼惧嘲褰囬弬鍥︽閸掓銆?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param shareId 閸掑棔闊㊣D
     * @param filePid 閺傚洣娆㈤悥绂滵
     * @return 閺傚洣娆㈤崚妤勩€?
     */
    @RequestMapping("/loadFileList")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<PaginationResultVO<FileInfoVO>> loadFileList(HttpSession session,
            @VerifyParam(required = true) String shareId, String filePid) {
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        FileInfoQuery query = new FileInfoQuery();
        if (!StringTools.isEmpty(filePid) && !Constants.ZERO_STR.equals(filePid)) {
            fileInfoService.checkRootFilePid(shareSessionDto.getFileId(), shareSessionDto.getShareUserId(), filePid);
            query.setFilePid(filePid);
        } else {
            query.setFileId(shareSessionDto.getFileId());
        }
        query.setUserId(shareSessionDto.getShareUserId());
        query.setOrderBy("last_update_time desc");
        query.setDelFlag(FileDelFlagEnums.USING.getFlag());
        PaginationResultVO<FileInfo> resultVO = fileInfoService.findListByPage(query);
        return getSuccessResponseVO(convert2PaginationVO(resultVO, FileInfoVO.class));
    }

    /**
     * 閼惧嘲褰囬惄顔肩秿娣団剝浼?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param shareId 閸掑棔闊㊣D
     * @param path    鐠侯垰绶?
     * @return 閻╊喖缍嶆穱鈩冧紖閸掓銆?
     */
    @RequestMapping("/getFolderInfo")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public ResponseVO<List<FolderVO>> getFolderInfo(HttpSession session,
            @VerifyParam(required = true) String shareId,
            @VerifyParam(required = true) String path) {
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        return super.getFolderInfo(path, shareSessionDto.getShareUserId());
    }

    /**
     * 閼惧嘲褰囬弬鍥︽.
     *
     * @param response HTTP 閸濆秴绨?
     * @param session  HTTP 娴兼俺鐦?
     * @param request  HTTP 鐠囬攱鐪?
     * @param shareId  閸掑棔闊㊣D
     * @param fileId   閺傚洣娆D
     */
    @RequestMapping("/getFile/{shareId}/{fileId}")
    @RateLimit(time = 1, count = 20)
    public void getFile(HttpServletResponse response, HttpSession session, HttpServletRequest request,
            @PathVariable("shareId") @VerifyParam(required = true) String shareId,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        checkReferer(request);
        SessionShareDto shareSessionDto = checkShare(session, shareId);

        String visitorId = getVisitorId(session);
        String visitorIp = getClientIp(request);
        String userAgent = request.getHeader("User-Agent");
        shareAccessLogService.logAccessAsync(shareId, fileId, visitorId, visitorIp, userAgent,
                ACCESS_TYPE_VIEW, true, null);

        super.getFile(response, fileId, shareSessionDto.getShareUserId());
    }

    /**
     * 閼惧嘲褰囬崶鍓у.
     *
     * @param session     HTTP 娴兼俺鐦?
     * @param request     HTTP 鐠囬攱鐪?
     * @param response    HTTP 閸濆秴绨?
     * @param shareId     閸掑棔闊㊣D
     * @param imageFolder 閸ュ墽澧栭弬鍥︽婢?
     * @param imageName   閸ュ墽澧栭崥宥囆?
     */
    @RequestMapping("/getImage/{shareId}/{imageFolder}/{imageName}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    @RateLimit(time = 1, count = 30)
    public void getImage(HttpSession session, HttpServletRequest request, HttpServletResponse response,
            @PathVariable("shareId") @VerifyParam(required = true) String shareId,
            @PathVariable("imageFolder") String imageFolder,
            @PathVariable("imageName") String imageName) {
        checkReferer(request);
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        super.getImage(response, imageFolder, imageName, shareSessionDto.getShareUserId());
    }

    /**
     * 閼惧嘲褰囩憴鍡涱暥.
     *
     * @param response HTTP 閸濆秴绨?
     * @param request  HTTP 鐠囬攱鐪?
     * @param session  HTTP 娴兼俺鐦?
     * @param shareId  閸掑棔闊㊣D
     * @param fileId   閺傚洣娆D
     */
    @RequestMapping("/ts/getVideoInfo/{shareId}/{fileId}")
    @RateLimit(time = 1, count = 100)
    public void getVideoInfo(HttpServletResponse response, HttpServletRequest request,
            HttpSession session,
            @PathVariable("shareId") @VerifyParam(required = true) String shareId,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        checkReferer(request);
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        super.getFile(response, fileId, shareSessionDto.getShareUserId());
    }

    /**
     * 閸掓稑缂撴稉瀣祰闁剧偓甯?
     *
     * @param session HTTP 娴兼俺鐦?
     * @param request HTTP 鐠囬攱鐪?
     * @param shareId 閸掑棔闊㊣D
     * @param fileId  閺傚洣娆D
     * @return 娑撳娴囬柧鐐复
     */
    @RequestMapping("/createDownloadUrl/{shareId}/{fileId}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    @RateLimit(time = 1, count = 5)
    public ResponseVO<String> createDownloadUrl(HttpSession session, HttpServletRequest request,
            @PathVariable("shareId") @VerifyParam(required = true) String shareId,
            @PathVariable("fileId") @VerifyParam(required = true) String fileId) {
        checkReferer(request);
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        return super.createDownloadUrl(fileId, shareSessionDto.getShareUserId());
    }

    /**
     * 娑撳娴囬弬鍥︽.
     *
     * @param request  HTTP 鐠囬攱鐪?
     * @param response HTTP 閸濆秴绨?
     * @param code     娑撳娴囬惍?
     * @throws Exception 瀵倸鐖?
     */
    @RequestMapping("/download/{code}")
    @GlobalInterceptor(checkLogin = false, checkParams = true)
    public void download(HttpServletRequest request, HttpServletResponse response,
            @PathVariable("code") @VerifyParam(required = true) String code) throws Exception {
        super.download(request, response, code);
    }

    /**
     * 娣囨繂鐡ㄩ崚鍡曢煩閺傚洣娆㈤崚鎷屽殰瀹歌京娈戠純鎴犳磸.
     *
     * @param session      HTTP 娴兼俺鐦?
     * @param shareId      閸掑棔闊㊣D
     * @param shareFileIds 閸掑棔闊╅弬鍥︽ID閸掓銆?
     * @param myFolderId   閻╊喗鐖ｉ弬鍥︽婢剁D
     * @return 閸濆秴绨茬€电钖?
     */
    @RequestMapping("/saveShare")
    @GlobalInterceptor(checkParams = true)
    public ResponseVO<Void> saveShare(HttpSession session,
            @VerifyParam(required = true) String shareId,
            @VerifyParam(required = true) String shareFileIds,
            @VerifyParam(required = true) String myFolderId) {
        SessionShareDto shareSessionDto = checkShare(session, shareId);
        SessionWebUserDto webUserDto = getUserInfoFromSession(session);
        if (shareSessionDto.getShareUserId().equals(webUserDto.getUserId())) {
            throw new BusinessException("Cannot save your own shared file to your own drive");
        }
        fileInfoService.saveShare(shareSessionDto.getFileId(), shareFileIds, myFolderId,
                shareSessionDto.getShareUserId(), webUserDto.getUserId());
        return getSuccessResponseVO(null);
    }

    private String getVisitorId(HttpSession session) {
        SessionWebUserDto userDto = getCurrentUserOptional(session);
        return userDto != null ? userDto.getUserId() : null;
    }

    private SessionWebUserDto getCurrentUserOptional(HttpSession session) {
        ServletRequestAttributes requestAttributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (requestAttributes != null) {
            Object requestUser = requestAttributes.getRequest().getAttribute(Constants.SESSION_KEY);
            if (requestUser instanceof SessionWebUserDto dto) {
                return dto;
            }
        }
        if (session != null) {
            Object sessionUser = session.getAttribute(Constants.SESSION_KEY);
            if (sessionUser instanceof SessionWebUserDto dto) {
                return dto;
            }
        }
        return null;
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_CLIENT_IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("HTTP_X_FORWARDED_FOR");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }
        return ip;
    }

    private void checkReferer(HttpServletRequest request) {
        String referer = request.getHeader("Referer");
        // 婵″倹鐏夌€涙ê婀?Referer 閸掓瑦瀚ら幋顏堟閻㈢喎鐓欓敍宀€娅ч崥宥呭礋閸欘垯浜掗崥搴㈡埂閹绘劘鍤﹂柊宥囩枂娑擃厼绺?
        if (!StringTools.isEmpty(referer)) {
            if (!referer.contains("localhost") && !referer.contains("127.0.0.1")
                    && !referer.contains("easycloudpan.com")) {
                throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "Illegal request, blocked by anti-hotlink rule");
            }
        }
    }
}
