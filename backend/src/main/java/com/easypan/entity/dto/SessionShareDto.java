package com.easypan.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.Date;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Session 分享信息数据传输对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Session Share Information")
public class SessionShareDto implements Serializable {
    private static final long serialVersionUID = 1L;
    
    @Schema(description = "Share ID")
    private String shareId;
    @Schema(description = "Share User ID")
    private String shareUserId;
    @Schema(description = "Expiration Time")
    private Date expireTime;
    @Schema(description = "File ID")
    private String fileId;
}
