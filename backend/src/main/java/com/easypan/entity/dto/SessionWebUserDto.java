package com.easypan.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Session 用户信息数据传输对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Session User Information")
public class SessionWebUserDto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "User Nickname")
    private String nickName;
    @Schema(description = "User ID")
    private String userId;
    /**
     * 将字段名从 isAdmin 改为 admin，使 Lombok 生成 getAdmin 和 setAdmin 以保持兼容性.
     */
    @Schema(description = "Is Admin")
    private Boolean admin;
    @Schema(description = "User Avatar")
    private String avatar;
    @Schema(description = "Tenant ID")
    private String tenantId;
}
