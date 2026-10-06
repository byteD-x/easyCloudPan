package com.easypan.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 下载文件数据传输对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Download File Information")
public class DownloadFileDto {
    @Schema(description = "Download Code")
    private String downloadCode;
    @Schema(description = "File ID")
    private String fileId;
    @Schema(description = "File Name")
    private String fileName;
    @Schema(description = "File Path")
    private String filePath;
}
