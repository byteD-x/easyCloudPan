package com.easypan.service;

import com.easypan.component.RedisComponent;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.UserInfoMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 缓存预热服务.
 *
 *<p>在应用启动完成后自动预热热点数据到缓存中，提升系统响应速度
 */
@Component
@Slf4j
public class CacheWarmupService {

    @Autowired
    private UserInfoMapper userInfoMapper;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private RedisComponent redisComponent;

    /**
     * 应用启动完成后执行一次缓存预热.
     *
     * @param event 应用就绪事件
     */
    @EventListener(ApplicationReadyEvent.class)
    public void warmupOnStartup(ApplicationReadyEvent event) {
        doWarmup();
    }

    /**
     * 每天凌晨 2 点定时预热缓存.
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * ?")
    public void warmupOnSchedule() {
        doWarmup();
    }

    /**
     * 统一的缓存预热逻辑：
     * 1. 预热最近活跃用户信息（UserInfo 多级缓存）
     * 2. 预热用户空间使用信息（Redis 用户空间缓存）
     * 3. 预热系统配置（SysSettings 缓存）
     */
    private void doWarmup() {
        log.info("开始缓存预热...");

        try {
            // 预热活跃用户信息（前 1000 名）
            List<UserInfo> activeUsers = userInfoMapper.selectActiveUsers(1000);

            if (activeUsers != null && !activeUsers.isEmpty()) {
                activeUsers.forEach(user -> {
                    String userId = user.getUserId();
                    try {
                        // 1. 预热多级用户信息缓存
                        cacheService.getUserInfo(userId);
                        // 2. 预热用户空间缓存
                        redisComponent.getUserSpaceUse(userId);
                    } catch (Exception e) {
                        log.warn("预热用户缓存失败: userId={}, error={}",
                                userId, e.getMessage());
                    }
                });

                log.info("缓存预热完成，预热用户数: {}", activeUsers.size());
            } else {
                log.info("缓存预热完成，无活跃用户数据");
            }

            // 3. 预热系统配置
            redisComponent.getSysSettingsDto();

            // 4. 预热活跃用户的文件列表 (示例：预热根目录文件)
            if (activeUsers != null && !activeUsers.isEmpty()) {
                log.info("开始预热活跃用户文件列表...");
                // 注意：这里需要根据实际业务调整，比如预热某些特定的大文件或共享文件
                // 由于 FileInfoService 的逻辑较复杂，这里仅为示例框架
            }

        } catch (Exception e) {
            log.error("缓存预热失败", e);
        }
    }
}
