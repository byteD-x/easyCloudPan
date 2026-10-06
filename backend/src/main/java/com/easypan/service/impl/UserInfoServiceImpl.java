package com.easypan.service.impl;

import com.easypan.component.RedisComponent;
import com.easypan.component.TenantContextHolder;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.QQInfoDto;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.PageSize;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.SimplePage;
import com.easypan.entity.query.UserInfoQuery;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.EmailCodeService;
import com.easypan.service.FileInfoService;
import com.easypan.service.UserInfoService;
import com.easypan.utils.JsonUtils;
import com.easypan.utils.OKHttpUtils;
import com.easypan.utils.QueryWrapperBuilder;
import com.easypan.utils.StringTools;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.security.crypto.password.PasswordEncoder;

import jakarta.annotation.Resource;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;

import static com.easypan.entity.po.table.UserInfoTableDef.USER_INFO;

/**
 * 用户信息服务实现类.
 */
@Service("userInfoService")
public class UserInfoServiceImpl implements UserInfoService {

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private EmailCodeService emailCodeService;

    @Resource
    private FileInfoService fileInfoService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private PasswordEncoder passwordEncoder;

    @Resource
    private com.easypan.service.TenantQuotaService tenantQuotaService;

    private static final Logger logger = LoggerFactory.getLogger(UserInfoServiceImpl.class);

    @Override
    public List<UserInfo> findListByParam(UserInfoQuery param) {
        QueryWrapper qw = QueryWrapperBuilder.build(param);
        return this.userInfoMapper.selectListByQuery(qw);
    }

    @Override
    public Integer findCountByParam(UserInfoQuery param) {
        QueryWrapper qw = QueryWrapperBuilder.build(param, false);
        return Math.toIntExact(this.userInfoMapper.selectCountByQuery(qw));
    }

    @Override
    public PaginationResultVO<UserInfo> findListByPage(UserInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<UserInfo> list = this.findListByParam(param);
        PaginationResultVO<UserInfo> result = new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(),
                page.getPageTotal(), list);
        return result;
    }

    @Override
    public Integer add(UserInfo bean) {
        return this.userInfoMapper.insert(bean);
    }

    @Override
    public Integer addBatch(List<UserInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userInfoMapper.insertBatch(listBean);
    }

    @Override
    public Integer addOrUpdateBatch(List<UserInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        return this.userInfoMapper.insertOrUpdateBatch(listBean);
    }

    @Override
    public UserInfo getUserInfoByUserId(String userId) {
        UserInfo userInfo = redisComponent.getUserInfo(userId);
        if (userInfo == null) {
            userInfo = this.userInfoMapper.selectOneByQuery(
                    QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
            if (userInfo != null) {
                redisComponent.saveUserInfo(userInfo);
            }
        }
        return userInfo;
    }

    @Override
    public Integer updateUserInfoByUserId(UserInfo bean, String userId) {
        Integer count = this.userInfoMapper.updateByQuery(bean,
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
        if (count > 0) {
            redisComponent.deleteUserInfo(userId);
        }
        return count;
    }

    @Override
    public Integer updatePassword(String userId, String rawPassword) {
        UserInfo updateInfo = new UserInfo();
        updateInfo.setPassword(passwordEncoder.encode(rawPassword));
        return updateUserInfoByUserId(updateInfo, userId);
    }

    @Override
    public Integer deleteUserInfoByUserId(String userId) {
        return this.userInfoMapper.deleteByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
    }

    @Override
    public UserInfo getUserInfoByEmail(String email) {
        return this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
    }

    @Override
    public Integer updateUserInfoByEmail(UserInfo bean, String email) {
        return this.userInfoMapper.updateByQuery(bean,
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
    }

    @Override
    public Integer deleteUserInfoByEmail(String email) {
        return this.userInfoMapper.deleteByQuery(
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
    }

    @Override
    public UserInfo getUserInfoByNickName(String nickName) {
        return this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.NICK_NAME.eq(nickName)));
    }

    @Override
    public Integer updateUserInfoByNickName(UserInfo bean, String nickName) {
        return this.userInfoMapper.updateByQuery(bean,
                QueryWrapper.create().where(USER_INFO.NICK_NAME.eq(nickName)));
    }

    @Override
    public Integer deleteUserInfoByNickName(String nickName) {
        return this.userInfoMapper.deleteByQuery(
                QueryWrapper.create().where(USER_INFO.NICK_NAME.eq(nickName)));
    }

    @Override
    public UserInfo getUserInfoByQqOpenId(String qqOpenId) {
        return this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.QQ_OPEN_ID.eq(qqOpenId)));
    }

    @Override
    public Integer updateUserInfoByQqOpenId(UserInfo bean, String qqOpenId) {
        return this.userInfoMapper.updateByQuery(bean,
                QueryWrapper.create().where(USER_INFO.QQ_OPEN_ID.eq(qqOpenId)));
    }

    @Override
    public Integer deleteUserInfoByQqOpenId(String qqOpenId) {
        return this.userInfoMapper.deleteByQuery(
                QueryWrapper.create().where(USER_INFO.QQ_OPEN_ID.eq(qqOpenId)));
    }

    @Override
    public SessionWebUserDto login(String email, String password) {
        UserInfo userInfo = this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
        if (null == userInfo) {
            throw new BusinessException("账号或者密码错误");
        }

        String dbPassword = userInfo.getPassword();
        boolean passwordMatch = false;
        boolean needUpgrade = false;

        if (dbPassword.length() == 32 && !dbPassword.startsWith("$")) {
            if (dbPassword.equals(StringTools.encodeByMD5(password))) {
                passwordMatch = true;
                needUpgrade = true;
            }
        } else {
            if (passwordEncoder.matches(password, dbPassword)) {
                passwordMatch = true;
            }
        }

        if (!passwordMatch) {
            throw new BusinessException("账号或者密码错误");
        }

        if (UserStatusEnum.DISABLE.getStatus().equals(userInfo.getStatus())) {
            throw new BusinessException("账号已禁用");
        }

        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginTime(new Date());

        if (needUpgrade) {
            updateInfo.setPassword(passwordEncoder.encode(password));
        }

        if (userInfo.getTotalSpace() == null) {
            SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
            Integer initMb = sysSettingsDto.getUserInitUseSpace();
            if (initMb == null) {
                initMb = 1024;
            }
            Long totalSpace = initMb * Constants.MB;
            updateInfo.setTotalSpace(totalSpace);
            userInfo.setTotalSpace(totalSpace);
        }

        this.userInfoMapper.updateByQuery(updateInfo,
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userInfo.getUserId())));
        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setNickName(userInfo.getNickName());
        sessionWebUserDto.setUserId(userInfo.getUserId());
        // 提供头像信息以便前端立即显示
        sessionWebUserDto.setAvatar(userInfo.getAvatar());
        sessionWebUserDto.setTenantId(userInfo.getTenantId());
        if ((userInfo.getIsAdmin() != null && userInfo.getIsAdmin())
                || ArrayUtils.contains(appConfig.getAdminEmails().split(","), email)) {
            sessionWebUserDto.setAdmin(true);
        } else {
            sessionWebUserDto.setAdmin(false);
        }
        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setUseSpace(fileInfoService.getUserUseSpace(userInfo.getUserId()));
        userSpaceDto.setTotalSpace(userInfo.getTotalSpace());
        redisComponent.saveUserSpaceUse(userInfo.getUserId(), userSpaceDto);
        // 主动刷新用户缓存（或删除后走懒加载）
        redisComponent.deleteUserInfo(userInfo.getUserId());
        return sessionWebUserDto;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void register(String email, String nickName, String password, String emailCode) {
        tenantQuotaService.checkUserQuota();
        UserInfo userInfo = this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
        if (null != userInfo) {
            throw new BusinessException("邮箱账号已经存在");
        }
        UserInfo nickNameUser = this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.NICK_NAME.eq(nickName)));
        if (null != nickNameUser) {
            throw new BusinessException("昵称已经存在");
        }
        emailCodeService.checkCode(email, emailCode);
        String userId = StringTools.getRandomNumber(Constants.LENGTH_10);
        userInfo = new UserInfo();
        userInfo.setUserId(userId);
        userInfo.setNickName(nickName);
        userInfo.setEmail(email);
        userInfo.setPassword(passwordEncoder.encode(password));
        userInfo.setJoinTime(new Date());
        userInfo.setStatus(UserStatusEnum.ENABLE.getStatus());
        userInfo.setTenantId(TenantContextHolder.getTenantId());
        SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
        userInfo.setTotalSpace(sysSettingsDto.getUserInitUseSpace() * Constants.MB);
        userInfo.setUseSpace(0L);
        this.userInfoMapper.insert(userInfo);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPwd(String email, String password, String emailCode) {
        UserInfo userInfo = this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
        if (null == userInfo) {
            throw new BusinessException("邮箱账号不存在");
        }
        emailCodeService.checkCode(email, emailCode);

        UserInfo updateInfo = new UserInfo();
        updateInfo.setPassword(passwordEncoder.encode(password));
        this.userInfoMapper.updateByQuery(updateInfo,
                QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateUserStatus(String userId, Integer status) {
        UserInfo userInfo = new UserInfo();
        userInfo.setStatus(status);
        if (UserStatusEnum.DISABLE.getStatus().equals(status)) {
            userInfo.setUseSpace(0L);
            fileInfoService.deleteFileByUserId(userId);
        }
        userInfoMapper.updateByQuery(userInfo,
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
        redisComponent.deleteUserInfo(userId);
    }

    @Override
    public SessionWebUserDto qqLogin(String code) {
        String accessToken = getQQAccessToken(code);
        String openId = getQQOpenId(accessToken);
        UserInfo user = this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.QQ_OPEN_ID.eq(openId)));
        String avatar = null;
        if (null == user) {
            QQInfoDto qqInfo = getQQUserInfo(accessToken, openId);
            user = new UserInfo();

            String nickName = qqInfo.getNickname();
            nickName = nickName.length() > Constants.LENGTH_150 ? nickName.substring(0, 150) : nickName;
            avatar = StringTools.isEmpty(qqInfo.getFigureurlQq2()) ? qqInfo.getFigureurlQq1()
                    : qqInfo.getFigureurlQq2();
            Date curDate = new Date();

            user.setQqOpenId(openId);
            user.setJoinTime(curDate);
            user.setNickName(nickName);
            user.setQqAvatar(avatar);
            user.setUserId(StringTools.getRandomString(Constants.LENGTH_10));
            user.setLastLoginTime(curDate);
            user.setStatus(UserStatusEnum.ENABLE.getStatus());
            user.setUseSpace(0L);
            user.setTotalSpace(redisComponent.getSysSettingsDto().getUserInitUseSpace() * Constants.MB);
            user.setTenantId(TenantContextHolder.getTenantId());
            this.userInfoMapper.insert(user);
            user = this.userInfoMapper.selectOneByQuery(
                    QueryWrapper.create().where(USER_INFO.QQ_OPEN_ID.eq(openId)));
        } else {
            UserInfo updateInfo = new UserInfo();
            updateInfo.setLastLoginTime(new Date());
            avatar = user.getQqAvatar();
            this.userInfoMapper.updateByQuery(updateInfo,
                    QueryWrapper.create().where(USER_INFO.QQ_OPEN_ID.eq(openId)));
            // 这里的 user 可能是旧对象，但仍可安全使用 userId 做缓存失效.
            // 当前 user 在上文登录流程中已查询得到.
            redisComponent.deleteUserInfo(user.getUserId());
        }
        if (UserStatusEnum.DISABLE.getStatus().equals(user.getStatus())) {
            throw new BusinessException("账号被禁用无法登录");
        }
        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setUserId(user.getUserId());
        sessionWebUserDto.setNickName(user.getNickName());
        sessionWebUserDto.setAvatar(avatar);
        sessionWebUserDto.setTenantId(user.getTenantId());
        if ((user.getIsAdmin() != null && user.getIsAdmin())
                || ArrayUtils.contains(appConfig.getAdminEmails().split(","),
                        user.getEmail() == null ? "" : user.getEmail())) {
            sessionWebUserDto.setAdmin(true);
        } else {
            sessionWebUserDto.setAdmin(false);
        }

        UserSpaceDto userSpaceDto = new UserSpaceDto();
        userSpaceDto.setUseSpace(fileInfoService.getUserUseSpace(user.getUserId()));
        userSpaceDto.setTotalSpace(user.getTotalSpace());
        redisComponent.saveUserSpaceUse(user.getUserId(), userSpaceDto);
        return sessionWebUserDto;
    }

    private String getQQAccessToken(String code) {
        String accessToken = null;
        String url = null;
        try {
            url = String.format(appConfig.getQqUrlAccessToken(), appConfig.getQqAppId(), appConfig.getQqAppKey(), code,
                    URLEncoder.encode(appConfig.getQqUrlRedirect(), "utf-8"));
        } catch (UnsupportedEncodingException e) {
            logger.error("encode失败");
        }
        String tokenResult = OKHttpUtils.getRequest(url);
        if (tokenResult == null || tokenResult.indexOf(Constants.VIEW_OBJ_RESULT_KEY) != -1) {
            logger.error("获取qqToken失败:{}", tokenResult);
            throw new BusinessException("获取qqToken失败");
        }
        String[] params = tokenResult.split("&");
        if (params != null && params.length > 0) {
            for (String p : params) {
                if (p.indexOf("access_token") != -1) {
                    accessToken = p.split("=")[1];
                    break;
                }
            }
        }
        return accessToken;
    }

    private String getQQOpenId(String accessToken) throws BusinessException {
        String url = String.format(appConfig.getQqUrlOpenId(), accessToken);
        String openIDResult = OKHttpUtils.getRequest(url);
        String tmpJson = this.getQQResp(openIDResult);
        if (tmpJson == null) {
            logger.error("调qq接口获取openID失败:tmpJson is null");
            throw new BusinessException("调qq接口获取openID失败");
        }
        @SuppressWarnings("unchecked")
        java.util.Map<String, Object> jsonData = JsonUtils.convertJson2Obj(tmpJson, java.util.Map.class);
        if (jsonData == null || jsonData.containsKey(Constants.VIEW_OBJ_RESULT_KEY)) {
            logger.error("调qq接口获取openID失败:{}", jsonData);
            throw new BusinessException("调qq接口获取openID失败");
        }
        return String.valueOf(jsonData.get("openid"));
    }

    private QQInfoDto getQQUserInfo(String accessToken, String qqOpenId) throws BusinessException {
        String url = String.format(appConfig.getQqUrlUserInfo(), accessToken, appConfig.getQqAppId(), qqOpenId);
        String response = OKHttpUtils.getRequest(url);
        if (StringUtils.isNotBlank(response)) {
            QQInfoDto qqInfo = JsonUtils.convertJson2Obj(response, QQInfoDto.class);
            if (qqInfo.getRet() != 0) {
                logger.error("qqInfo:{}", response);
                throw new BusinessException("调qq接口获取用户信息异常");
            }
            return qqInfo;
        }
        throw new BusinessException("调qq接口获取用户信息异常");
    }

    private String getQQResp(String result) {
        if (StringUtils.isNotBlank(result)) {
            int pos = result.indexOf("callback");
            if (pos != -1) {
                int start = result.indexOf("(");
                int end = result.lastIndexOf(")");
                String jsonStr = result.substring(start + 1, end - 1);
                return jsonStr;
            }
        }
        return null;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void changeUserSpace(String userId, Integer changeSpace) {
        UserInfo userInfo = getUserInfoByUserId(userId);
        Long currentTotalSpace = userInfo.getTotalSpace();
        if (currentTotalSpace == null) {
            SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
            Integer initMb = sysSettingsDto.getUserInitUseSpace();
            if (initMb == null) {
                initMb = 1024;
            }
            currentTotalSpace = initMb * Constants.MB;
        }

        Long deltaSpace = changeSpace * Constants.MB;
        Long newTotalSpace = currentTotalSpace + deltaSpace;

        if (newTotalSpace < 0) {
            newTotalSpace = 0L;
        }

        // 减少空间时校验：新空间不能低于已使用空间
        if (deltaSpace < 0) {
            Long usedSpace = fileInfoService.getUserUseSpace(userId);
            if (newTotalSpace < usedSpace) {
                throw new BusinessException("空间不能低于已使用量（" + (usedSpace / Constants.MB) + "MB）");
            }
        }

        this.userInfoMapper.updateTotalSpace(userId, newTotalSpace);
        redisComponent.resetUserSpaceUse(userId);
        redisComponent.deleteUserInfo(userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setUserSpace(String userId, Long totalSpaceMB) {
        if (totalSpaceMB == null || totalSpaceMB < 0) {
            throw new BusinessException("空间大小无效");
        }

        Long newTotalSpace = totalSpaceMB * Constants.MB;

        // 校验：新空间不能低于已使用空间
        Long usedSpace = fileInfoService.getUserUseSpace(userId);
        if (newTotalSpace < usedSpace) {
            throw new BusinessException("空间不能低于已使用量（" + (usedSpace / Constants.MB) + "MB）");
        }

        this.userInfoMapper.updateTotalSpace(userId, newTotalSpace);
        redisComponent.resetUserSpaceUse(userId);
        redisComponent.deleteUserInfo(userId);
    }
}
