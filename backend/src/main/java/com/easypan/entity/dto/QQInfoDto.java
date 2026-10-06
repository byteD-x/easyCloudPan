package com.easypan.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * QQ 用户信息 DTO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "QQ User Information")
public class QQInfoDto {
    @Schema(description = "Return Code")
    private Integer ret;
    @Schema(description = "Message")
    private String msg;
    @Schema(description = "Nickname")
    private String nickname;
    @Schema(description = "Avatar URL 1")
    private String figureurlQq1;
    @Schema(description = "Avatar URL 2")
    private String figureurlQq2;
    @Schema(description = "Gender")
    private String gender;
}
