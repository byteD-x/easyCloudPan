package com.easypan.entity.config;

import com.easypan.entity.constants.Constants;
import com.easypan.utils.StringTools;
import lombok.Data;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 应用配置类，管理应用程序的各项配置参数.
 */
@Component("appConfig")
@Data
public class AppConfig {

    /**
     * 文件目录.
     */
    @Value("${project.folder:}")
    private String projectFolder;

    /**
     * 发送人邮箱.
     */
    @Value("${spring.mail.username:}")
    private String sendUserName;

    @Value("${admin.emails:}")
    private String adminEmails;

    /**
     * 获取管理员邮箱列表.
     *
     * @return 管理员邮箱列表
     */
    public String getAdminEmails() {
        return adminEmails;
    }

    @Value("${dev:false}")
    private Boolean dev;

    @Value("${qq.app.id:}")
    private String qqAppId;

    @Value("${qq.app.key:}")
    private String qqAppKey;

    @Value("${qq.url.authorization:}")
    private String qqUrlAuthorization;

    @Value("${qq.url.access.token:}")
    private String qqUrlAccessToken;

    @Value("${qq.url.openid:}")
    private String qqUrlOpenId;

    @Value("${qq.url.user.info:}")
    private String qqUrlUserInfo;

    @Value("${qq.url.redirect:}")
    private String qqUrlRedirect;

    // OAuth: GitHub
    @Value("${oauth.github.client-id:}")
    private String githubClientId;
    @Value("${oauth.github.client-secret:}")
    private String githubClientSecret;
    @Value("${oauth.github.redirect-uri:}")
    private String githubRedirectUri;

    // OAuth: Gitee
    @Value("${oauth.gitee.client-id:}")
    private String giteeClientId;
    @Value("${oauth.gitee.client-secret:}")
    private String giteeClientSecret;
    @Value("${oauth.gitee.redirect-uri:}")
    private String giteeRedirectUri;

    // OAuth: Google
    @Value("${oauth.google.client-id:}")
    private String googleClientId;
    @Value("${oauth.google.client-secret:}")
    private String googleClientSecret;
    @Value("${oauth.google.redirect-uri:}")
    private String googleRedirectUri;

    // OAuth: Microsoft
    @Value("${oauth.microsoft.client-id:}")
    private String microsoftClientId;
    @Value("${oauth.microsoft.client-secret:}")
    private String microsoftClientSecret;
    @Value("${oauth.microsoft.redirect-uri:}")
    private String microsoftRedirectUri;

    // Jackson configuration
    @Value("${spring.jackson.date-format:yyyy-MM-dd HH:mm:ss}")
    private String jsonDateFormat;

    @Value("${spring.jackson.time-zone:GMT+8}")
    private String jsonTimeZone;

    /**
     * 获取项目文件夹路径.
     *
     * @return 项目文件夹路径
     */
    public String getProjectFolder() {
        if (!StringTools.isEmpty(projectFolder) && !projectFolder.endsWith("/")) {
            projectFolder = projectFolder + "/";
        }
        return projectFolder;
    }

    /**
     * 获取文件存储根目录，兼容以下两种配置：
     * 1) PROJECT_FOLDER=/data/easypan/  -> /data/easypan/file
     * 2) PROJECT_FOLDER=.../backend/file/ -> .../backend/file
     *
     * @return 规范化后的文件根目录（不带结尾斜杠）
     */
    public String getFileRootPath() {
        String folder = getProjectFolder();
        if (StringTools.isEmpty(folder)) {
            return "/file";
        }
        String normalized = folder.replace("\\", "/");
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        String fileSegment = Constants.FILE_FOLDER_FILE.substring(0, Constants.FILE_FOLDER_FILE.length() - 1);
        if (normalized.endsWith(fileSegment)) {
            return normalized;
        }
        return normalized + fileSegment;
    }

}
