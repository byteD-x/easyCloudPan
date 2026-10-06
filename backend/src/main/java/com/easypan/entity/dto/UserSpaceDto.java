package com.easypan.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户空间数据传输对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User Space Information")
public class UserSpaceDto implements Serializable {
    @Schema(description = "Used Space (Bytes)")
    private Long useSpace;
    @Schema(description = "Total Space (Bytes)")
    private Long totalSpace;
}
