package com.easypan.service;

import com.easypan.component.RedisUtils;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.UserInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

import static com.easypan.entity.po.table.UserInfoTableDef.USER_INFO;

/**
 * 缓存保护服务，防止缓存穿透和雪崩.
 */
@Component
public class CacheProtectionService {

    private static final Logger log = LoggerFactory.getLogger(CacheProtectionService.class);

    private static final String NULL_VALUE = "NULL";
    private static final long NULL_TTL = 2;

    /**
     * 获取锁失败时的最大重试次数（消除递归栈溢出风险）.
     */
    private static final int MAX_LOCK_RETRIES = 3;

    /**
     * 获取锁失败时的等待间隔（毫秒）.
     */
    private static final long LOCK_RETRY_INTERVAL_MS = 50;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    @Autowired
    private RedisUtils<Object> redisUtils;

    @Autowired
    private UserInfoMapper userInfoMapper;

    /**
     * 获取用户信息（带空值缓存）.
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    public UserInfo getUserInfoWithNullCache(String userId) {
        String key = "user:info:" + userId;
        Object cached = redisUtils.get(key);

        if (NULL_VALUE.equals(cached)) {
            log.debug("缓存空值命中: {}", userId);
            return null;
        }

        if (cached != null) {
            log.debug("缓存命中: {}", userId);
            return (UserInfo) cached;
        }

        UserInfo userInfo = userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
        if (userInfo == null) {
            log.debug("缓存空值以防止穿透: {}", userId);
            redisUtils.setex(key, NULL_VALUE, NULL_TTL * 60);
        } else {
            redisUtils.setex(key, userInfo, 30 * 60);
        }

        return userInfo;
    }

    /**
     * 使用分布式锁获取用户信息.
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    public UserInfo getUserInfoWithLock(String userId) {
        String key = "user:info:" + userId;
        String lockKey = "lock:" + key;

        for (int attempt = 0; attempt <= MAX_LOCK_RETRIES; attempt++) {
            Object cached = redisUtils.get(key);
            if (cached != null && !NULL_VALUE.equals(cached)) {
                log.debug("缓存命中: {}", userId);
                return (UserInfo) cached;
            }

            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, "1", 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    cached = redisUtils.get(key);
                    if (cached != null && !NULL_VALUE.equals(cached)) {
                        return (UserInfo) cached;
                    }

                    log.debug("获取锁成功，查询数据库: {}", userId);
                    UserInfo userInfo = userInfoMapper.selectOneByQuery(
                            QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
                    if (userInfo != null) {
                        setWithRandomTTL(key, userInfo, 30 * 60);
                    } else {
                        redisUtils.setex(key, NULL_VALUE, NULL_TTL * 60);
                    }
                    return userInfo;
                } finally {
                    redisTemplate.delete(lockKey);
                }
            }

            if (attempt < MAX_LOCK_RETRIES) {
                try {
                    log.debug("等待锁释放 (attempt {}/{}): {}", attempt + 1, MAX_LOCK_RETRIES, userId);
                    Thread.sleep(LOCK_RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("等待锁时被中断: {}", userId);
                    break;
                }
            }
        }

        log.warn("获取分布式锁失败（已重试{}次），降级直查数据库: {}", MAX_LOCK_RETRIES, userId);
        return userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
    }

    /**
     * 设置带随机过期时间的缓存.
     *
     * @param key 缓存键
     * @param value 缓存值
     * @param baseTtl 基础过期时间（秒）
     */
    public void setWithRandomTTL(String key, Object value, long baseTtl) {
        long randomTtl = baseTtl + ThreadLocalRandom.current().nextLong(300);
        redisUtils.setex(key, value, randomTtl);
        log.debug("设置随机 TTL 缓存: key={}, ttl={} 秒", key, randomTtl);
    }

    /**
     * 获取用户信息（完整保护：空值缓存 + 分布式锁 + 随机 TTL）.
     *
     * @param userId 用户 ID
     * @return 用户信息
     */
    public UserInfo getUserInfoWithFullProtection(String userId) {
        String key = "user:info:" + userId;
        String lockKey = "lock:" + key;

        for (int attempt = 0; attempt <= MAX_LOCK_RETRIES; attempt++) {
            Object cached = redisUtils.get(key);
            if (NULL_VALUE.equals(cached)) {
                log.debug("缓存空值命中: {}", userId);
                return null;
            }
            if (cached != null) {
                log.debug("缓存命中: {}", userId);
                return (UserInfo) cached;
            }

            Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                    lockKey, "1", 10, TimeUnit.SECONDS);
            if (Boolean.TRUE.equals(locked)) {
                try {
                    cached = redisUtils.get(key);
                    if (cached != null) {
                        if (NULL_VALUE.equals(cached)) {
                            return null;
                        }
                        return (UserInfo) cached;
                    }

                    log.debug("获取锁成功，查询数据库: {}", userId);
                    UserInfo userInfo = userInfoMapper.selectOneByQuery(
                            QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
                    if (userInfo != null) {
                        setWithRandomTTL(key, userInfo, 30 * 60);
                    } else {
                        redisUtils.setex(key, NULL_VALUE, NULL_TTL * 60);
                    }
                    return userInfo;
                } finally {
                    redisTemplate.delete(lockKey);
                }
            }

            if (attempt < MAX_LOCK_RETRIES) {
                try {
                    Thread.sleep(LOCK_RETRY_INTERVAL_MS);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    log.warn("完整保护模式等待锁时被中断: {}", userId);
                    break;
                }
            }
        }

        log.warn("获取分布式锁失败（完整保护模式），降级直查数据库: {}", userId);
        UserInfo userInfo = userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
        if (userInfo != null) {
            setWithRandomTTL(key, userInfo, 30 * 60);
        }
        return userInfo;
    }

    /**
     * 使缓存失效.
     *
     * @param userId 用户 ID
     */
    public void invalidateUserInfo(String userId) {
        String key = "user:info:" + userId;
        redisUtils.delete(key);
        log.debug("清除缓存: {}", key);
    }
}
