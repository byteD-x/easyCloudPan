package com.easypan.service;

import com.easypan.component.RedisUtils;
import com.easypan.entity.po.FileInfo;
import com.easypan.mappers.FileInfoMapper;
import com.github.benmanes.caffeine.cache.Cache;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 多级缓存服务.
 *
 *<p>实现 L1 (Caffeine Local Cache) + L2 (Redis) + L3 (Database) 的多级缓存读取策略.
 * 当前主要针对 FileInfo 高频查询.
 */
@Service
@Slf4j
public class MultiLevelCacheService {

    @Resource
    private Cache<String, FileInfo> fileInfoCache;

    @Resource
    private RedisUtils<Object> redisUtils;

    @Resource
    private FileInfoMapper fileInfoMapper;

    private static final String FILE_INFO_KEY_PREFIX = "file:info:";
    private static final String NULL_MARKER = "NULL_MARKER";
    private static final int NULL_CACHE_TTL = 300; // 5 minutes for null cache

    /**
     * 获取文件信息（多级缓存）.
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     * @return 文件信息
     */
    public FileInfo getFileInfo(String fileId, String userId) {
        String cacheKey = fileId + "_" + userId;
        String redisKey = FILE_INFO_KEY_PREFIX + cacheKey;

        // L1: 本地缓存
        FileInfo cached = fileInfoCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.debug("L1 Cache Hit: {}", cacheKey);
            return cached;
        }

        // L2: Redis 缓存
        Object redisObj = redisUtils.get(redisKey);
        if (redisObj instanceof FileInfo) {
            cached = (FileInfo) redisObj;
            log.debug("L2 Cache Hit: {}", cacheKey);
            // 回写 L1
            fileInfoCache.put(cacheKey, cached);
            return cached;
        }

        // 检查空值标记（防止缓存穿透）
        if (redisUtils.isNullMarker(redisKey)) {
            log.debug("L2 Null Marker Hit: {}", cacheKey);
            return null;
        }

        // L3: 数据库
        cached = fileInfoMapper.selectByFileIdAndUserId(fileId, userId);
        if (cached != null) {
            log.debug("L3 DB Hit: {}", cacheKey);
            // 回写 L2 & L1
            redisUtils.setex(redisKey, cached, 3600); // 1小时
            fileInfoCache.put(cacheKey, cached);
        } else {
            // 缓存空值标记，防止缓存穿透
            log.debug("L3 DB Miss, caching null marker: {}", cacheKey);
            redisUtils.setNullMarker(redisKey, NULL_CACHE_TTL);
        }

        return cached;
    }

    /**
     * 清除文件信息缓存.
     * 当文件信息更新或删除时调用.
     *
     * @param fileId 文件ID
     * @param userId 用户ID
     */
    public void evictFileInfo(String fileId, String userId) {
        String cacheKey = fileId + "_" + userId;
        String redisKey = FILE_INFO_KEY_PREFIX + cacheKey;

        fileInfoCache.invalidate(cacheKey);
        redisUtils.delete(redisKey);
        log.debug("Evicted Cache: {}", cacheKey);
    }
}
