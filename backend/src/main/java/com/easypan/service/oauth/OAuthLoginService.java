package com.easypan.service.oauth;

import com.easypan.component.RedisComponent;
import com.easypan.component.TenantContextHolder;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.FileInfoService;
import com.easypan.utils.StringTools;
import com.mybatisflex.core.query.QueryWrapper;
import com.easypan.entity.dto.OAuthCallbackDto;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.apache.commons.lang3.ArrayUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.Resource;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.easypan.entity.po.table.UserInfoTableDef.USER_INFO;

/**
 * OAuth 登录服务.
 */
@Service
public class OAuthLoginService {

    @Resource
    private List<OAuthProviderStrategy> strategyList;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private FileInfoService fileInfoService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private PasswordEncoder passwordEncoder;

    private Map<String, OAuthProviderStrategy> strategyMap = new HashMap<>();

    /**
     * 初始化策略映射.
     */
    @PostConstruct
    public void init() {
        for (OAuthProviderStrategy strategy : strategyList) {
            strategyMap.put(strategy.getProviderName(), strategy);
        }
    }

    /**
     * 构建 OAuth 授权 URL.
     *
     * @param provider OAuth 提供商
     * @param state    状态参数
     * @return 授权 URL
     */
    public String buildAuthorizationUrl(String provider, String state) {
        OAuthProviderStrategy strategy = strategyMap.get(provider);
        if (strategy == null) {
            throw new BusinessException("Unsupported OAuth provider: " + provider);
        }
        return strategy.buildAuthorizationUrl(state);
    }

    /**
     * OAuth 登录处理.
     *
     * @param provider OAuth 提供商
     * @param code     授权码
     * @return 登录结果
     */
    @Transactional(rollbackFor = Exception.class)
    public OAuthCallbackDto login(String provider, String code) {
        OAuthProviderStrategy strategy = strategyMap.get(provider);
        if (strategy == null) {
            throw new BusinessException("Unsupported OAuth provider: " + provider);
        }

        String accessToken = strategy.getAccessToken(code);
        OAuthUserInfo oauthUser = strategy.getUserInfo(accessToken);
        oauthUser.setProvider(provider); // 兜底写入 provider，确保后续注册链路可用。

        // 1. 先按 provider + providerId 查找绑定用户
        UserInfo user = userInfoMapper.selectOneByQuery(QueryWrapper.create()
                .where(USER_INFO.OAUTH_PROVIDER.eq(provider))
                .and(USER_INFO.OAUTH_PROVIDER_ID.eq(oauthUser.getProviderId())));

        // 2. 若未找到，且 OAuth 返回邮箱，则尝试按邮箱自动关联
        if (user == null && !StringTools.isEmpty(oauthUser.getEmail())) {
            user = userInfoMapper.selectOneByQuery(QueryWrapper.create()
                    .where(USER_INFO.EMAIL.eq(oauthUser.getEmail())));
            // 按邮箱命中后补齐 OAuth 绑定关系
            if (user != null) {
                UserInfo updateInfo = new UserInfo();
                updateInfo.setOauthProvider(provider);
                updateInfo.setOauthProviderId(oauthUser.getProviderId());
                if (StringTools.isEmpty(user.getAvatar())) {
                    updateInfo.setAvatar(oauthUser.getAvatarUrl());
                }
                userInfoMapper.updateByQuery(updateInfo,
                        QueryWrapper.create().where(USER_INFO.USER_ID.eq(user.getUserId())));
            }
        }

        OAuthCallbackDto result = new OAuthCallbackDto();

        // 3. 命中用户（直接命中或邮箱关联）则直接登录
        if (user != null) {
            checkUserStatus(user);
            SessionWebUserDto sessionWebUserDto = createSessionUser(user);
            result.setLoginSuccess(true);
            result.setSessionWebUserDto(sessionWebUserDto);
        } else {
            // 4. 未命中用户则返回 OAuth 信息，交给前端注册流程
            result.setLoginSuccess(false);
            result.setOauthUserInfo(oauthUser);
        }
        return result;
    }

    /**
     * OAuth 用户注册.
     *
     * @param provider  OAuth 提供商
     * @param oauthUser OAuth 用户信息
     * @param password  密码
     * @return 会话用户信息
     */
    @Transactional(rollbackFor = Exception.class)
    public SessionWebUserDto register(String provider, OAuthUserInfo oauthUser, String password) {
        // 并发场景二次校验，避免重复注册
        UserInfo existing = userInfoMapper.selectOneByQuery(QueryWrapper.create()
                .where(USER_INFO.EMAIL.eq(oauthUser.getEmail())));
        if (existing != null) {
            throw new BusinessException("该邮箱已被注册，请直接登录关联");
        }

        UserInfo user = new UserInfo();
        user.setUserId(StringTools.getRandomString(Constants.LENGTH_10));
        user.setNickName(oauthUser.getNickname());
        user.setEmail(oauthUser.getEmail());
        user.setPassword(passwordEncoder.encode(password));
        user.setAvatar(oauthUser.getAvatarUrl());
        user.setOauthProvider(provider);
        user.setOauthProviderId(oauthUser.getProviderId());
        user.setJoinTime(new Date());
        user.setLastLoginTime(new Date());
        user.setStatus(UserStatusEnum.ENABLE.getStatus());
        user.setUseSpace(0L);
        user.setTotalSpace(redisComponent.getSysSettingsDto().getUserInitUseSpace() * Constants.MB);
        user.setTenantId(TenantContextHolder.getTenantId());

        // 昵称冲突时自动补随机后缀
        UserInfo existingNick = userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.NICK_NAME.eq(user.getNickName())));
        if (existingNick != null) {
            user.setNickName(user.getNickName() + "_" + StringTools.getRandomString(5));
        }

        userInfoMapper.insert(user);
        return createSessionUser(user);
    }

    private void checkUserStatus(UserInfo user) {
        if (UserStatusEnum.DISABLE.getStatus().equals(user.getStatus())) {
            throw new BusinessException("账号被禁用无法登录");
        }
    }

    private SessionWebUserDto createSessionUser(UserInfo user) {
        SessionWebUserDto sessionWebUserDto = new SessionWebUserDto();
        sessionWebUserDto.setUserId(user.getUserId());
        sessionWebUserDto.setNickName(user.getNickName());
        sessionWebUserDto.setAvatar(user.getAvatar());
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
}
