package com.easypan.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import jakarta.annotation.Resource;
import java.util.Arrays;
import java.util.List;

/**
 * 批量操作服务.
 *
 * <p>统一处理文件批量操作的业务逻辑，确保跨页面行为一致性
 */
@Service
@Slf4j
public class BatchOperationService {

    @Resource
    private FileInfoService fileInfoService;

    /**
     * 批量操作结果.
     */
    public static class BatchOperationResult {
        private int successCount;
        private int failCount;
        private List<String> failedFileIds;
        private String message;

        /**
         * 构造函数.
         *
         * @param successCount  成功数量
         * @param failCount     失败数量
         * @param failedFileIds 失败的文件ID列表
         * @param message       消息
         */
        public BatchOperationResult(int successCount, int failCount, List<String> failedFileIds, String message) {
            this.successCount = successCount;
            this.failCount = failCount;
            this.failedFileIds = failedFileIds;
            this.message = message;
        }

        public int getSuccessCount() {
            return successCount;
        }

        public int getFailCount() {
            return failCount;
        }

        public List<String> getFailedFileIds() {
            return failedFileIds;
        }

        public String getMessage() {
            return message;
        }

        public boolean isAllSuccess() {
            return failCount == 0;
        }
    }

    /**
     * 批量移动到回收站.
     *
     * @param userId  用户ID
     * @param fileIds 文件ID列表（逗号分隔）
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchMoveToRecycle(String userId, String fileIds) {
        long startTime = System.currentTimeMillis();
        String[] fileIdArray = fileIds.split(",");
        int totalCount = fileIdArray.length;

        try {
            log.info("[BATCH_OP] Start moving {} files to recycle for user: {}", totalCount, userId);

            fileInfoService.removeFile2RecycleBatch(userId, fileIds);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[BATCH_OP] Successfully moved {} files to recycle in {}ms", totalCount, duration);

            return new BatchOperationResult(totalCount, 0, List.of(),
                    String.format("成功移动 %d 个文件到回收站", totalCount));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[BATCH_OP] Failed to move files to recycle after {}ms: {}", duration, e.getMessage(), e);

            return new BatchOperationResult(0, totalCount, Arrays.asList(fileIdArray),
                    "批量移动失败: " + e.getMessage());
        }
    }

    /**
     * 批量恢复文件.
     *
     * @param userId  用户ID
     * @param fileIds 文件ID列表（逗号分隔）
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchRecover(String userId, String fileIds) {
        long startTime = System.currentTimeMillis();
        String[] fileIdArray = fileIds.split(",");
        int totalCount = fileIdArray.length;

        try {
            log.info("[BATCH_OP] Start recovering {} files for user: {}", totalCount, userId);

            fileInfoService.recoverFileBatch(userId, fileIds);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[BATCH_OP] Successfully recovered {} files in {}ms", totalCount, duration);

            return new BatchOperationResult(totalCount, 0, List.of(),
                    String.format("成功恢复 %d 个文件", totalCount));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[BATCH_OP] Failed to recover files after {}ms: {}", duration, e.getMessage(), e);

            return new BatchOperationResult(0, totalCount, Arrays.asList(fileIdArray),
                    "批量恢复失败: " + e.getMessage());
        }
    }

    /**
     * 批量永久删除.
     *
     * @param userId  用户ID
     * @param fileIds 文件ID列表（逗号分隔）
     * @param adminOp 是否为管理员操作
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchDelete(String userId, String fileIds, Boolean adminOp) {
        long startTime = System.currentTimeMillis();
        String[] fileIdArray = fileIds.split(",");
        int totalCount = fileIdArray.length;

        try {
            log.info("[BATCH_OP] Start deleting {} files for user: {} (admin: {})",
                    totalCount, userId, adminOp);

            fileInfoService.delFileBatch(userId, fileIds, adminOp);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[BATCH_OP] Successfully deleted {} files in {}ms", totalCount, duration);

            return new BatchOperationResult(totalCount, 0, List.of(),
                    String.format("成功删除 %d 个文件", totalCount));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[BATCH_OP] Failed to delete files after {}ms: {}", duration, e.getMessage(), e);

            return new BatchOperationResult(0, totalCount, Arrays.asList(fileIdArray),
                    "批量删除失败: " + e.getMessage());
        }
    }

    /**
     * 批量移动文件.
     *
     * @param userId    用户ID
     * @param fileIds   文件ID列表（逗号分隔）
     * @param targetPid 目标文件夹ID
     * @return 操作结果
     */
    @Transactional(rollbackFor = Exception.class)
    public BatchOperationResult batchMove(String userId, String fileIds, String targetPid) {
        long startTime = System.currentTimeMillis();
        String[] fileIdArray = fileIds.split(",");
        int totalCount = fileIdArray.length;

        try {
            log.info("[BATCH_OP] Start moving {} files to folder {} for user: {}",
                    totalCount, targetPid, userId);

            fileInfoService.changeFileFolder(fileIds, targetPid, userId);

            long duration = System.currentTimeMillis() - startTime;
            log.info("[BATCH_OP] Successfully moved {} files in {}ms", totalCount, duration);

            return new BatchOperationResult(totalCount, 0, List.of(),
                    String.format("成功移动 %d 个文件", totalCount));
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - startTime;
            log.error("[BATCH_OP] Failed to move files after {}ms: {}", duration, e.getMessage(), e);

            return new BatchOperationResult(0, totalCount, Arrays.asList(fileIdArray),
                    "批量移动失败: " + e.getMessage());
        }
    }
}

