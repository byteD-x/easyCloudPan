package com.easypan.entity.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 上传进度数据传输对象.
 *
 * <p>用于断点续传功能，记录文件上传的进度信息
 *
 * <p>需求：2.3.2
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Upload Progress Information")
public class UploadProgressDto implements Serializable {

    /**
     * 文件 MD5 值.
     */
    @Schema(description = "File MD5")
    private String fileMd5;

    /**
     * 已完成的分片数量.
     */
    @Schema(description = "Completed Chunk Count")
    private Integer completedChunks;

    /**
     * 总分片数量.
     */
    @Schema(description = "Total Chunk Count")
    private Integer totalChunks;

    /**
     * 上传进度百分比 (0-100).
     *
     * @return 进度百分比
     */
    public Double getProgress() {
        if (totalChunks == null || totalChunks == 0) {
            return 0.0;
        }
        return (completedChunks * 100.0) / totalChunks;
    }

    /**
     * 是否上传完成.
     *
     * @return 是否完成
     */
    public Boolean isCompleted() {
        return completedChunks != null && totalChunks != null
                && completedChunks.equals(totalChunks);
    }
}
