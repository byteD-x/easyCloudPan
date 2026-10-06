package com.easypan.service;

import com.easypan.entity.dto.UploadProgressDto;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * 断点续传服务.
 *
 * <p>提供文件上传进度管理和断点续传功能
 *
 * <p>使用 Redis 记录上传进度，支持客户端断线重连后继续上传
 *
 * <p>Redis Key 设计：
 *
 * <p>- upload:progress:{userId}:{fileMd5}:completed - Set 类型，存储已完成的分片索引
 *
 * <p>- upload:progress:{userId}:{fileMd5}:total - String 类型，存储总分片数
 *
 * <p>需求：2.3.2
 */
@Service
@Slf4j
public class ResumableUploadService {

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    /**
     * Redis Key 前缀.
     */
    private static final String UPLOAD_PROGRESS_KEY = "upload:progress:";

    /**
     * 上传进度过期时间（7天）.
     */
    private static final long PROGRESS_EXPIRE_DAYS = 7;

    /**
     * 获取上传进度.
     *
     * <p>查询指定文件的上传进度，包括已完成的分片数和总分片数
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     * @return 上传进度信息
     */
    public UploadProgressDto getUploadProgress(String userId, String fileMd5) {
        String uploadKey = buildUploadKey(userId, fileMd5);

        Set<Object> completedChunks = redisTemplate.opsForSet().members(uploadKey + ":completed");

        Object totalChunksObj = redisTemplate.opsForValue().get(uploadKey + ":total");
        Integer totalChunks = totalChunksObj != null ? Integer.parseInt(totalChunksObj.toString()) : null;

        int completedCount = completedChunks != null ? completedChunks.size() : 0;

        log.debug("获取上传进度 - userId: {}, fileMd5: {}, 已完成: {}/{}",
                userId, fileMd5, completedCount, totalChunks);

        return UploadProgressDto.builder()
                .fileMd5(fileMd5)
                .completedChunks(completedCount)
                .totalChunks(totalChunks != null ? totalChunks : 0)
                .build();
    }

    /**
     * 检查分片是否已上传.
     *
     * <p>用于断点续传时判断某个分片是否需要重新上传
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     * @param chunkIndex 分片索引（从0开始）
     * @return true 表示该分片已上传，false 表示未上传
     */
    public boolean isChunkUploaded(String userId, String fileMd5, Integer chunkIndex) {
        String uploadKey = buildUploadKey(userId, fileMd5);
        Boolean isMember = redisTemplate.opsForSet().isMember(uploadKey + ":completed", (Object) chunkIndex);

        boolean uploaded = Boolean.TRUE.equals(isMember);

        log.debug("检查分片状态 - userId: {}, fileMd5: {}, chunkIndex: {}, 已上传: {}",
                userId, fileMd5, chunkIndex, uploaded);

        return uploaded;
    }

    /**
     * 记录分片上传完成.
     *
     * <p>将已完成的分片索引添加到 Redis Set 中
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     * @param chunkIndex 分片索引
     */
    public void markChunkCompleted(String userId, String fileMd5, Integer chunkIndex) {
        String uploadKey = buildUploadKey(userId, fileMd5);

        redisTemplate.opsForSet().add(uploadKey + ":completed", (Object) chunkIndex);

        redisTemplate.expire(uploadKey + ":completed", PROGRESS_EXPIRE_DAYS, TimeUnit.DAYS);

        log.debug("标记分片完成 - userId: {}, fileMd5: {}, chunkIndex: {}",
                userId, fileMd5, chunkIndex);
    }

    /**
     * 初始化上传任务.
     *
     * <p>记录总分片数，用于计算上传进度
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     * @param totalChunks 总分片数
     */
    public void initUploadTask(String userId, String fileMd5, Integer totalChunks) {
        String uploadKey = buildUploadKey(userId, fileMd5);

        redisTemplate.opsForValue().set(uploadKey + ":total", totalChunks,
                PROGRESS_EXPIRE_DAYS, TimeUnit.DAYS);

        log.info("初始化上传任务 - userId: {}, fileMd5: {}, totalChunks: {}",
                userId, fileMd5, totalChunks);
    }

    /**
     * 清除上传进度.
     *
     * <p>文件上传完成或取消后，清除 Redis 中的进度记录
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     */
    public void clearUploadProgress(String userId, String fileMd5) {
        String uploadKey = buildUploadKey(userId, fileMd5);

        redisTemplate.delete(uploadKey + ":completed");

        redisTemplate.delete(uploadKey + ":total");

        log.info("清除上传进度 - userId: {}, fileMd5: {}", userId, fileMd5);
    }

    /**
     * 检查上传是否完成.
     *
     * <p>比较已完成分片数和总分片数，判断是否所有分片都已上传
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     * @return true 表示上传完成，false 表示未完成
     */
    public boolean isUploadCompleted(String userId, String fileMd5) {
        UploadProgressDto progress = getUploadProgress(userId, fileMd5);
        boolean completed = progress.isCompleted();

        log.debug("检查上传完成状态 - userId: {}, fileMd5: {}, 完成: {}",
                userId, fileMd5, completed);

        return completed;
    }

    /**
     * 构建 Redis Key.
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5值
     * @return Redis Key
     */
    private String buildUploadKey(String userId, String fileMd5) {
        return UPLOAD_PROGRESS_KEY + userId + ":" + fileMd5;
    }
}
