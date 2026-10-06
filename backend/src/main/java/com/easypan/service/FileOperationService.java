package com.easypan.service;

import com.easypan.entity.enums.FileDelFlagEnums;
import com.easypan.entity.enums.FileFolderTypeEnums;
import com.easypan.entity.po.FileInfo;
import com.easypan.exception.BusinessException;
import com.easypan.mappers.FileInfoMapper;
import com.easypan.strategy.StorageStrategy;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.apache.commons.io.IOUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static com.easypan.entity.po.table.FileInfoTableDef.FILE_INFO;

/**
 * 閺傚洣娆㈤幙宥勭稊閺堝秴濮熼敍灞煎▏閻劏娅勯幏鐔哄殠缁嬪绱崠?
 */
@Service
public class FileOperationService {

    private static final Logger logger = LoggerFactory.getLogger(FileOperationService.class);

    @Resource
    @Qualifier("virtualThreadExecutor")
    private AsyncTaskExecutor virtualThreadExecutor;

    @Resource
    private FileInfoService fileInfoService;

    @Resource
    private FileInfoMapper fileInfoMapper;

    @Resource
    @Qualifier("storageFailoverService")
    private StorageStrategy storageStrategy;

    /**
     * 閹靛綊鍣洪崚鐘绘珟閺傚洣娆㈤敍鍫⑿╅崗銉ユ礀閺€鍓佺彲閿?
     *
     * @param fileIds 閺傚洣娆D閸掓銆?     * @param userId  閻劍鍩汭D
     */
    public void batchDeleteFile(List<String> fileIds, String userId) {
        CompletableFuture.runAsync(() -> {
            try {
                String fileIdsStr = String.join(",", fileIds);
                logger.info("Virtual Thread: Batch moving files to recycle bin: {}", fileIdsStr);
                fileInfoService.removeFile2RecycleBatch(userId, fileIdsStr);
            } catch (Exception e) {
                logger.error("Failed to batch delete files", e);
            }
        }, virtualThreadExecutor);
    }

    /**
     * 閹靛綊鍣烘稉瀣祰閺傚洣娆㈤獮鑸靛ⅵ閸?
     *
     * @param userId       閻劍鍩汭D
     * @param fileIds      閺傚洣娆D閸掓銆?     * @param outputStream 鏉堟挸鍤ù?
     * @throws IOException IO瀵倸鐖?     */
    public void downloadMultipleFiles(String userId, List<String> fileIds, OutputStream outputStream) throws IOException {
        if (fileIds == null || fileIds.isEmpty()) {
            throw new BusinessException("File list cannot be empty");
        }

        LinkedHashSet<String> distinctFileIds = new LinkedHashSet<>();
        for (String fileId : fileIds) {
            if (fileId != null && !fileId.isBlank()) {
                distinctFileIds.add(fileId.trim());
            }
        }
        if (distinctFileIds.isEmpty()) {
            throw new BusinessException("File list cannot be empty");
        }

        List<FileInfo> authorizedFiles = fileInfoMapper.selectListByQuery(
                QueryWrapper.create()
                        .where(FILE_INFO.USER_ID.eq(userId))
                        .and(FILE_INFO.FILE_ID.in((Object[]) distinctFileIds.toArray(String[]::new)))
                        .and(FILE_INFO.FOLDER_TYPE.eq(FileFolderTypeEnums.FILE.getType()))
                        .and(FILE_INFO.DEL_FLAG.eq(FileDelFlagEnums.USING.getFlag())));

        if (authorizedFiles.size() != distinctFileIds.size()) {
            throw new BusinessException("闁劌鍨庨弬鍥︽娑撳秴鐡ㄩ崷銊﹀灗閺冪姵娼堢拋鍧楁６");
        }

        Map<String, FileInfo> fileInfoMap = new HashMap<>(authorizedFiles.size());
        for (FileInfo fileInfo : authorizedFiles) {
            fileInfoMap.put(fileInfo.getFileId(), fileInfo);
        }

        try (ZipOutputStream zipOut = new ZipOutputStream(outputStream)) {
            Set<String> usedEntryNames = new HashSet<>();
            for (String fileId : distinctFileIds) {
                FileInfo fileInfo = fileInfoMap.get(fileId);
                if (fileInfo == null) {
                    continue;
                }
                String entryName = buildUniqueEntryName(fileInfo.getFileName(), usedEntryNames);
                try (InputStream inputStream = storageStrategy.download(fileInfo.getFilePath())) {
                    zipOut.putNextEntry(new ZipEntry(entryName));
                    IOUtils.copy(inputStream, zipOut);
                    zipOut.closeEntry();
                }
            }
        }
    }

    private String buildUniqueEntryName(String fileName, Set<String> usedEntryNames) {
        String normalizedName = (fileName == null || fileName.isBlank()) ? "unknown" : fileName.trim();
        if (usedEntryNames.add(normalizedName)) {
            return normalizedName;
        }

        int dotIndex = normalizedName.lastIndexOf('.');
        String base = dotIndex > 0 ? normalizedName.substring(0, dotIndex) : normalizedName;
        String ext = dotIndex > 0 ? normalizedName.substring(dotIndex) : "";

        int suffix = 1;
        while (true) {
            String candidate = base + "(" + suffix + ")" + ext;
            if (usedEntryNames.add(candidate)) {
                return candidate;
            }
            suffix++;
        }
    }
}
