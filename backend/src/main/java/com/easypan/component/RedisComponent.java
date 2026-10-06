package com.easypan.component;

import com.easypan.entity.constants.CacheTTL;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.DownloadFileDto;
import com.easypan.entity.dto.SysSettingsDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.po.UserInfo;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.mappers.UserInfoMapper;
import com.google.common.hash.BloomFilter;
import com.mybatisflex.core.query.QueryWrapper;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import jakarta.annotation.Resource;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.easypan.entity.po.table.UserInfoTableDef.USER_INFO;

/**
 * Redis 缓存操作组件.
 */
@Component("redisComponent")
public class RedisComponent {

    @Resource
    private RedisUtils<Object> redisUtils;

    /** Redis 原子操作模板，专用于配额数值与预留 Lua 脚本。 */
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    private static final DefaultRedisScript<String> RESERVE_TENANT_STORAGE_SCRIPT =
            new DefaultRedisScript<>(
                    "local used = redis.call('GET', KEYS[1]); "
                            + "if not used then return -1 end; "
                            + "local previous = tonumber(redis.call('HGET', KEYS[2], ARGV[1]) or '0'); "
                            + "local reserved = tonumber(redis.call('HGET', KEYS[2], '_total') or '0'); "
                            + "local delta = tonumber(ARGV[2]) - previous; "
                            + "local next = reserved + delta; "
                            + "if tonumber(used) + next > tonumber(ARGV[3]) then return -2 end; "
                            + "redis.call('HSET', KEYS[2], ARGV[1], ARGV[2], '_total', tostring(next)); "
                            + "redis.call('EXPIRE', KEYS[2], ARGV[4]); "
                            + "return next",
                    String.class);

    private static final DefaultRedisScript<String> COMMIT_TENANT_STORAGE_SCRIPT =
            new DefaultRedisScript<>(
                    "local reserved = redis.call('HGET', KEYS[2], '_total'); "
                            + "if not reserved then return -1 end; "
                            + "local used = tonumber(redis.call('GET', KEYS[1]) or '0'); "
                            + "local next = used + tonumber(reserved); "
                            + "redis.call('SET', KEYS[1], tostring(next), 'EX', ARGV[1]); "
                            + "redis.call('DEL', KEYS[2]); "
                            + "return next",
                    String.class);

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    @Qualifier("fileMd5BloomFilter")
    private BloomFilter<String> fileMd5BloomFilter;

    @Resource
    @Qualifier("fileIdBloomFilter")
    private BloomFilter<String> fileIdBloomFilter;

    /**
     * 获取系统设置.
     *
     * @return 系统设置对象
     */
    public SysSettingsDto getSysSettingsDto() {
        SysSettingsDto sysSettingsDto = (SysSettingsDto) redisUtils.get(Constants.REDIS_KEY_SYS_SETTING);
        if (sysSettingsDto == null) {
            sysSettingsDto = new SysSettingsDto();
            redisUtils.setex(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto, CacheTTL.SYS_CONFIG);
        }
        return sysSettingsDto;
    }

    /**
     * 保存系统设置.
     *
     * @param sysSettingsDto 系统设置对象
     */
    public void saveSysSettingsDto(SysSettingsDto sysSettingsDto) {
        redisUtils.setex(Constants.REDIS_KEY_SYS_SETTING, sysSettingsDto, CacheTTL.SYS_CONFIG);
    }

    /**
     * 保存下载码.
     *
     * @param code            下载码
     * @param downloadFileDto 下载文件信息
     */
    public void saveDownloadCode(String code, DownloadFileDto downloadFileDto) {
        redisUtils.setex(Constants.REDIS_KEY_DOWNLOAD + code,
                downloadFileDto, Constants.REDIS_KEY_EXPIRES_FIVE_MIN);
    }

    /**
     * 获取下载码对应的文件信息.
     *
     * @param code 下载码
     * @return 下载文件信息
     */
    public DownloadFileDto getDownloadCode(String code) {
        return (DownloadFileDto) redisUtils.get(Constants.REDIS_KEY_DOWNLOAD + code);
    }

    /**
     * 获取用户使用的空间.
     *
     * @param userId 用户ID
     * @return 用户空间使用情况
     */
    public UserSpaceDto getUserSpaceUse(String userId) {
        String key = Constants.REDIS_KEY_USER_SPACE_USE + userId;
        UserSpaceDto spaceDto = (UserSpaceDto) redisUtils.get(key);
        if (spaceDto == null || spaceDto.getTotalSpace() == null || spaceDto.getUseSpace() == null) {
            if (spaceDto == null) {
                spaceDto = new UserSpaceDto();
            }

            Long useSpace = this.fileInfoMapper.selectUseSpace(userId);
            spaceDto.setUseSpace(useSpace);
            spaceDto.setTotalSpace(resolveUserTotalSpace(userId));
            redisUtils.setex(key, spaceDto, CacheTTL.WARM_DATA);
        }
        return spaceDto;
    }

    private Long resolveUserTotalSpace(String userId) {
        Long totalSpace = null;
        try {
            UserInfo userInfo = this.userInfoMapper.selectOneByQuery(
                    QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
            if (userInfo != null) {
                totalSpace = userInfo.getTotalSpace();
            }
        } catch (Exception e) {
            // 保留兜底路径，避免 total_space 为空或异常时影响上传链路.
        }
        if (totalSpace != null) {
            return totalSpace;
        }

        Integer initMb = getSysSettingsDto().getUserInitUseSpace();
        if (initMb == null) {
            initMb = 1024;
        }
        return initMb * Constants.MB;
    }

    /**
     * 保存已使用的空间.
     *
     * @param userId       用户ID
     * @param userSpaceDto 用户空间使用情况
     */
    public void saveUserSpaceUse(String userId, UserSpaceDto userSpaceDto) {
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE_USE + userId,
                userSpaceDto, CacheTTL.WARM_DATA);
    }

    /**
     * 重置用户空间使用情况.
     *
     * @param userId 用户ID
     * @return 用户空间使用情况
     */
    public UserSpaceDto resetUserSpaceUse(String userId) {
        UserSpaceDto spaceDto = new UserSpaceDto();
        Long useSpace = this.fileInfoMapper.selectUseSpace(userId);
        spaceDto.setUseSpace(useSpace);

        UserInfo userInfo = this.userInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));
        Long totalSpace = userInfo != null ? userInfo.getTotalSpace() : null;
        if (totalSpace == null) {
            totalSpace = resolveUserTotalSpace(userId);
        }
        spaceDto.setTotalSpace(totalSpace);
        redisUtils.setex(Constants.REDIS_KEY_USER_SPACE_USE + userId,
                spaceDto, CacheTTL.WARM_DATA);
        return spaceDto;
    }

    /**
     * 保存文件临时大小.
     *
     * @param userId   用户ID
     * @param fileId   文件ID
     * @param fileSize 文件大小
     */
    public void saveFileTempSize(String userId, String fileId, Long fileSize) {
        Long currentSize = getFileTempSize(userId, fileId);
        redisUtils.setex(Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId,
                currentSize + fileSize, Constants.REDIS_KEY_EXPIRES_ONE_HOUR);
    }

    /**
     * 获取文件临时大小.
     *
     * @param userId 用户ID
     * @param fileId 文件ID
     * @return 文件临时大小
     */
    public Long getFileTempSize(String userId, String fileId) {
        Long currentSize = getFileSizeFromRedis(
                Constants.REDIS_KEY_USER_FILE_TEMP_SIZE + userId + fileId);
        return currentSize;
    }

    private Long getFileSizeFromRedis(String key) {
        Object sizeObj = redisUtils.get(key);
        if (sizeObj == null) {
            return 0L;
        }
        if (sizeObj instanceof Integer) {
            return ((Integer) sizeObj).longValue();
        } else if (sizeObj instanceof Long) {
            return (Long) sizeObj;
        }

        return 0L;
    }

    /**
     * 添加 JWT 到黑名单.
     *
     * @param token                   JWT Token
     * @param expirationTimeInSeconds 过期时间（秒）
     */
    public void addBlacklistToken(String token, long expirationTimeInSeconds) {
        redisUtils.setex(Constants.REDIS_KEY_JWT_BLACKLIST + token, "", expirationTimeInSeconds);
    }

    /**
     * 检查 Token 是否在黑名单中.
     *
     * @param token JWT Token
     * @return 是否在黑名单中
     */
    public boolean isTokenBlacklisted(String token) {
        return redisUtils.get(Constants.REDIS_KEY_JWT_BLACKLIST + token) != null;
    }

    /**
     * 保存 Refresh Token.
     *
     * @param userId                  用户ID
     * @param refreshToken            Refresh Token
     * @param expirationTimeInSeconds 过期时间（秒）
     */
    public void saveRefreshToken(String userId, String refreshToken, long expirationTimeInSeconds) {
        redisUtils.setex(Constants.REDIS_KEY_REFRESH_TOKEN + userId,
                refreshToken, expirationTimeInSeconds);
    }

    /**
     * 获取 Refresh Token.
     *
     * @param userId 用户ID
     * @return Refresh Token
     */
    public String getRefreshToken(String userId) {
        return (String) redisUtils.get(Constants.REDIS_KEY_REFRESH_TOKEN + userId);
    }

    /**
     * 验证 Refresh Token.
     *
     * @param userId       用户ID
     * @param refreshToken Refresh Token
     * @return 是否有效
     */
    public boolean validateRefreshToken(String userId, String refreshToken) {
        String storedToken = getRefreshToken(userId);
        return refreshToken.equals(storedToken);
    }

    /**
     * 删除 Refresh Token.
     *
     * @param userId 用户ID
     */
    public void deleteRefreshToken(String userId) {
        redisUtils.delete(Constants.REDIS_KEY_REFRESH_TOKEN + userId);
    }

    /**
     * 检查文件 MD5 是否可能存在（布隆过滤器）.
     * 用于在秒传场景下降低对数据库的无效访问概率。
     *
     * @param fileMd5 文件 MD5
     * @return 是否可能存在
     */
    public boolean mightContainFileMd5(String fileMd5) {
        if (fileMd5 == null) {
            return false;
        }
        return fileMd5BloomFilter.mightContain(fileMd5);
    }

    /**
     * 将新的文件 MD5 加入布隆过滤器.
     *
     * @param fileMd5 文件 MD5
     */
    public void addFileMd5ToBloom(String fileMd5) {
        if (fileMd5 == null) {
            return;
        }
        fileMd5BloomFilter.put(fileMd5);
    }

    /**
     * 检查文件 ID 是否可能存在（布隆过滤器）.
     * 用于防止对不存在文件的缓存穿透查询.
     *
     * @param fileId 文件 ID
     * @return 是否可能存在
     */
    public boolean mightContainFileId(String fileId) {
        if (fileId == null) {
            return false;
        }
        return fileIdBloomFilter.mightContain(fileId);
    }

    /**
     * 将新的文件 ID 加入布隆过滤器.
     *
     * @param fileId 文件 ID
     */
    public void addFileIdToBloom(String fileId) {
        if (fileId == null) {
            return;
        }
        fileIdBloomFilter.put(fileId);
    }

    /**
     * 缓存空值标记（防止缓存穿透）.
     *
     * @param keyPrefix 键前缀
     * @param id        标识符
     * @param ttlSeconds 过期时间（秒）
     */
    public void cacheNullMarker(String keyPrefix, String id, long ttlSeconds) {
        redisUtils.setNullMarker(keyPrefix + id + ":null", ttlSeconds);
    }

    /**
     * 检查是否存在空值标记.
     *
     * @param keyPrefix 键前缀
     * @param id        标识符
     * @return 是否存在空值标记
     */
    public boolean hasNullMarker(String keyPrefix, String id) {
        return redisUtils.isNullMarker(keyPrefix + id + ":null");
    }

    public void saveOAuthTempUser(String key, Object info, long expire) {
        redisUtils.setex(key, info, expire);
    }

    public Object getOAuthTempUser(String key) {
        return redisUtils.get(key);
    }

    /**
     * 保存用户信息到缓存.
     *
     * @param userInfo 用户信息
     */
    public void saveUserInfo(UserInfo userInfo) {
        if (userInfo == null) {
            return;
        }
        redisUtils.setex(Constants.REDIS_KEY_USER_INFO + userInfo.getUserId(),
                userInfo, Constants.REDIS_KEY_EXPIRES_ONE_HOUR);
    }

    /**
     * 从缓存获取用户信息.
     *
     * @param userId 用户ID
     * @return 用户信息
     */
    public UserInfo getUserInfo(String userId) {
        return (UserInfo) redisUtils.get(Constants.REDIS_KEY_USER_INFO + userId);
    }

    /**
     * 删除用户信息缓存.
     *
     * @param userId 用户ID
     */
    public void deleteUserInfo(String userId) {
        redisUtils.delete(Constants.REDIS_KEY_USER_INFO + userId);
    }

    /**
     * 获取租户已用存储空间.
     *
     * @param tenantId 租户ID
     * @return 已用存储空间（字节），null 表示未缓存
     */
    public Long getTenantUsedStorage(String tenantId) {
        Object value = redisUtils.get(Constants.REDIS_KEY_TENANT_STORAGE + tenantId);
        if (value == null) {
            return null;
        }
        if (value instanceof Long) {
            return (Long) value;
        } else if (value instanceof Integer) {
            return ((Integer) value).longValue();
        }
        return null;
    }

    /**
     * 保存租户已用存储空间.
     *
     * @param tenantId    租户ID
     * @param usedStorage 已用存储空间（字节）
     */
    public void saveTenantUsedStorage(String tenantId, Long usedStorage) {
        redisUtils.setex(Constants.REDIS_KEY_TENANT_STORAGE + tenantId, 
                usedStorage, CacheTTL.WARM_DATA);
    }

    /** 仅在缓存缺失时回填，避免并发初始化覆盖刚提交的计数。 */
    public void initializeTenantUsedStorageIfAbsent(String tenantId, long usedStorage) {
        String key = Constants.REDIS_KEY_TENANT_STORAGE + tenantId;
        Boolean initialized = stringRedisTemplate.opsForValue().setIfAbsent(key, String.valueOf(usedStorage));
        if (Boolean.TRUE.equals(initialized)) {
            stringRedisTemplate.expire(key, CacheTTL.WARM_DATA, TimeUnit.SECONDS);
        }
    }

    /**
     * 增量更新租户已用存储空间.
     *
     * @param tenantId  租户ID
     * @param deltaSize 变化大小（正数增加，负数减少）
     * @return 更新后的值
     */
    public Long incrementTenantUsedStorage(String tenantId, Long deltaSize) {
        String key = Constants.REDIS_KEY_TENANT_STORAGE + tenantId;
        try {
            Long newValue = stringRedisTemplate.opsForValue().increment(key, deltaSize);
            if (newValue != null) {
                stringRedisTemplate.expire(key, CacheTTL.WARM_DATA, TimeUnit.SECONDS);
            }
            return newValue;
        } catch (Exception e) {
            // 旧缓存可能由 JSON 序列化写入，无法直接 INCRBY；让上层失效并回源数据库。
            return null;
        }
    }

    /** 原子预留租户空间，返回 -1 表示缓存未初始化，-2 表示超额。 */
    public Long reserveTenantStorage(String tenantId, String reservationId, String reservationPart,
            long partSize, long quota) {
        if (partSize < 0) {
            return 0L;
        }
        String usedKey = Constants.REDIS_KEY_TENANT_STORAGE + tenantId;
        String reservationKey = usedKey + ":reservation:" + reservationId;
        try {
            String result = stringRedisTemplate.execute(RESERVE_TENANT_STORAGE_SCRIPT,
                    List.of(usedKey, reservationKey), reservationPart, String.valueOf(partSize),
                    String.valueOf(quota), String.valueOf(CacheTTL.HOT_DATA));
            return result == null ? null : Long.valueOf(result);
        } catch (RuntimeException e) {
            // 配额服务由上层决定是否回源/拒绝，不能把 Redis 异常伪装成成功预留。
            return null;
        }
    }

    /** 提交上传会话的租户空间预留。 */
    public Long commitTenantStorageReservation(String tenantId, String reservationId) {
        String usedKey = Constants.REDIS_KEY_TENANT_STORAGE + tenantId;
        String reservationKey = usedKey + ":reservation:" + reservationId;
        try {
            String result = stringRedisTemplate.execute(COMMIT_TENANT_STORAGE_SCRIPT,
                    List.of(usedKey, reservationKey), String.valueOf(CacheTTL.WARM_DATA));
            return result == null ? null : Long.valueOf(result);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** 释放失败或中断上传留下的租户空间预留。 */
    public void releaseTenantStorageReservation(String tenantId, String reservationId) {
        try {
            stringRedisTemplate.delete(Constants.REDIS_KEY_TENANT_STORAGE + tenantId + ":reservation:" + reservationId);
        } catch (RuntimeException e) {
            // 释放是清理动作，不能覆盖上传链路原始异常；预留键会按 TTL 自动过期。
        }
    }

    /**
     * 删除租户存储缓存.
     *
     * @param tenantId 租户ID
     */
    public void deleteTenantUsedStorage(String tenantId) {
        redisUtils.delete(Constants.REDIS_KEY_TENANT_STORAGE + tenantId);
    }
}
