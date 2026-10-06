package com.easypan.service.impl;

import com.easypan.component.RedisComponent;
import com.easypan.component.TenantContextHolder;
import com.easypan.entity.config.AppConfig;
import com.easypan.entity.constants.Constants;
import com.easypan.entity.dto.SessionWebUserDto;
import com.easypan.entity.dto.UploadResultDto;
import com.easypan.entity.dto.UserSpaceDto;
import com.easypan.entity.enums.DateTimePatternEnum;
import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.enums.FileStatusEnums;
import com.easypan.entity.enums.FileTypeEnums;
import com.easypan.entity.enums.PageSize;
import com.easypan.entity.enums.ResponseCodeEnum;
import com.easypan.entity.enums.UploadStatusEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.entity.po.UserInfo;
import com.easypan.entity.query.FileInfoQuery;
import com.easypan.entity.query.SimplePage;
import com.easypan.entity.vo.PaginationResultVO;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.mappers.UserInfoMapper;
import com.easypan.service.FileInfoService;
import com.easypan.service.MediaTranscodeService;
import com.easypan.utils.DateUtil;
import com.easypan.utils.QueryWrapperBuilder;
import com.easypan.utils.StringTools;
import com.easypan.utils.UploadPathValidator;
import com.mybatisflex.core.query.QueryWrapper;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import java.util.concurrent.CompletableFuture;

import jakarta.annotation.Resource;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.security.DigestInputStream;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static com.easypan.entity.po.table.FileInfoTableDef.FILE_INFO;
import static com.easypan.entity.po.table.UserInfoTableDef.USER_INFO;

/**
 * 文件信息服务实现类。
 */
@Service("fileInfoService")
public class FileInfoServiceImpl implements FileInfoService {

    private static final Logger logger = LoggerFactory.getLogger(FileInfoServiceImpl.class);

    @Resource
    @Lazy
    private FileInfoServiceImpl fileInfoService;

    @Resource
    private AppConfig appConfig;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    private UserInfoMapper userInfoMapper;

    @Resource
    private RedisComponent redisComponent;

    @Resource
    private com.easypan.component.UploadRateLimiter uploadRateLimiter;

    @Resource
    @Qualifier("virtualThreadExecutor")
    private AsyncTaskExecutor virtualThreadExecutor;

    @Resource
    private MediaTranscodeService mediaTranscodeService;

    @Resource
    @Qualifier("storageFailoverService")
    private com.easypan.strategy.StorageStrategy storageStrategy;

    @Resource
    private com.easypan.service.TenantQuotaService tenantQuotaService;

    @Value("${app.storage.type:local}")
    private String storageType;

    @Resource
    private com.easypan.service.UploadProgressService uploadProgressService;

    @Resource
    private com.easypan.service.QuickUploadService quickUploadService;

    @Override
    public List<FileInfo> findListByParam(FileInfoQuery param) {
        QueryWrapper qw = QueryWrapperBuilder.build(param);

        if (Boolean.TRUE.equals(param.getQueryNickName())) {
            qw.select(
                    FILE_INFO.FILE_ID,
                    FILE_INFO.USER_ID,
                    FILE_INFO.FILE_MD5,
                    FILE_INFO.FILE_PID,
                    FILE_INFO.FILE_SIZE,
                    FILE_INFO.FILE_NAME,
                    FILE_INFO.FILE_COVER,
                    FILE_INFO.FILE_PATH,
                    FILE_INFO.CREATE_TIME,
                    FILE_INFO.LAST_UPDATE_TIME,
                    FILE_INFO.FOLDER_TYPE,
                    FILE_INFO.FILE_CATEGORY,
                    FILE_INFO.FILE_TYPE,
                    FILE_INFO.STATUS,
                    FILE_INFO.RECOVERY_TIME,
                    FILE_INFO.DEL_FLAG,
                    USER_INFO.NICK_NAME);
            qw.leftJoin(USER_INFO).on(USER_INFO.USER_ID.eq(FILE_INFO.USER_ID));
        } else {
            qw.select(
                    FILE_INFO.FILE_ID,
                    FILE_INFO.USER_ID,
                    FILE_INFO.FILE_MD5,
                    FILE_INFO.FILE_PID,
                    FILE_INFO.FILE_SIZE,
                    FILE_INFO.FILE_NAME,
                    FILE_INFO.FILE_COVER,
                    FILE_INFO.FILE_PATH,
                    FILE_INFO.CREATE_TIME,
                    FILE_INFO.LAST_UPDATE_TIME,
                    FILE_INFO.FOLDER_TYPE,
                    FILE_INFO.FILE_CATEGORY,
                    FILE_INFO.FILE_TYPE,
                    FILE_INFO.STATUS,
                    FILE_INFO.RECOVERY_TIME,
                    FILE_INFO.DEL_FLAG);
        }

        return this.fileInfoMapper.selectListByQuery(qw);
    }

    @Override
    public Integer findCountByParam(FileInfoQuery param) {
        QueryWrapper qw = QueryWrapperBuilder.build(param, false);
        return Math.toIntExact(this.fileInfoMapper.selectCountByQuery(qw));
    }

    @Override
    public PaginationResultVO<FileInfo> findListByPage(FileInfoQuery param) {
        int count = this.findCountByParam(param);
        int pageSize = param.getPageSize() == null ? PageSize.SIZE15.getSize() : param.getPageSize();

        SimplePage page = new SimplePage(param.getPageNo(), count, pageSize);
        param.setSimplePage(page);
        List<FileInfo> list = this.findListByParam(param);
        PaginationResultVO<FileInfo> result = new PaginationResultVO<>(count, page.getPageSize(), page.getPageNo(),
                page.getPageTotal(), list);
        return result;
    }

    @Override
    public com.easypan.entity.query.CursorPage<FileInfo> findListByCursor(String userId, String cursor,
            Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        Date cursorTime = null;
        String cursorId = null;

        if (cursor != null && !cursor.isEmpty()) {
            String[] parts = cursor.split("_");
            if (parts.length >= 2) {
                try {
                    cursorTime = new Date(Long.parseLong(parts[0]));
                    cursorId = parts[1];
                } catch (NumberFormatException e) {
                    logger.warn("Invalid cursor format: {}", cursor);
                }
            }
        }

        int fetchSize = pageSize + 1;
        List<FileInfo> list;

        if (cursorTime == null || cursorId == null) {
            QueryWrapper qw = QueryWrapper.create()
                    .where(FILE_INFO.USER_ID.eq(userId))
                    .orderBy(FILE_INFO.CREATE_TIME.desc(), FILE_INFO.FILE_ID.desc())
                    .limit(fetchSize);
            list = this.fileInfoMapper.selectListByQuery(qw);
        } else {
            list = this.fileInfoMapper.selectByCursorPagination(userId, cursorTime, cursorId, fetchSize);
        }

        String nextCursor = null;
        if (list.size() > pageSize) {
            FileInfo lastItem = list.get(pageSize - 1);
            nextCursor = lastItem.getCreateTime().getTime() + "_" + lastItem.getFileId();
            list = list.subList(0, pageSize);
        }

        return com.easypan.entity.query.CursorPage.of(list, nextCursor, pageSize);
    }

    @Override
    public com.easypan.entity.query.CursorPage<FileInfo> findListByCursorWithFilter(
            String userId, String filePid, Integer delFlag, Integer category,
            String cursor, Integer pageSize) {
        if (pageSize == null || pageSize <= 0) {
            pageSize = 20;
        }
        if (pageSize > 100) {
            pageSize = 100;
        }

        Date cursorTime = null;
        String cursorId = null;

        if (cursor != null && !cursor.isEmpty()) {
            String[] parts = cursor.split("_");
            if (parts.length >= 2) {
                try {
                    cursorTime = new Date(Long.parseLong(parts[0]));
                    cursorId = parts[1];
                } catch (NumberFormatException e) {
                    logger.warn("Invalid cursor format: {}", cursor);
                }
            }
        }

        int fetchSize = pageSize + 1;
        List<FileInfo> list = this.fileInfoMapper.selectByCursorWithFilter(
                userId, filePid, delFlag, category, null, cursorTime, cursorId, fetchSize);

        String nextCursor = null;
        if (list.size() > pageSize) {
            FileInfo lastItem = list.get(pageSize - 1);
            nextCursor = lastItem.getCreateTime().getTime() + "_" + lastItem.getFileId();
            list = list.subList(0, pageSize);
        }

        return com.easypan.entity.query.CursorPage.of(list, nextCursor, pageSize);
    }

    @Override
    public Integer add(FileInfo bean) {
        ensureTenant(bean);
        return this.fileInfoMapper.insert(bean);
    }

    @Override
    public Integer addBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        listBean.forEach(this::ensureTenant);
        return this.fileInfoMapper.insertBatch(listBean);
    }

    @Override
    public Integer addOrUpdateBatch(List<FileInfo> listBean) {
        if (listBean == null || listBean.isEmpty()) {
            return 0;
        }
        listBean.forEach(this::ensureTenant);
        return this.fileInfoMapper.insertOrUpdateBatch(listBean);
    }

    private void ensureTenant(FileInfo fileInfo) {
        if (fileInfo != null && StringTools.isEmpty(fileInfo.getTenantId())) {
            fileInfo.setTenantId(TenantContextHolder.getTenantId());
        }
    }

    @Resource
    private com.easypan.service.MultiLevelCacheService multiLevelCacheService;

    @Override
    public FileInfo getFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.multiLevelCacheService.getFileInfo(fileId, userId);
    }

    @Override
    public Integer updateFileInfoByFileIdAndUserId(FileInfo bean, String fileId, String userId) {
        // 更新前清除缓存
        multiLevelCacheService.evictFileInfo(fileId, userId);
        return this.fileInfoMapper.updateByQuery(bean,
                QueryWrapper.create().where(FILE_INFO.FILE_ID.eq(fileId)).and(FILE_INFO.USER_ID.eq(userId)));
    }

    @Override
    public Integer deleteFileInfoByFileIdAndUserId(String fileId, String userId) {
        return this.fileInfoMapper.deleteByQuery(
                QueryWrapper.create().where(FILE_INFO.FILE_ID.eq(fileId)).and(FILE_INFO.USER_ID.eq(userId)));
    }

    @Override
    public UploadResultDto uploadFile(SessionWebUserDto webUserDto, String fileId, MultipartFile file, String fileName,
            String filePid, String fileMd5, Integer chunkIndex, Integer chunks) {

        if (chunkIndex == null || chunks == null || chunkIndex < 0 || chunks <= 0 || chunkIndex >= chunks) {
            throw new BusinessException("非法的分片参数");
        }

        if (!uploadRateLimiter.tryAcquire(webUserDto.getUserId())) {
            throw new BusinessException("当前上传请求过多，请稍后重试");
        }

        File tempFileFolder = null;
        Boolean uploadSuccess = true;
        try {
            if (chunkIndex == 0) {
                // 首片执行文件类型校验（扩展名 + 文件内容）。
                String fileSuffix = StringTools.getFileSuffix(fileName);

                if (com.easypan.utils.FileTypeValidator.isDangerousFileType(fileSuffix)) {
                    throw new BusinessException("不允许上传可执行文件类型");
                }

                logger.info("开始上传文件: userId={}, fileId={}, fileName={}, chunks={}",
                        webUserDto.getUserId(), fileId, fileName, chunks);

                try (InputStream inputStream = file.getInputStream()) {
                    if (!com.easypan.utils.FileTypeValidator.validateFileType(inputStream, fileSuffix)) {
                        logger.warn("文件类型校验失败: fileName={}, suffix={}", fileName, fileSuffix);
                        throw new BusinessException("文件类型不匹配，请上传正确的文件");
                    }
                } catch (IOException e) {
                    logger.error("文件类型校验异常", e);
                    throw new BusinessException("文件类型校验失败");
                }
            }

            UploadResultDto resultDto = new UploadResultDto();
            if (StringTools.isEmpty(fileId)) {
                fileId = StringTools.getRandomString(Constants.LENGTH_10);
            }
            UploadPathValidator.validateFileId(fileId);
            String reservationId = buildStorageReservationId(webUserDto.getUserId(), fileId);
            resultDto.setFileId(fileId);
            final Date curDate = new Date();
            UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());

            // 秒传逻辑
            if (chunkIndex == 0) {
                FileInfo dbFile = null;
                if (!StringTools.isEmpty(fileMd5) && redisComponent.mightContainFileMd5(fileMd5)) {
                    dbFile = this.fileInfoMapper.selectOneByMd5AndStatus(fileMd5, FileStatusEnums.USING.getStatus());
                }
                if (dbFile != null) {
                    Long dbFileSize = dbFile.getFileSize();
                    if (dbFileSize == null) {
                        logger.warn("Quick upload source file has null fileSize, fallback. fileId={}",
                                dbFile.getFileId());
                        dbFile = null;
                    } else if (dbFileSize + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
                        throw new BusinessException(ResponseCodeEnum.CODE_904);
                    }

                    if (dbFile != null && canReuseForInstantUpload(webUserDto.getUserId(), file, fileMd5, dbFile)) {
                        // 秒传按完整对象大小校验租户配额，不能只校验客户端提交的首片大小。
                        tenantQuotaService.reserveStorageQuota(reservationId, "instant", dbFileSize);
                        return fileInfoService.processInstantUpload(webUserDto, fileId, filePid, fileMd5, fileName,
                                dbFile, dbFileSize);
                    }
                }
            }

            String currentUserFolderName = webUserDto.getUserId() + fileId;
            tempFileFolder = UploadPathValidator.resolveTempFolder(appConfig.getProjectFolder(),
                    webUserDto.getUserId(), fileId).toFile();
            if (!tempFileFolder.exists() && !tempFileFolder.mkdirs()) {
                logger.error("Failed to create temp folder: {}", tempFileFolder.getAbsolutePath());
                throw new BusinessException("创建临时目录失败");
            }

            Long currentTempSize = redisComponent.getFileTempSize(webUserDto.getUserId(), fileId);
            if (currentTempSize == null) {
                currentTempSize = 0L;
            }

            File newFile = new File(tempFileFolder.getPath() + "/" + chunkIndex);
            boolean chunkAlreadyStored = newFile.exists() && newFile.length() == file.getSize();
            long additionalSize = chunkAlreadyStored ? 0L : file.getSize();

            if (additionalSize + currentTempSize + spaceDto.getUseSpace() > spaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }
            // 只预留本次新增分片，Redis Lua 会把同一会话累计预留量与其他并发上传原子比较。
            tenantQuotaService.reserveStorageQuota(reservationId, "chunk:" + chunkIndex, additionalSize);

            // 文件写入是 IO 操作，不放在事务中执行。
            if (!chunkAlreadyStored) {
                file.transferTo(newFile);
                if (newFile.length() != file.getSize()) {
                    throw new BusinessException("分片大小校验失败，请重试上传");
                }
                redisComponent.saveFileTempSize(webUserDto.getUserId(), fileId, file.getSize());
                // 记录上传进度，用于断点续传与前端展示。
                uploadProgressService.updateProgress(webUserDto.getUserId(), fileId, chunkIndex, chunks);
            }

            if (chunkIndex < chunks - 1) {
                resultDto.setStatus(UploadStatusEnums.UPLOADING.getCode());
                return resultDto;
            }

            // 最后一个分片上传完成，进入事务保存元数据。
            return fileInfoService.completeUploadAndSave(webUserDto, fileId, filePid, fileMd5, fileName,
                    currentUserFolderName, curDate);

        } catch (BusinessException e) {
            uploadSuccess = false;
            logger.error("文件上传失败", e);
            throw e;
        } catch (Exception e) {
            uploadSuccess = false;
            logger.error("文件上传失败", e);
            throw new BusinessException("文件上传失败");
        } finally {
            uploadRateLimiter.release(webUserDto.getUserId());
            if (!uploadSuccess) {
                tenantQuotaService.releaseStorageReservation(buildStorageReservationId(webUserDto.getUserId(), fileId));
            }
            if (tempFileFolder != null && !uploadSuccess) {
                try {
                    FileUtils.deleteDirectory(tempFileFolder);
                } catch (IOException e) {
                    logger.error("删除临时目录失败");
                }
            }
        }
    }

    /**
     * 处理秒传入库（事务方法）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto processInstantUpload(SessionWebUserDto webUserDto, String fileId, String filePid,
            String fileMd5, String fileName, FileInfo dbFile, Long dbFileSize) {
        UploadResultDto resultDto = new UploadResultDto();
        resultDto.setFileId(fileId);
        Date curDate = new Date();

        dbFile.setFileId(fileId);
        dbFile.setFilePid(filePid);
        dbFile.setUserId(webUserDto.getUserId());
        dbFile.setTenantId(TenantContextHolder.getTenantId());
        dbFile.setFileMd5(null);
        dbFile.setCreateTime(curDate);
        dbFile.setLastUpdateTime(curDate);
        dbFile.setStatus(FileStatusEnums.USING.getStatus());
        dbFile.setDelFlag(FileDelFlagEnums.USING.getFlag());
        dbFile.setFileMd5(fileMd5);
        fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
        dbFile.setFileName(fileName);
        this.fileInfoMapper.insert(dbFile);
        resultDto.setStatus(UploadStatusEnums.UPLOAD_SECONDS.getCode());
        updateUserSpace(webUserDto, dbFileSize);
        registerStorageReservationCommit(buildStorageReservationId(webUserDto.getUserId(), fileId));

        logger.info("秒传成功: userId={}, fileId={}, fileName={}, md5={}",
                webUserDto.getUserId(), fileId, fileName, fileMd5);
        return resultDto;
    }

    /**
     * 校验秒传的持有证明。当前用户自己的文件可以直接复用；跨用户复用必须提交与源对象前缀一致的首片，
     * 不能仅凭客户端声明的 MD5 获得另一个用户的对象引用。
     */
    private boolean canReuseForInstantUpload(String userId, MultipartFile firstChunk, String requestedMd5,
            FileInfo sourceFile) {
        if (sourceFile.getFilePath() == null || StringTools.isEmpty(sourceFile.getFileMd5())
                || !sourceFile.getFileMd5().equalsIgnoreCase(requestedMd5)) {
            return false;
        }
        String sourceTenantId = sourceFile.getTenantId();
        if (sourceTenantId == null || sourceTenantId.isBlank()) {
            // V5 migration 为历史记录提供了 default；对迁移前/测试中的 null 做同样的兼容归一化。
            sourceTenantId = "default";
        }
        if (!TenantContextHolder.getTenantId().equals(sourceTenantId)) {
            return false;
        }
        if (userId.equals(sourceFile.getUserId())) {
            return true;
        }
        if (firstChunk == null || storageStrategy == null) {
            return false;
        }

        try (InputStream source = storageStrategy.download(sourceFile.getFilePath())) {
            byte[] expectedPrefix = firstChunk.getBytes();
            byte[] actualPrefix = source.readNBytes(expectedPrefix.length);
            return actualPrefix.length == expectedPrefix.length && MessageDigest.isEqual(actualPrefix, expectedPrefix);
        } catch (IOException | RuntimeException e) {
            logger.warn("跨用户秒传持有证明校验失败: sourceFileId={}, userId={}", sourceFile.getFileId(), userId);
            return false;
        }
    }

    /**
     * 完成上传并保存元数据（事务方法）。
     */
    @Transactional(rollbackFor = Exception.class)
    public UploadResultDto completeUploadAndSave(SessionWebUserDto webUserDto, String fileId, String filePid,
            String fileMd5, String fileName, String currentUserFolderName, Date curDate) {
        UploadResultDto resultDto = new UploadResultDto();
        resultDto.setFileId(fileId);

        fileName = autoRename(filePid, webUserDto.getUserId(), fileName);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(fileId);
        fileInfo.setUserId(webUserDto.getUserId());
        fileInfo.setTenantId(TenantContextHolder.getTenantId());
        fileInfo.setFileMd5(fileMd5);
        fileInfo.setFileName(fileName);
        String fileSuffix = StringTools.getFileSuffix(fileName);
        String month = DateUtil.format(curDate, DateTimePatternEnum.YYYYMM.getPattern());
        String realFileName = currentUserFolderName + fileSuffix;
        fileInfo.setFilePath(month + "/" + realFileName);
        fileInfo.setFilePid(filePid);
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        FileTypeEnums fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);
        fileInfo.setFileCategory(fileTypeEnum.getCategory().getCategory());
        fileInfo.setFileType(fileTypeEnum.getType());
        fileInfo.setStatus(FileStatusEnums.TRANSFER.getStatus());
        fileInfo.setFolderType(FileFolderTypeEnums.FILE.getType());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        this.fileInfoMapper.insert(fileInfo);

        if (!StringTools.isEmpty(fileMd5)) {
            redisComponent.addFileMd5ToBloom(fileMd5);
        }

        // Redis 临时计数可能过期或因并发重试短暂失准；配额/用户空间以磁盘上的实际分片字节数为准。
        Long totalSize = calculateTempUploadSize(webUserDto.getUserId(), fileId);
        updateUserSpace(webUserDto, totalSize);
        registerStorageReservationCommit(buildStorageReservationId(webUserDto.getUserId(), fileId));
        // 上传完成后清除进度
        uploadProgressService.clearProgress(webUserDto.getUserId(), fileId);

        resultDto.setStatus(UploadStatusEnums.UPLOAD_FINISH.getCode());

        logger.info("文件元数据保存完成: userId={}, fileId={}", webUserDto.getUserId(), fileId);

        // 使用事务同步机制，在事务提交后触发转码。单元测试或非 Spring 调用没有事务时，
        // 直接触发，避免 TransactionSynchronizationManager 抛出 "synchronization is not active"。
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    fileInfoService.transferFile(fileInfo.getFileId(), webUserDto);
                }
            });
        } else {
            logger.warn("上传完成时没有活动事务，直接触发转码: fileId={}", fileInfo.getFileId());
            fileInfoService.transferFile(fileInfo.getFileId(), webUserDto);
        }

        return resultDto;
    }

    /** 在元数据事务提交后提交租户空间预留，事务回滚时由上传 finally 释放。 */
    private void registerStorageReservationCommit(String reservationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tenantQuotaService.commitStorageReservation(reservationId);
                }
            });
        } else {
            tenantQuotaService.commitStorageReservation(reservationId);
        }
    }

    /** 将用户绑定到预留键，避免同一租户中恶意复用 fileId 互相覆盖预留。 */
    private String buildStorageReservationId(String userId, String fileId) {
        return userId + ":" + fileId;
    }

    /** 删除事务提交后再失效租户缓存，避免其他请求在旧数据库快照上重建过期计数。 */
    private void registerTenantStorageInvalidation() {
        String tenantId = TenantContextHolder.getTenantId();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tenantQuotaService.invalidateUsedStorage(tenantId);
                }
            });
        } else {
            tenantQuotaService.invalidateUsedStorage(tenantId);
        }
    }

    private long calculateTempUploadSize(String userId, String fileId) {
        Path tempFolder = UploadPathValidator.resolveTempFolder(appConfig.getProjectFolder(), userId, fileId);
        long totalSize = 0L;
        try (Stream<Path> chunks = Files.list(tempFolder)) {
            for (Path chunk : chunks.toList()) {
                if (!chunk.getFileName().toString().matches("\\d+")
                        || !Files.isRegularFile(chunk, LinkOption.NOFOLLOW_LINKS)) {
                    throw new BusinessException("发现非法分片文件");
                }
                totalSize = Math.addExact(totalSize, Files.size(chunk));
            }
            return totalSize;
        } catch (IOException | UncheckedIOException e) {
            throw new BusinessException("读取上传分片大小失败");
        } catch (ArithmeticException e) {
            throw new BusinessException("上传文件大小超出支持范围");
        }
    }

    private void updateUserSpace(SessionWebUserDto webUserDto, Long totalSize) {
        Integer count = userInfoMapper.incrementUseSpace(webUserDto.getUserId(), totalSize);
        if (count == 0) {
            throw new BusinessException(ResponseCodeEnum.CODE_904);
        }
        UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(webUserDto.getUserId());
        spaceDto.setUseSpace(spaceDto.getUseSpace() + totalSize);
        redisComponent.saveUserSpaceUse(webUserDto.getUserId(), spaceDto);
    }

    private String autoRename(String filePid, String userId, String fileName) {
        long count = this.fileInfoMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_PID.eq(filePid))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag()))
                        .and(FILE_INFO.FILE_NAME.eq(fileName)));
        if (count > 0) {
            return StringTools.rename(fileName);
        }
        return fileName;
    }

    /**
     * 异步转码文件。
     *
     * @param fileId     文件ID
     * @param webUserDto 用户会话信息
     */
    @Async("virtualThreadExecutor")
    public void transferFile(String fileId, SessionWebUserDto webUserDto) {
        Boolean transferSuccess = true;
        String targetFilePath = null;
        String cover = null;
        FileTypeEnums fileTypeEnum = null;
        FileInfo fileInfo = getFileInfoByFileIdAndUserId(fileId, webUserDto.getUserId());
        try {
            if (fileInfo == null || !FileStatusEnums.TRANSFER.getStatus().equals(fileInfo.getStatus())) {
                return;
            }
            String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
            String currentUserFolderName = webUserDto.getUserId() + fileId;
            File fileFolder = new File(tempFolderName + currentUserFolderName);
            if (!fileFolder.exists() && !fileFolder.mkdirs()) {
                logger.error("Failed to create folder: {}", fileFolder.getAbsolutePath());
                throw new BusinessException("创建目录失败");
            }
            String fileSuffix = StringTools.getFileSuffix(fileInfo.getFileName());
            String month = DateUtil.format(fileInfo.getCreateTime(), DateTimePatternEnum.YYYYMM.getPattern());
            String targetFolderName = appConfig.getFileRootPath();
            File targetFolder = new File(targetFolderName + "/" + month);
            if (!targetFolder.exists() && !targetFolder.mkdirs()) {
                logger.error("Failed to create target folder: {}", targetFolder.getAbsolutePath());
                throw new BusinessException("创建目标目录失败");
            }
            String realFileName = currentUserFolderName + fileSuffix;
            targetFilePath = targetFolder.getPath() + "/" + realFileName;
            unionWithNIO(fileFolder.getPath(), targetFilePath, fileInfo.getFileName(), true);

            com.easypan.strategy.StorageStrategy storageStrategy = this.storageStrategy;
            storageStrategy.upload(new File(targetFilePath), fileInfo.getFilePath());

            fileTypeEnum = FileTypeEnums.getFileTypeBySuffix(fileSuffix);

            logger.info("开始转码文件: fileId={}, userId={}, fileType={}",
                    fileId, webUserDto.getUserId(), fileTypeEnum);

            if (FileTypeEnums.VIDEO == fileTypeEnum) {
                cutFile4Video(fileId, targetFilePath);
                cover = month + "/" + currentUserFolderName + Constants.IMAGE_PNG_SUFFIX;
                String coverPath = targetFolderName + "/" + cover;
                File coverFile = new File(coverPath);
                mediaTranscodeService.createVideoCover(new File(targetFilePath), Constants.LENGTH_150, coverFile);
                if (coverFile.exists()) {
                    storageStrategy.upload(coverFile, cover);
                }
                String tsFolderName = targetFilePath.substring(0, targetFilePath.lastIndexOf("."));
                File tsFolder = new File(tsFolderName);
                if (tsFolder.exists()) {
                    storageStrategy.uploadDirectory(
                            fileInfo.getFilePath().substring(0, fileInfo.getFilePath().lastIndexOf(".")), tsFolder);
                }
            } else if (FileTypeEnums.IMAGE == fileTypeEnum) {
                cover = month + "/" + realFileName.replace(".", "_.");
                String coverPath = targetFolderName + "/" + cover;
                File coverFile = new File(coverPath);
                Boolean created = mediaTranscodeService.createThumbnail(new File(targetFilePath), Constants.LENGTH_150,
                        coverFile, false);
                if (!created) {
                    FileUtils.copyFile(new File(targetFilePath), coverFile);
                }
                storageStrategy.upload(coverFile, cover);
            }
        } catch (RuntimeException e) {
            logger.error("文件转码失败: fileId={}, userId={}", fileId, webUserDto.getUserId(), e);
            transferSuccess = false;
        } catch (Exception e) {
            logger.error("文件转码失败: fileId={}, userId={}", fileId, webUserDto.getUserId(), e);
            transferSuccess = false;
        } finally {
            FileInfo updateInfo = new FileInfo();
            File targetFile = targetFilePath != null ? new File(targetFilePath) : null;
            updateInfo.setFileSize(targetFile != null && targetFile.exists() ? targetFile.length() : 0L);
            if (transferSuccess && targetFile != null && targetFile.exists()) {
                try {
                    updateInfo.setFileMd5(calculateFileMd5(targetFile));
                } catch (IOException e) {
                    logger.warn("计算文件 MD5 失败，保留原上传摘要: fileId={}", fileId, e);
                }
            }
            updateInfo.setFileCover(cover);
            updateInfo.setStatus(
                    transferSuccess ? FileStatusEnums.USING.getStatus() : FileStatusEnums.TRANSFER_FAIL.getStatus());
            fileInfoMapper.updateFileStatusWithOldStatus(fileId, webUserDto.getUserId(), updateInfo,
                    FileStatusEnums.TRANSFER.getStatus());
            try {
                tenantQuotaService.invalidateUsedStorage(fileInfo == null ? null : fileInfo.getTenantId());
            } catch (RuntimeException e) {
                logger.warn("转码后清理租户空间缓存失败: fileId={}", fileId, e);
            }

            // transferFile() 通过 MultiLevelCacheService（L1/L2）读取 FileInfo，
            // 这里必须主动失效缓存，避免“转码中”状态在缓存中滞留。
            try {
                multiLevelCacheService.evictFileInfo(fileId, webUserDto.getUserId());
            } catch (Exception e) {
                logger.warn("转码后清理文件缓存失败: fileId={}, userId={}",
                        fileId, webUserDto.getUserId(), e);
            }

            if (targetFilePath != null
                    && !com.easypan.entity.enums.StorageTypeEnum.LOCAL.getCode().equals(storageType)) {
                FileUtils.deleteQuietly(new File(targetFilePath));
                String tsFolderName = targetFilePath.substring(0, targetFilePath.lastIndexOf("."));
                FileUtils.deleteQuietly(new File(tsFolderName));
            }

            logger.info("转码流程结束: fileId={}, userId={}, success={}",
                    fileId, webUserDto.getUserId(), transferSuccess);
        }
    }

    private static void unionWithNIO(String dirPath, String toFilePath, String fileName, boolean delSource)
            throws BusinessException {
        File dir = new File(dirPath);
        if (!dir.exists()) {
            throw new BusinessException("目录不存在");
        }

        File[] chunks = dir.listFiles();
        if (chunks == null || chunks.length == 0) {
            throw new BusinessException("未找到分片文件");
        }

        // 分片文件名是数字序号；不能按字符串排序，否则 10、11 会排在 2 前面，造成文件内容损坏。
        Set<Long> chunkIndexes = new HashSet<>();
        for (File chunk : chunks) {
            if (!chunk.isFile() || !chunk.getName().matches("\\d+")) {
                throw new BusinessException("发现非法分片文件");
            }
            try {
                if (!chunkIndexes.add(Long.parseLong(chunk.getName()))) {
                    throw new BusinessException("分片序号重复");
                }
            } catch (NumberFormatException e) {
                throw new BusinessException("分片序号超出范围");
            }
        }
        Arrays.sort(chunks, Comparator.comparingLong((File chunk) -> Long.parseLong(chunk.getName()))
                .thenComparing(File::getName));

        java.nio.file.Path targetPath = java.nio.file.Paths.get(toFilePath);
        try (java.nio.channels.FileChannel outChannel = java.nio.channels.FileChannel.open(
                targetPath,
                java.nio.file.StandardOpenOption.CREATE,
                java.nio.file.StandardOpenOption.WRITE,
                java.nio.file.StandardOpenOption.TRUNCATE_EXISTING)) {

            for (File chunk : chunks) {
                try (java.nio.channels.FileChannel inChannel = java.nio.channels.FileChannel.open(
                        chunk.toPath(),
                        java.nio.file.StandardOpenOption.READ)) {
                    long size = inChannel.size();
                    long position = 0L;
                    // 限制单次 transferTo 为 8MB，避免大文件合并时资源峰值过高。
                    final long TRANSFER_CHUNK_SIZE = 8L * 1024 * 1024;
                    while (position < size) {
                        long count = Math.min(TRANSFER_CHUNK_SIZE, size - position);
                        long transferred = inChannel.transferTo(position, count, outChannel);
                        if (transferred <= 0) {
                            break;
                        }
                        position += transferred;
                    }
                } catch (Exception e) {
                    logger.error("NIO 合并分片失败", e);
                    throw new BusinessException("合并文件失败");
                }
            }
        } catch (Exception e) {
            logger.error("NIO 合并文件失败: {}", fileName, e);
            throw new BusinessException("合并文件" + fileName + "出错了");
        } finally {
            if (delSource && dir.exists()) {
                try {
                    FileUtils.deleteDirectory(dir);
                } catch (IOException e) {
                    logger.error("删除临时目录失败: {}", dir.getPath(), e);
                }
            }
        }
    }

    /**
     * 在服务端对合并后的完整文件计算摘要，避免把客户端声明的 MD5 当成可信内容身份。
     */
    private String calculateFileMd5(File file) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance("MD5");
            try (InputStream input = new DigestInputStream(new FileInputStream(file), digest)) {
                input.transferTo(java.io.OutputStream.nullOutputStream());
            }
            return java.util.HexFormat.of().formatHex(digest.digest());
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("JDK 不支持 MD5", e);
        }
    }

    private void cutFile4Video(String fileId, String videoFilePath) {
        File tsFolder = new File(videoFilePath.substring(0, videoFilePath.lastIndexOf(".")));
        if (!tsFolder.exists() && !tsFolder.mkdirs()) {
            logger.error("Failed to create ts folder: {}", tsFolder.getAbsolutePath());
            return;
        }

        String tsPath = tsFolder + "/" + Constants.TS_NAME;
        mediaTranscodeService.transcodeToTs(videoFilePath, tsPath);
        mediaTranscodeService.cutToM3u8(tsPath, tsFolder.getPath(), fileId);
        File tsFile = new File(tsPath);
        if (tsFile.exists() && !tsFile.delete()) {
            logger.warn("Failed to delete ts file: {}", tsPath);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo rename(String fileId, String userId, String fileName) {
        FileInfo fileInfo = getFileInfoByFileIdAndUserId(fileId, userId);
        if (fileInfo == null) {
            throw new BusinessException("文件不存在");
        }
        if (fileInfo.getFileName().equals(fileName)) {
            return fileInfo;
        }
        String filePid = fileInfo.getFilePid();
        checkFileName(filePid, userId, fileName, fileInfo.getFolderType());
        if (FileFolderTypeEnums.FILE.getType().equals(fileInfo.getFolderType())) {
            fileName = fileName + StringTools.getFileSuffix(fileInfo.getFileName());
        }
        Date curDate = new Date();
        FileInfo dbInfo = new FileInfo();
        dbInfo.setFileName(fileName);
        dbInfo.setLastUpdateTime(curDate);
        updateFileInfoByFileIdAndUserId(dbInfo, fileId, userId);

        long count = this.fileInfoMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.FILE_PID.eq(filePid))
                        .and(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_NAME.eq(fileName))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag())));
        if (count > 1) {
            throw new BusinessException("文件名" + fileName + "已经存在");
        }
        fileInfo.setFileName(fileName);
        fileInfo.setLastUpdateTime(curDate);
        return fileInfo;
    }

    private void checkFileName(String filePid, String userId, String fileName, Integer folderType) {
        long count = this.fileInfoMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.FOLDER_TYPE.eq(folderType))
                        .and(FILE_INFO.FILE_NAME.eq(fileName))
                        .and(FILE_INFO.FILE_PID.eq(filePid))
                        .and(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag())));
        if (count > 0) {
            throw new BusinessException("此目录下已存在同名文件，请修改名称");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public FileInfo newFolder(String filePid, String userId, String folderName) {
        checkFileName(filePid, userId, folderName, FileFolderTypeEnums.FOLDER.getType());
        Date curDate = new Date();
        FileInfo fileInfo = new FileInfo();
        fileInfo.setFileId(StringTools.getRandomString(Constants.LENGTH_10));
        fileInfo.setUserId(userId);
        fileInfo.setTenantId(TenantContextHolder.getTenantId());
        fileInfo.setFilePid(filePid);
        fileInfo.setFileName(folderName);
        fileInfo.setFolderType(FileFolderTypeEnums.FOLDER.getType());
        fileInfo.setCreateTime(curDate);
        fileInfo.setLastUpdateTime(curDate);
        fileInfo.setStatus(FileStatusEnums.USING.getStatus());
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        this.fileInfoMapper.insert(fileInfo);

        long count = this.fileInfoMapper.selectCountByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.FILE_PID.eq(filePid))
                        .and(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_NAME.eq(folderName))
                        .and(FILE_INFO.FOLDER_TYPE.eq(FileFolderTypeEnums.FOLDER.getType()))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag())));
        if (count > 1) {
            throw new BusinessException("文件夹" + folderName + "已经存在");
        }
        fileInfo.setFileName(folderName);
        fileInfo.setLastUpdateTime(curDate);
        return fileInfo;
    }

    /**
     * 更改文件所属文件夹。
     *
     * @param fileIds 文件ID列表
     * @param filePid 目标父文件夹ID
     * @param userId  用户ID
     */
    @Transactional(rollbackFor = Exception.class)
    public void changeFileFolder(String fileIds, String filePid, String userId) {
        if (fileIds.equals(filePid)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "不能将文件移动到自身");
        }
        if (!Constants.ZERO_STR.equals(filePid)) {
            FileInfo fileInfo = fileInfoService.getFileInfoByFileIdAndUserId(filePid, userId);
            if (fileInfo == null || !FileDelFlagEnums.USING.getFlag().equals(fileInfo.getDelFlag())) {
                throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "目标文件夹不存在或已被删除");
            }
        }
        String[] fileIdArray = fileIds.split(",");

        List<FileInfo> dbFileList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.FILE_PID.eq(filePid))
                        .and(FILE_INFO.USER_ID.eq(userId)));

        Map<String, FileInfo> dbFileNameMap = dbFileList.stream()
                .collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));

        List<FileInfo> selectFileList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_ID.in((Object[]) fileIdArray)));

        List<FileInfo> updateList = new ArrayList<>();
        Date curDate = new Date(); // 统一更新时间，便于排序与审计。

        for (FileInfo item : selectFileList) {
            FileInfo rootFileInfo = dbFileNameMap.get(item.getFileName());
            FileInfo updateInfo = new FileInfo();
            updateInfo.setFileId(item.getFileId()); // 主键用于定位更新目标。
            updateInfo.setUserId(userId); // 保留用户范围，避免跨用户误更新。

            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                updateInfo.setFileName(fileName);
            }
            updateInfo.setFilePid(filePid);
            updateInfo.setLastUpdateTime(curDate);
            updateList.add(updateInfo);
        }

        if (!updateList.isEmpty()) {
            // 批量更新移动结果，减少逐条更新带来的数据库往返。
            // 这里直接使用 Mapper 的 updateBatch，避免额外包装层开销。
            this.fileInfoMapper.updateBatch(updateList);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void removeFile2RecycleBatch(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");

        List<FileInfo> fileInfoList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_ID.in((Object[]) fileIdArray))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag())));

        if (fileInfoList.isEmpty()) {
            return;
        }
        List<String> delFilePidList = new ArrayList<>();
        List<String> folderIds = fileInfoList.stream()
                .filter(item -> FileFolderTypeEnums.FOLDER.getType().equals(item.getFolderType()))
                .map(FileInfo::getFileId)
                .collect(Collectors.toList());
        if (!folderIds.isEmpty()) {
            delFilePidList = this.fileInfoMapper.selectDescendantFolderIds(folderIds, userId,
                    FileDelFlagEnums.USING.getFlag());
        }

        if (!delFilePidList.isEmpty()) {
            FileInfo updateInfo = new FileInfo();
            updateInfo.setDelFlag(FileDelFlagEnums.DEL.getFlag());
            this.fileInfoMapper.updateFileDelFlagBatch(updateInfo, userId, delFilePidList, null,
                    FileDelFlagEnums.USING.getFlag());
        }

        List<String> delFileIdList = Arrays.asList(fileIdArray);
        FileInfo fileInfo = new FileInfo();
        fileInfo.setRecoveryTime(new Date());
        fileInfo.setDelFlag(FileDelFlagEnums.RECYCLE.getFlag());
        this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList,
                FileDelFlagEnums.USING.getFlag());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void recoverFileBatch(String userId, String fileIds) {
        String[] fileIdArray = fileIds.split(",");

        List<FileInfo> fileInfoList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_ID.in((Object[]) fileIdArray))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.RECYCLE.getFlag())));

        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        List<String> folderIds = fileInfoList.stream()
                .filter(item -> FileFolderTypeEnums.FOLDER.getType().equals(item.getFolderType()))
                .map(FileInfo::getFileId)
                .collect(Collectors.toList());
        if (!folderIds.isEmpty()) {
            delFileSubFolderFileIdList = this.fileInfoMapper.selectDescendantFolderIds(folderIds, userId,
                    null);
        }

        List<FileInfo> allRootFileList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag()))
                        .and(FILE_INFO.FILE_PID.eq(Constants.ZERO_STR)));

        Map<String, FileInfo> rootFileMap = allRootFileList.stream()
                .collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));

        if (!delFileSubFolderFileIdList.isEmpty()) {
            FileInfo fileInfo = new FileInfo();
            fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
            this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, delFileSubFolderFileIdList, null,
                    FileDelFlagEnums.DEL.getFlag());
        }
        FileInfo fileInfo = new FileInfo();
        fileInfo.setDelFlag(FileDelFlagEnums.USING.getFlag());
        fileInfo.setFilePid(Constants.ZERO_STR);
        fileInfo.setLastUpdateTime(new Date());
        List<String> delFileIdList = Arrays.asList(fileIdArray);
        this.fileInfoMapper.updateFileDelFlagBatch(fileInfo, userId, null, delFileIdList,
                FileDelFlagEnums.RECYCLE.getFlag());

        // 批量更新重命名结果，避免 N+1 写入
        Date updateTime = new Date();
        List<FileInfo> renameList = new ArrayList<>();
        for (FileInfo item : fileInfoList) {
            FileInfo rootFileInfo = rootFileMap.get(item.getFileName());
            if (rootFileInfo != null) {
                String fileName = StringTools.rename(item.getFileName());
                FileInfo updateInfo = new FileInfo();
                updateInfo.setFileId(item.getFileId());
                updateInfo.setUserId(userId);
                updateInfo.setFilePid(Constants.ZERO_STR);
                updateInfo.setFileName(fileName);
                updateInfo.setLastUpdateTime(updateTime);
                renameList.add(updateInfo);
            }
        }
        if (!renameList.isEmpty()) {
            this.fileInfoMapper.updateBatch(renameList);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delFileBatch(String userId, String fileIds, Boolean adminOp) {
        String[] fileIdArray = fileIds.split(",");

        QueryWrapper queryQw = QueryWrapper.create()
                .where(FILE_INFO.USER_ID.eq(userId))
                .and(FILE_INFO.FILE_ID.in((Object[]) fileIdArray));
        if (!adminOp) {
            queryQw.and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.RECYCLE.getFlag()));
        }
        List<FileInfo> fileInfoList = this.fileInfoMapper.selectListByQuery(queryQw);
        if (fileInfoList == null || fileInfoList.isEmpty()) {
            return;
        }

        List<String> delFileSubFolderFileIdList = new ArrayList<>();
        List<String> folderIds = fileInfoList.stream()
                .filter(item -> FileFolderTypeEnums.FOLDER.getType().equals(item.getFolderType()))
                .map(FileInfo::getFileId)
                .collect(Collectors.toList());
        if (!folderIds.isEmpty()) {
            delFileSubFolderFileIdList = this.fileInfoMapper.selectDescendantFolderIds(folderIds, userId,
                    null);
        }

        // 目录硬删除时需要包含后代记录，用于统一清理存储与缓存。
        List<FileInfo> deleteInfoList = fileInfoList;
        if (!folderIds.isEmpty()) {
            List<FileInfo> descendants = this.fileInfoMapper.selectDescendantFiles(folderIds, userId, null);
            if (descendants != null && !descendants.isEmpty()) {
                java.util.Map<String, FileInfo> merged = new java.util.HashMap<>();
                for (FileInfo f : fileInfoList) {
                    merged.put(f.getFileId(), f);
                }
                for (FileInfo f : descendants) {
                    merged.put(f.getFileId(), f);
                }
                deleteInfoList = new java.util.ArrayList<>(merged.values());
            }
        }

        if (!delFileSubFolderFileIdList.isEmpty()) {
            this.fileInfoMapper.delFileBatch(userId, delFileSubFolderFileIdList, null,
                    adminOp ? null : FileDelFlagEnums.DEL.getFlag());
        }
        List<String> rootFileIdList = fileInfoList.stream().map(FileInfo::getFileId).toList();
        this.fileInfoMapper.delFileBatch(userId, null, rootFileIdList,
                adminOp ? null : FileDelFlagEnums.RECYCLE.getFlag());

        Long useSpace = this.fileInfoMapper.selectUseSpace(userId);
        UserInfo userInfo = new UserInfo();
        userInfo.setUseSpace(useSpace);
        userInfoMapper.updateByQuery(userInfo, QueryWrapper.create().where(USER_INFO.USER_ID.eq(userId)));

        UserSpaceDto userSpaceDto = redisComponent.getUserSpaceUse(userId);
        if (userSpaceDto != null) {
            userSpaceDto.setUseSpace(useSpace);
            redisComponent.saveUserSpaceUse(userId, userSpaceDto);
        }
        registerTenantStorageInvalidation();

        List<String> filePathList = new ArrayList<>();
        java.util.Set<String> dirPathSet = new java.util.HashSet<>();
        for (FileInfo item : deleteInfoList) {
            try {
                multiLevelCacheService.evictFileInfo(item.getFileId(), userId);
            } catch (Exception e) {
                logger.warn("删除后清理文件缓存失败: fileId={}, userId={}", item.getFileId(), userId,
                        e);
            }

            // 删除文件时同步清理 MD5 缓存，避免秒传命中已删除文件。
            if (!StringTools.isEmpty(item.getFileMd5())) {
                try {
                    quickUploadService.clearMd5Cache(item.getFileMd5());
                } catch (Exception e) {
                    logger.warn("清除 MD5 缓存失败: fileMd5={}", item.getFileMd5(), e);
                }
            }

            if (FileFolderTypeEnums.FILE.getType().equals(item.getFolderType()) && item.getFilePath() != null) {
                // 秒传/转存可能让多条元数据共享同一个物理对象；只有最后一个引用删除后才允许清理。
                if (!hasRemainingStoredReference(item.getFilePath())) {
                    filePathList.add(item.getFilePath());
                }
                if (!StringTools.isEmpty(item.getFileCover())
                        && !hasRemainingStoredReference(item.getFileCover())) {
                    filePathList.add(item.getFileCover());
                }
                if (item.getFileType() != null && FileTypeEnums.VIDEO.getType().equals(item.getFileType())
                        && item.getFilePath().contains(".")
                        && !hasRemainingStoredReference(item.getFilePath())) {
                    dirPathSet.add(item.getFilePath().substring(0, item.getFilePath().lastIndexOf(".")));
                }
            }
        }

        if (!filePathList.isEmpty() || !dirPathSet.isEmpty()) {
            final List<String> pathsToDelete = filePathList;
            final java.util.Set<String> dirsToDelete = dirPathSet;
            CompletableFuture.runAsync(() -> {
                try {
                    // 删除在事务提交后异步执行；再次回源检查，降低“检查后又创建引用”导致误删共享对象的窗口。
                    List<String> safePaths = pathsToDelete.stream()
                            .filter(path -> !hasRemainingStoredReference(path))
                            .toList();
                    if (!safePaths.isEmpty()) {
                        storageStrategy.deleteBatch(safePaths);
                    }
                    if (!dirsToDelete.isEmpty()) {
                        for (String dir : dirsToDelete) {
                            try {
                                if (!hasRemainingStoredReferencePrefix(dir)) {
                                    storageStrategy.deleteDirectory(dir);
                                }
                            } catch (Exception e) {
                                logger.warn("批量删除存储目录失败: {}", dir, e);
                            }
                        }
                    }
                } catch (Exception e) {
                    logger.warn("批量删除存储文件失败", e);
                }
            }, virtualThreadExecutor);
        }
    }

    /**
     * 删除物理对象前查询数据库中是否仍有其他元数据记录引用它。
     * 查询失败时采取保守策略，宁可暂不删除，也不冒险删除共享对象。
     */
    private boolean hasRemainingStoredReference(String storedPath) {
        try {
            return fileInfoMapper.countReferencesByStoredPath(storedPath) > 0;
        } catch (RuntimeException e) {
            logger.error("查询物理对象引用失败，跳过删除: path={}", storedPath, e);
            return true;
        }
    }

    private boolean hasRemainingStoredReferencePrefix(String pathPrefix) {
        try {
            return fileInfoMapper.countReferencesByStoredPathPrefix(pathPrefix) > 0;
        } catch (RuntimeException e) {
            logger.error("查询物理目录引用失败，跳过删除: pathPrefix={}", pathPrefix, e);
            return true;
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void saveShare(String shareRootFilePid, String shareFileIds, String myFolderId, String shareUserId,
            String currentUserId) {
        String[] shareFileIdArray = shareFileIds.split(",");

        // 1. Fetch target folder current files (for duplicate name check)
        List<FileInfo> currentFileList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(currentUserId))
                        .and(FILE_INFO.FILE_PID.eq(myFolderId)));

        Map<String, FileInfo> currentFileMap = currentFileList.stream()
                .collect(Collectors.toMap(FileInfo::getFileName, Function.identity(), (file1, file2) -> file2));

        // 2. Fetch all shared root files
        List<FileInfo> shareRootFileList = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(shareUserId))
                        .and(FILE_INFO.FILE_ID.in((Object[]) shareFileIdArray)));

        // 3. Separate folders to fetch descendants
        List<String> rootFolderIds = shareRootFileList.stream()
                .filter(item -> FileFolderTypeEnums.FOLDER.getType().equals(item.getFolderType()))
                .map(FileInfo::getFileId)
                .collect(Collectors.toList());

        // 4. Fetch all descendants using recursive CTE
        List<FileInfo> allDescendants = new ArrayList<>();
        if (!rootFolderIds.isEmpty()) {
            allDescendants = fileInfoMapper.selectDescendantFiles(rootFolderIds, shareUserId,
                    FileDelFlagEnums.USING.getFlag());
        }

        // 5. Group descendants by PID for easy lookup
        Map<String, List<FileInfo>> childrenMap = allDescendants.stream()
                .collect(Collectors.groupingBy(FileInfo::getFilePid));

        // 6. Pre-calculate total size BEFORE any insertion (space pre-check)
        Long totalSize = 0L;
        for (FileInfo root : shareRootFileList) {
            totalSize += (root.getFileSize() == null ? 0L : root.getFileSize());
        }
        for (FileInfo desc : allDescendants) {
            totalSize += (desc.getFileSize() == null ? 0L : desc.getFileSize());
        }

        // 7. Pre-check space availability
        if (totalSize > 0) {
            UserSpaceDto spaceDto = redisComponent.getUserSpaceUse(currentUserId);
            if (spaceDto.getUseSpace() + totalSize > spaceDto.getTotalSpace()) {
                throw new BusinessException(ResponseCodeEnum.CODE_904);
            }
        }

        // 8. Prepare for copy
        List<FileInfo> batchInsertList = new ArrayList<>();
        Map<String, String> idMapping = new java.util.HashMap<>();
        Date curDate = new Date();

        // 9. Process Roots
        for (FileInfo root : shareRootFileList) {
            String newFileId = StringTools.getRandomString(Constants.LENGTH_10);
            idMapping.put(root.getFileId(), newFileId);

            FileInfo newRoot = copyFileInfo(root, newFileId, myFolderId, currentUserId, curDate);
            // 目标目录同名时自动重命名
            FileInfo existing = currentFileMap.get(newRoot.getFileName());
            if (existing != null) {
                newRoot.setFileName(StringTools.rename(newRoot.getFileName()));
            }

            batchInsertList.add(newRoot);
        }

        // 10. Process Descendants (Iterative BFS)
        java.util.Queue<String> folderQueue = new java.util.LinkedList<>(rootFolderIds);

        while (!folderQueue.isEmpty()) {
            String sourceParentId = folderQueue.poll();
            String newParentId = idMapping.get(sourceParentId);
            if (newParentId == null) {
                logger.error("Parent ID mapping not found for sourceId: {}", sourceParentId);
                continue;
            }

            List<FileInfo> children = childrenMap.get(sourceParentId);
            if (children != null) {
                for (FileInfo child : children) {
                    String newFileId = StringTools.getRandomString(Constants.LENGTH_10);
                    idMapping.put(child.getFileId(), newFileId);

                    FileInfo newChild = copyFileInfo(child, newFileId, newParentId, currentUserId, curDate);
                    batchInsertList.add(newChild);

                    if (FileFolderTypeEnums.FOLDER.getType().equals(child.getFolderType())) {
                        folderQueue.add(child.getFileId());
                    }

                    // 批量插入前校验
                    if (batchInsertList.size() >= 1000) {
                        fileInfoMapper.insertBatch(batchInsertList);
                        batchInsertList.clear();
                    }
                }
            }
        }

        // 11. Final Batch Insert
        if (!batchInsertList.isEmpty()) {
            fileInfoMapper.insertBatch(batchInsertList);
        }

        // 12. Update User Space (already pre-checked, safe to update)
        if (totalSize > 0) {
            SessionWebUserDto currentUser = new SessionWebUserDto();
            currentUser.setUserId(currentUserId);
            updateUserSpace(currentUser, totalSize);
        }
    }

    private FileInfo copyFileInfo(FileInfo source, String newId, String newPid, String userId, Date date) {
        FileInfo info = new FileInfo();
        info.setFileId(newId);
        info.setUserId(userId);
        info.setTenantId(TenantContextHolder.getTenantId());
        info.setFileMd5(source.getFileMd5());
        info.setFilePid(newPid);
        info.setFileSize(source.getFileSize());
        info.setFileName(source.getFileName());
        info.setFileCover(source.getFileCover());
        info.setFilePath(source.getFilePath());
        info.setCreateTime(date);
        info.setLastUpdateTime(date);
        info.setFolderType(source.getFolderType());
        info.setFileCategory(source.getFileCategory());
        info.setFileType(source.getFileType());
        info.setStatus(FileStatusEnums.USING.getStatus());
        info.setRecoveryTime(null);
        info.setDelFlag(FileDelFlagEnums.USING.getFlag());
        return info;
    }

    @Override
    public Long getUserUseSpace(String userId) {
        return redisComponent.getUserSpaceUse(userId).getUseSpace();
    }

    @Override
    public void deleteFileByUserId(String userId) {
        this.fileInfoMapper.deleteFileByUserId(userId);
    }

    @Override
    public void checkRootFilePid(String rootFilePid, String userId, String fileId) {
        if (StringTools.isEmpty(fileId) || Constants.ZERO_STR.equals(fileId)) {
            return;
        }
        FileInfo fileInfo = this.fileInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(FILE_INFO.FILE_ID.eq(fileId)).and(FILE_INFO.USER_ID.eq(userId)));
        if (fileInfo == null) {
            throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "文件不存在或无权访问");
        }
        if (!isSubFolder(rootFilePid, fileId, userId)) {
            throw new BusinessException(ResponseCodeEnum.CODE_600.getCode(), "文件不在当前目录下，无法操作");
        }
    }

    private boolean isSubFolder(String rootFilePid, String fileId, String userId) {
        if (rootFilePid.equals(fileId)) {
            return true;
        }
        FileInfo current = this.fileInfoMapper.selectOneByQuery(
                QueryWrapper.create().where(FILE_INFO.FILE_ID.eq(fileId)).and(FILE_INFO.USER_ID.eq(userId)));
        if (current == null || Constants.ZERO_STR.equals(current.getFilePid())) {
            return false;
        }
        if (rootFilePid.equals(current.getFilePid())) {
            return true;
        }
        return isSubFolder(rootFilePid, current.getFilePid(), userId);
    }

    /**
     * 定时清理孤儿分片任务。
     * 每天凌晨 2 点执行，清理超过 24 小时未完成更新的分片目录。
     */
    @org.springframework.scheduling.annotation.Scheduled(cron = "0 0 2 * * ?")
    public void cleanOrphanedChunks() {
        logger.info("开始执行孤儿分片清理任务");
        String tempFolderName = appConfig.getProjectFolder() + Constants.FILE_FOLDER_TEMP;
        File tempDir = new File(tempFolderName);
        if (!tempDir.exists() || !tempDir.isDirectory()) {
            return;
        }

        long now = System.currentTimeMillis();
        long expireTime = 24L * 60 * 60 * 1000; // 24小时

        File[] userChunkDirs = tempDir.listFiles();
        if (userChunkDirs != null) {
            for (File userChunkDir : userChunkDirs) {
                if (userChunkDir.isDirectory()) {
                    // 目录最后修改时间超过 24 小时，视为异常中断遗留目录。
                    if (now - userChunkDir.lastModified() > expireTime) {
                        try {
                            // 目录名称格式为 {userId}{fileId}，过期后可直接删除。
                            FileUtils.deleteDirectory(userChunkDir);
                            logger.info("已删除过期分片目录: {}", userChunkDir.getAbsolutePath());
                        } catch (IOException e) {
                            logger.warn("删除过期分片目录失败: {}", userChunkDir.getAbsolutePath(), e);
                        }
                    }
                }
            }
        }
        logger.info("孤儿分片清理任务执行完毕");
    }
}
