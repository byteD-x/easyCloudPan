package com.easypan.service;

import com.easypan.component.S3Component;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.exception.BusinessException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import software.amazon.awssdk.services.s3.model.CompletedPart;

/**
 * 分片上传服务.
 *
 * <p>实现分片上传并发控制，限制同一文件最大并发数为 5
 *
 * <p>需求：2.3.1
 */
@Service
@Slf4j
public class ChunkUploadService {

    @Autowired
    private S3Component s3Component;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private AsyncTaskExecutor virtualThreadExecutor;

    /**
     * 同一文件最大并发上传分片数.
     */
    private static final int MAX_CONCURRENT_CHUNKS = 5;

    /**
     * Redis key 前缀：记录上传中的分片.
     */
    private static final String UPLOAD_PROGRESS_KEY = "upload:progress:";

    /**
     * 分片上传（带并发控制）.
     *
     * @param userId      用户ID
     * @param fileMd5     文件MD5值
     * @param chunkIndex  分片索引
     * @param totalChunks 总分片数
     * @param chunkFile   分片文件
     * @return 上传结果
     * @throws IOException IO异常
     */
    public UploadResultDto uploadChunk(
            String userId,
            String fileMd5,
            Integer chunkIndex,
            Integer totalChunks,
            MultipartFile chunkFile) throws IOException {

        String uploadKey = UPLOAD_PROGRESS_KEY + userId + ":" + fileMd5;

        Long currentConcurrent = redisTemplate.opsForSet().size(uploadKey + ":uploading");
        if (currentConcurrent != null && currentConcurrent >= MAX_CONCURRENT_CHUNKS) {
            log.warn("并发上传数超限: userId={}, fileMd5={}, current={}", userId, fileMd5, currentConcurrent);
            throw new BusinessException("并发上传数超限，请稍后重试");
        }

        redisTemplate.opsForSet().add(uploadKey + ":uploading", chunkIndex);
        redisTemplate.expire(uploadKey + ":uploading", 1, TimeUnit.HOURS);

        try {
            String chunkPath = String.format("chunks/%s/%s/%d", userId, fileMd5, chunkIndex);
            s3Component.uploadBytes(chunkPath, chunkFile.getBytes());

            log.info("分片上传成功: userId={}, fileMd5={}, chunkIndex={}/{}",
                    userId, fileMd5, chunkIndex, totalChunks);

            redisTemplate.opsForSet().add(uploadKey + ":completed", chunkIndex);
            redisTemplate.expire(uploadKey + ":completed", 24, TimeUnit.HOURS);

            Long completedCount = redisTemplate.opsForSet().size(uploadKey + ":completed");
            if (completedCount != null && completedCount.intValue() == totalChunks) {
                log.info("所有分片上传完成，准备合并: userId={}, fileMd5={}", userId, fileMd5);
                return new UploadResultDto(fileMd5, "merge_ready");
            }

            return new UploadResultDto(fileMd5, "uploading");

        } catch (IOException e) {
            log.error("分片上传失败: userId={}, fileMd5={}, chunkIndex={}", userId, fileMd5, chunkIndex, e);
            throw e;
        } finally {
            redisTemplate.opsForSet().remove(uploadKey + ":uploading", chunkIndex);
        }
    }

    /**
     * 获取已上传的分片数量.
     *
     * @param userId  用户ID
     * @param fileMd5 文件MD5值
     * @return 已上传分片数
     */
    public Long getCompletedChunkCount(String userId, String fileMd5) {
        String uploadKey = UPLOAD_PROGRESS_KEY + userId + ":" + fileMd5;
        Long count = redisTemplate.opsForSet().size(uploadKey + ":completed");
        return count != null ? count : 0L;
    }

    /**
     * 检查分片是否已上传.
     *
     * @param userId     用户ID
     * @param fileMd5    文件MD5值
     * @param chunkIndex 分片索引
     * @return 是否已上传
     */
    public boolean isChunkUploaded(String userId, String fileMd5, Integer chunkIndex) {
        String uploadKey = UPLOAD_PROGRESS_KEY + userId + ":" + fileMd5;
        Boolean isMember = redisTemplate.opsForSet().isMember(uploadKey + ":completed", (Object) chunkIndex);
        return Boolean.TRUE.equals(isMember);
    }

    /**
     * 清理上传进度记录.
     *
     * @param userId  用户ID
     * @param fileMd5 文件MD5值
     */
    public void clearUploadProgress(String userId, String fileMd5) {
        String uploadKey = UPLOAD_PROGRESS_KEY + userId + ":" + fileMd5;
        redisTemplate.delete(uploadKey + ":uploading");
        redisTemplate.delete(uploadKey + ":completed");
        log.info("清理上传进度: userId={}, fileMd5={}", userId, fileMd5);
    }

    /**
     * Redis key 前缀：合并状态.
     */
    private static final String MERGE_STATUS_KEY = "easypan:merge:status:";

    /**
     * 合并分片文件.
     *
     * <p>使用虚拟线程异步合并，通过 Redis 记录合并状态（T16 增强）
     *
     * @param userId      用户ID
     * @param fileMd5     文件MD5值
     * @param totalChunks 总分片数
     * @return 合并结果
     */
    public UploadResultDto mergeChunks(String userId, String fileMd5, Integer totalChunks) {
        log.info("开始合并分片: userId={}, fileMd5={}, totalChunks={}", userId, fileMd5, totalChunks);

        String statusKey = MERGE_STATUS_KEY + userId + ":" + fileMd5;
        redisTemplate.opsForValue().set(statusKey, "merging", 30, TimeUnit.MINUTES);

        CompletableFuture.runAsync(() -> {
            String finalPath = String.format("files/%s/%s", userId, fileMd5);
            String uploadId = null;
            try {
                // 1. 初始化分片上传
                uploadId = s3Component.createMultipartUpload(finalPath);
                List<CompletedPart> completedParts = new ArrayList<>();

                // 2. 遍历分片并执行服务端复制
                for (int i = 0; i < totalChunks; i++) {
                    String chunkPath = String.format("chunks/%s/%s/%d", userId, fileMd5, i);
                    CompletedPart part = s3Component.uploadPartCopy(chunkPath, finalPath, uploadId, i + 1);
                    completedParts.add(part);
                }

                // 3. 完成分片上传
                s3Component.completeMultipartUpload(finalPath, uploadId, completedParts);
                log.info("文件合并完成 (S3 Multipart): {}", finalPath);

                // T16: 更新合并状态为成功
                redisTemplate.opsForValue().set(statusKey, "success", 10, TimeUnit.MINUTES);

                // 4. 清理分片
                for (int i = 0; i < totalChunks; i++) {
                    String chunkPath = String.format("chunks/%s/%s/%d", userId, fileMd5, i);
                    try {
                        s3Component.deleteFileAsync(chunkPath);
                    } catch (Exception e) {
                        log.warn("清理分片失败: chunkPath={}", chunkPath, e);
                    }
                }

                clearUploadProgress(userId, fileMd5);

            } catch (Exception e) {
                log.error("文件合并失败: userId={}, fileMd5={}", userId, fileMd5, e);
                // T16: 更新合并状态为失败
                redisTemplate.opsForValue().set(statusKey, "failed", 10, TimeUnit.MINUTES);
                if (uploadId != null) {
                    try {
                        s3Component.abortMultipartUpload(finalPath, uploadId);
                    } catch (Exception abortEx) {
                        log.error("中止分片上传失败", abortEx);
                    }
                }
                clearUploadProgress(userId, fileMd5);
            }
        }, virtualThreadExecutor);

        return new UploadResultDto(fileMd5, "merging");
    }

    /**
     * 查询合并状态（T16 新增）.
     *
     * @param userId  用户ID
     * @param fileMd5 文件MD5值
     * @return 合并状态: merging / success / failed / null
     */
    public String getMergeStatus(String userId, String fileMd5) {
        String statusKey = MERGE_STATUS_KEY + userId + ":" + fileMd5;
        Object status = redisTemplate.opsForValue().get(statusKey);
        return status != null ? status.toString() : null;
    }

}

