package com.easypan.service.impl;

import com.easypan.component.RedisUtils;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.UploadProgressDto;
import com.easypan.service.UploadProgressService;
import jakarta.annotation.Resource;
import org.springframework.stereotype.Service;

/**
 * 上传进度服务实现.
 */
@Service("uploadProgressService")
public class UploadProgressServiceImpl implements UploadProgressService {

    @Resource
    private RedisUtils<Object> redisUtils;

    private static final String PROGRESS_KEY_PREFIX = "upload:progress:";

    @Override
    public void updateProgress(String userId, String fileId, Integer chunkIndex, Integer totalChunks) {
        String key = getProgressKey(userId, fileId);
        // 当前使用简单的分片计数策略.
        // 该实现假设分片上传成功后再记入进度；若需要追踪缺失分片，应使用 BitMap/BitSet 或 Redis Set.
        // 这里采用 Redis Set 记录唯一分片索引，避免重复上传导致计数失真.
        redisUtils.setSet(key, chunkIndex);
        redisUtils.expire(key, Constants.REDIS_KEY_EXPIRES_ONE_HOUR); // 1 小时过期
    }

    @Override
    public UploadProgressDto getProgress(String userId, String fileId) {
        String key = getProgressKey(userId, fileId);
        long uploadedCount = redisUtils.getSetSize(key);

        // 默认不在 Redis 持久化 totalChunks，通常由前端持有；也可在首片上传时写入.
        // 当前实现只返回已完成分片数，保持读写链路简单稳定.
        return UploadProgressDto.builder()
                .completedChunks((int) uploadedCount)
                .build();
    }

    @Override
    public void clearProgress(String userId, String fileId) {
        redisUtils.delete(getProgressKey(userId, fileId));
    }

    private String getProgressKey(String userId, String fileId) {
        return PROGRESS_KEY_PREFIX + userId + ":" + fileId;
    }
}
