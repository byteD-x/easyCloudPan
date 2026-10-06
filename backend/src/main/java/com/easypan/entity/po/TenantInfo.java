package com.easypan.entity.po;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.mybatisflex.annotation.Id;
import com.mybatisflex.annotation.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;

import java.io.Serializable;
import java.util.Date;

/**
 * 租户信息实体类.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Table("tenant_info")
public class TenantInfo implements Serializable {

    /**
     * 租户ID.
     */
    @Id
    private String tenantId;

    /**
     * 租户名称.
     */
    private String tenantName;

    /**
     * 租户代码（唯一标识）.
     */
    private String tenantCode;

    /**
     * 存储配额（字节）.
     */
    private Long storageQuota;

    /**
     * 用户配额（数量）.
     */
    private Integer userQuota;

    /**
     * 状态 0:禁用 1:启用.
     */
    private Integer status;

    /**
     * 创建时间.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date createTime;

    /**
     * 过期时间.
     */
    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss", timezone = "GMT+8")
    @DateTimeFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    private Date expireTime;
}
