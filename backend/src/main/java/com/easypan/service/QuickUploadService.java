package com.easypan.service;

import com.easypan.entity.dto.UploadResultDto;
import com.easypan.component.TenantContextHolder;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileStatusEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.utils.StringTools;
import com.mybatisflex.core.query.QueryWrapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.easypan.entity.po.table.FileInfoTableDef.FILE_INFO;

/**
 * 秒传服务类，处理文件快速上传逻辑.
 */
@Service
@Slf4j
public class QuickUploadService {

    @Autowired
    private FileInfoMapper fileInfoMapper;

    @Autowired
    private RedisTemplate<String, Object> redisTemplate;

    private static final String FILE_MD5_KEY = "file:md5:";
    private static final long MD5_CACHE_TTL_DAYS = 7;

    /**
     * 检查是否可以秒传.
     *
     * @param userId 用户ID
     * @param fileMd5 文件MD5
     * @param fileName 文件名
     * @param filePid 父文件夹ID
     * @return 上传结果，如果可以秒传则返回结果，否则返回null
     */
    public UploadResultDto checkQuickUpload(String userId, String fileMd5, String fileName, String filePid) {
        log.debug("检查秒传 - userId: {}, fileMd5: {}, fileName: {}", userId, fileMd5, fileName);

        String md5Key = FILE_MD5_KEY + fileMd5;
        String cachedFileId = (String) redisTemplate.opsForValue().get(md5Key);

        FileInfo existingFile = null;

        if (cachedFileId != null) {
            log.debug("Redis 缓存命中 - fileMd5: {}, cachedFileId: {}", fileMd5, cachedFileId);

            QueryWrapper qw = QueryWrapper.create()
                    .where(FILE_INFO.FILE_ID.eq(cachedFileId))
                    .and(FILE_INFO.USER_ID.eq(userId))
                    .and(FILE_INFO.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                    .and(FILE_INFO.STATUS.eq(FileStatusEnums.USING.getStatus()));

            List<FileInfo> fileList = fileInfoMapper.selectListByQuery(qw);
            if (!fileList.isEmpty()) {
                existingFile = fileList.get(0);
            }
        }

        if (existingFile == null) {
            log.debug("Redis 缓存未命中，查询数据库 - fileMd5: {}", fileMd5);

            QueryWrapper qw = QueryWrapper.create()
                    .where(FILE_INFO.FILE_MD5.eq(fileMd5))
                    .and(FILE_INFO.USER_ID.eq(userId))
                    .and(FILE_INFO.TENANT_ID.eq(TenantContextHolder.getTenantId()))
                    .and(FILE_INFO.STATUS.eq(FileStatusEnums.USING.getStatus()))
                    .limit(1);

            List<FileInfo> fileList = fileInfoMapper.selectListByQuery(qw);

            if (!fileList.isEmpty()) {
                existingFile = fileList.get(0);

                redisTemplate.opsForValue().set(md5Key, existingFile.getFileId(),
                        MD5_CACHE_TTL_DAYS, TimeUnit.DAYS);

                log.info("缓存 MD5 映射 - fileMd5: {}, fileId: {}", fileMd5, existingFile.getFileId());
            }
        }

        if (existingFile != null) {
            FileInfo newFile = createFileReference(userId, existingFile, fileName, filePid);

            log.info("秒传成功 - userId: {}, newFileId: {}, sourceFileId: {}, fileName: {}",
                    userId, newFile.getFileId(), existingFile.getFileId(), fileName);

            UploadResultDto result = new UploadResultDto();
            result.setFileId(newFile.getFileId());
            result.setStatus("upload_seconds");

            return result;
        }

        log.debug("无法秒传，需要正常上传 - userId: {}, fileMd5: {}", userId, fileMd5);
        return null;
    }

    private FileInfo createFileReference(String userId, FileInfo existingFile, String fileName, String filePid) {
        FileInfo newFile = new FileInfo();

        newFile.setFileId(StringTools.getRandomString(10));
        newFile.setUserId(userId);
        newFile.setTenantId(TenantContextHolder.getTenantId());
        newFile.setFileMd5(existingFile.getFileMd5());
        newFile.setFilePid(filePid);
        newFile.setFileName(fileName);
        newFile.setFilePath(existingFile.getFilePath());
        newFile.setFileSize(existingFile.getFileSize());
        newFile.setFileCover(existingFile.getFileCover());
        newFile.setFileCategory(existingFile.getFileCategory());
        newFile.setFileType(existingFile.getFileType());
        newFile.setFolderType(existingFile.getFolderType());
        newFile.setStatus(FileStatusEnums.USING.getStatus());
        newFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
        newFile.setCreateTime(new Date());
        newFile.setLastUpdateTime(new Date());

        fileInfoMapper.insert(newFile);

        log.debug("创建文件引用 - newFileId: {}, sourceFileId: {}, filePath: {}",
                newFile.getFileId(), existingFile.getFileId(), existingFile.getFilePath());

        return newFile;
    }

    /**
     * 缓存 MD5 映射.
     *
     * @param fileMd5 文件MD5
     * @param fileId 文件ID
     */
    public void cacheMd5Mapping(String fileMd5, String fileId) {
        if (StringTools.isEmpty(fileMd5) || StringTools.isEmpty(fileId)) {
            log.warn("MD5 或 FileId 为空，跳过缓存 - fileMd5: {}, fileId: {}", fileMd5, fileId);
            return;
        }

        String md5Key = FILE_MD5_KEY + fileMd5;
        redisTemplate.opsForValue().set(md5Key, fileId, MD5_CACHE_TTL_DAYS, TimeUnit.DAYS);

        log.debug("缓存 MD5 映射 - fileMd5: {}, fileId: {}", fileMd5, fileId);
    }

    /**
     * 清除 MD5 缓存.
     *
     * @param fileMd5 文件MD5
     */
    public void clearMd5Cache(String fileMd5) {
        if (StringTools.isEmpty(fileMd5)) {
            return;
        }

        String md5Key = FILE_MD5_KEY + fileMd5;
        redisTemplate.delete(md5Key);

        log.debug("清除 MD5 缓存 - fileMd5: {}", fileMd5);
    }
}
