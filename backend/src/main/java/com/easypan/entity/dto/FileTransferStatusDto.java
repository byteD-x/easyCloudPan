package com.easypan.entity.dto;

import lombok.Data;

/**
 * 文件传输状态 DTO.
 */
@Data
public class FileTransferStatusDto {
    private String fileId;
    private Integer status;
}
