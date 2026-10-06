package com.easypan.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

/**
 * 系统设置数据传输对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "System Settings")
public class SysSettingsDto implements Serializable {
    /**
     * 注册发送邮件标题.
     */
    @Schema(description = "Register Email Title")
    private String registerEmailTitle = "邮箱验证码";

    /**
     * 注册发送邮件内容.
     */
    @Schema(description = "Register Email Content")
    private String registerEmailContent = "你好，您的邮箱验证码是：%s，15分钟有效";

    /**
     * 用户初始化空间大小 1024M.
     */
    @Schema(description = "User Initial Space Size (MB)")
    private Integer userInitUseSpace = 1024;
}
