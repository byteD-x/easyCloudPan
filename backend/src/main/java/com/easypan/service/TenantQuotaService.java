package com.easypan.service;

import com.easypan.component.RedisComponent;
import com.easypan.component.TenantContextHolder;
import com.easypan.entity.po.TenantInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.mappers.TenantInfoMapper;
import com.easypan.mappers.UserInfoMapper;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;


/**
 * 租户配额管理服务.
 */
@Service
public class TenantQuotaService {

    private static final Logger logger = LoggerFactory.getLogger(TenantQuotaService.class);

    @Resource
    private TenantInfoMapper tenantInfoMapper;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    /**
     * 检查存储配额.
     *
     * @param fileSize 待上传文件大小
     */
    public void checkStorageQuota(Long fileSize) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null || "default".equals(tenantId)) {
            // 默认租户可能有很大配额，但还是要检查
        }

        TenantInfo tenantInfo = tenantInfoMapper.selectOneById(tenantId);
        if (tenantInfo == null) {
            // 租户不存在，可能是未初始化的默认租户
            logger.warn("Tenant info not found for tenantId: {}", tenantId);
            return;
        }

        if (tenantInfo.getStatus() != null && tenantInfo.getStatus() == 0) {
            throw new BusinessException("租户已被禁用");
        }

        // 优先从缓存获取已用存储
        Long usedStorage = redisComponent.getTenantUsedStorage(tenantId);
        if (usedStorage == null) {
            // 缓存未命中，查询数据库
            usedStorage = fileInfoMapper.selectUseSpaceByTenantId(tenantId);
            if (usedStorage == null) {
                usedStorage = 0L;
            }
            // 写入缓存
            redisComponent.saveTenantUsedStorage(tenantId, usedStorage);
        }

        if (usedStorage + fileSize > tenantInfo.getStorageQuota()) {
            throw new BusinessException(String.format("租户存储空间不足，总配额: %d MB, 已用: %d MB", 
                tenantInfo.getStorageQuota() / 1024 / 1024, usedStorage / 1024 / 1024));
        }
    }

    /**
     * 更新租户已用存储（增量）.
     * 上传成功后调用。
     *
     * @param deltaSize 变化大小（正数增加，负数减少）
     */
    public void updateUsedStorage(Long deltaSize) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId == null) {
            return;
        }
        // 增量更新缓存
        Long newValue = redisComponent.incrementTenantUsedStorage(tenantId, deltaSize);
        if (newValue == null) {
            // 缓存不存在，删除让下次查询重建
            redisComponent.deleteTenantUsedStorage(tenantId);
        }
    }

    /**
     * 文件硬删除或批量修复后使租户空间缓存失效，下一次检查从带租户条件的数据库事实重建。
     */
    public void invalidateUsedStorage() {
        String tenantId = TenantContextHolder.getTenantId();
        invalidateUsedStorage(tenantId);
    }

    /** 异步任务使用记录本身携带的租户 ID，避免依赖请求线程上下文传播。 */
    public void invalidateUsedStorage(String tenantId) {
        if (tenantId != null) {
            redisComponent.deleteTenantUsedStorage(tenantId);
        }
    }

    /**
     * 为上传会话原子预留空间，避免并发上传同时通过配额检查造成超配。
     *
     * @param reservationId 上传会话 ID
     * @param fileSize      本次新增字节数
     */
    public void reserveStorageQuota(String reservationId, String reservationPart, Long fileSize) {
        if (fileSize == null || fileSize <= 0) {
            return;
        }
        String tenantId = TenantContextHolder.getTenantId();
        TenantInfo tenantInfo = tenantInfoMapper.selectOneById(tenantId);
        if (tenantInfo == null) {
            logger.warn("Tenant info not found for tenantId: {}", tenantId);
            return;
        }
        if (tenantInfo.getStatus() != null && tenantInfo.getStatus() == 0) {
            throw new BusinessException("租户已被禁用");
        }

        Long usedStorage = redisComponent.getTenantUsedStorage(tenantId);
        if (usedStorage == null) {
            usedStorage = fileInfoMapper.selectUseSpaceByTenantId(tenantId);
            if (usedStorage == null) {
                usedStorage = 0L;
            }
            redisComponent.initializeTenantUsedStorageIfAbsent(tenantId, usedStorage);
        }

        Long reserved = redisComponent.reserveTenantStorage(
                tenantId, reservationId, reservationPart, fileSize, tenantInfo.getStorageQuota());
        if (reserved != null && reserved == -1L) {
            // 缓存尚未初始化时先用数据库事实回填，再重试 Lua；不能在未预留的状态下放行上传。
            Long actualUsed = fileInfoMapper.selectUseSpaceByTenantId(tenantId);
            long safeUsed = actualUsed == null ? 0L : actualUsed;
            redisComponent.initializeTenantUsedStorageIfAbsent(tenantId, safeUsed);
            reserved = redisComponent.reserveTenantStorage(
                    tenantId, reservationId, reservationPart, fileSize, tenantInfo.getStorageQuota());
        }
        if (reserved != null && reserved == -2L) {
            throw new BusinessException(String.format("租户存储空间不足，总配额: %d MB, 已用: %d MB",
                    tenantInfo.getStorageQuota() / 1024 / 1024, usedStorage / 1024 / 1024));
        }
        if (reserved == null || reserved == -1L) {
            // Redis 不可用时无法提供跨实例原子预留，宁可短暂拒绝上传，也不能放行潜在超配。
            throw new BusinessException("配额服务暂时不可用，请稍后重试");
        }
    }

    /** 提交上传会话的租户空间预留；事务提交后调用。 */
    public void commitStorageReservation(String reservationId) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && reservationId != null) {
            Long committed = redisComponent.commitTenantStorageReservation(tenantId, reservationId);
            if (committed == null || committed == -1L) {
                // 提交结果未知时让后续请求从数据库重建，避免长期信任可能过期的缓存值。
                redisComponent.deleteTenantUsedStorage(tenantId);
            }
        }
    }

    /** 释放失败或过期上传会话的租户空间预留。 */
    public void releaseStorageReservation(String reservationId) {
        String tenantId = TenantContextHolder.getTenantId();
        if (tenantId != null && reservationId != null) {
            redisComponent.releaseTenantStorageReservation(tenantId, reservationId);
        }
    }

    /**
     * 检查用户配额.
     */
    public void checkUserQuota() {
        String tenantId = TenantContextHolder.getTenantId();
        TenantInfo tenantInfo = tenantInfoMapper.selectOneById(tenantId);
        if (tenantInfo == null) {
            return;
        }

        long userCount = userInfoMapper.countByTenantId(tenantId);
        
        if (userCount >= tenantInfo.getUserQuota()) {
            throw new BusinessException("租户用户数量已达上限");
        }
    }

    /**
     * 获取租户使用情况.
     *
     * @return 租户信息
     */
    public TenantInfo getTenantUsage() {
        String tenantId = TenantContextHolder.getTenantId();
        return tenantInfoMapper.selectOneById(tenantId);
    }
}
