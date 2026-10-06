package com.easypan.service;

import com.easypan.entity.dto.UploadProgressDto;

/**
 * 上传进度服务接口.
 */
public interface UploadProgressService {

    /**
     * 更新上传进度.
     *
     * @param userId      用户ID
     * @param fileId      文件ID
     * @param chunkIndex  分片索引
     * @param totalChunks 总分片数
     */
    void updateProgress(String userId, String fileId, Integer chunkIndex, Integer totalChunks);

    /**
     * 获取上传进度.
     *
     * @param userId 用户ID
     * @param fileId 文件ID
     * @return 进度信息
     */
    UploadProgressDto getProgress(String userId, String fileId);

    /**
     * 清除上传进度.
     *
     * @param userId 用户ID
     * @param fileId 文件ID
     */
    void clearProgress(String userId, String fileId);
}
