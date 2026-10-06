package com.easypan.service;

import com.easypan.component.RedisComponent;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.enums.UserStatusEnum;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.utils.StringTools;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Service;

import jakarta.annotation.Resource;
import java.util.Date;
import java.util.Map;

import static com.easypan.entity.po.table.UserInfoTableDef.USER_INFO;

/**
 * 自定义 OAuth2 用户服务，处理第三方登录.
 */
@Service
public class CustomOAuth2UserService extends DefaultOAuth2UserService {

    private static final Logger logger = LoggerFactory.getLogger(CustomOAuth2UserService.class);

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private com.easypan.entity.config.AppConfig appConfig;

    @Override
    public OAuth2User loadUser(OAuth2UserRequest userRequest) throws OAuth2AuthenticationException {
        String registrationId = userRequest.getClientRegistration().getRegistrationId();
        logger.info("Loading user from provider: {}", registrationId);

        OAuth2User oauth2User = super.loadUser(userRequest);
        Map<String, Object> attributes = oauth2User.getAttributes();

        processOAuth2User(registrationId, attributes);

        return oauth2User;
    }

    private void processOAuth2User(String registrationId, Map<String, Object> attributes) {
        String email = extractEmail(registrationId, attributes);
        String nickname = extractNickname(registrationId, attributes);
        String avatar = extractAvatar(registrationId, attributes);
        String providerUserId = extractProviderUserId(registrationId, attributes);
        
        if (email == null && nickname == null) {
            logger.warn("Cannot create user: both email and nickname are null for provider: {}", registrationId);
            return;
        }

        UserInfo user = null;
        if (email != null) {
            user = userInfoMapper.selectOneByQuery(
                    QueryWrapper.create().where(USER_INFO.EMAIL.eq(email)));
        }

        if (user == null) {
            createOAuth2User(email, nickname, avatar, registrationId, providerUserId);
            logger.info("Created new user from OAuth2 provider: {}, email: {}", registrationId, email);
        } else {
            updateLastLoginTime(user);
            logger.info("Updated last login time for user: {}", user.getUserId());
        }
    }

    private String extractEmail(String registrationId, Map<String, Object> attributes) {
        Object email = attributes.get("email");
        if (email == null) {
            email = attributes.get("email_address");
        }
        return email != null ? email.toString() : null;
    }

    private String extractNickname(String registrationId, Map<String, Object> attributes) {
        Object nickname = attributes.get("name");
        if (nickname == null) {
            nickname = attributes.get("nickname");
        }
        if (nickname == null) {
            nickname = attributes.get("login");
        }
        if (nickname == null) {
            nickname = attributes.get("preferred_username");
        }
        
        if (nickname != null) {
            String name = nickname.toString();
            return name.length() > Constants.LENGTH_150 ? name.substring(0, 150) : name;
        }
        return null;
    }

    private String extractAvatar(String registrationId, Map<String, Object> attributes) {
        Object avatar = attributes.get("avatar_url");
        if (avatar == null) {
            avatar = attributes.get("picture");
        }
        if (avatar == null) {
            avatar = attributes.get("profile_image_url");
        }
        if (avatar == null) {
            Object photos = attributes.get("photos");
            if (photos instanceof java.util.List) {
                java.util.List<?> photoList = (java.util.List<?>) photos;
                if (!photoList.isEmpty()) {
                    Object firstPhoto = photoList.get(0);
                    if (firstPhoto instanceof Map) {
                        Object value = ((Map<?, ?>) firstPhoto).get("value");
                        if (value != null) {
                            return value.toString();
                        }
                    }
                }
            }
        }
        return avatar != null ? avatar.toString() : null;
    }

    private String extractProviderUserId(String registrationId, Map<String, Object> attributes) {
        Object id = attributes.get("id");
        if (id == null) {
            id = attributes.get("sub");
        }
        if (id == null) {
            id = attributes.get("node_id");
        }
        return id != null ? id.toString() : null;
    }

    private UserInfo createOAuth2User(String email, String nickname, String avatar,
            String registrationId, String providerUserId) {
        UserInfo user = new UserInfo();
        user.setUserId(StringTools.getRandomString(Constants.LENGTH_10));
        user.setEmail(email);

        String finalNickname = nickname;
        if (finalNickname == null) {
            finalNickname = "user_" + StringTools.getRandomString(Constants.LENGTH_5);
        }

        UserInfo existingNickName = userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.NICK_NAME.eq(finalNickname)));
        if (existingNickName != null) {
            finalNickname = finalNickname + "_" + StringTools.getRandomString(Constants.LENGTH_5);
        }
        user.setNickName(finalNickname);

        if ("qq".equalsIgnoreCase(registrationId)) {
            user.setQqOpenId(providerUserId);
            user.setQqAvatar(avatar);
        }

        Date curDate = new Date();
        user.setJoinTime(curDate);
        user.setLastLoginTime(curDate);
        user.setStatus(UserStatusEnum.ENABLE.getStatus());
        user.setUseSpace(0L);
        
        SysSettingsDto sysSettingsDto = redisComponent.getSysSettingsDto();
        user.setTotalSpace(sysSettingsDto.getUserInitUseSpace() * Constants.MB);
        
        userInfoMapper.insert(user);
        
        return user;
    }

    private void updateLastLoginTime(UserInfo user) {
        UserInfo updateInfo = new UserInfo();
        updateInfo.setLastLoginTime(new Date());
        userInfoMapper.updateByQuery(updateInfo, 
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(user.getUserId())));
    }
}
