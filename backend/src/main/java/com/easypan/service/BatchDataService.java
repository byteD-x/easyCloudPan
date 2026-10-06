package com.easypan.service;

import com.easypan.entity.po.FileInfo;
import com.easypan.mappers.FileInfoMapper;
import com.mybatisflex.core.query.QueryWrapper;
import jakarta.annotation.Resource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

/**
 * 批量数据服务，使用虚拟线程优化并发查询.
 */
@Service
public class BatchDataService {

    @Resource
    @Qualifier("virtualThreadExecutor")
    private AsyncTaskExecutor virtualThreadExecutor;

    @Resource
    private FileInfoMapper fileInfoMapper;

    /**
     * 并发获取多个文件的详细信息.
     *
     * @param fileIds 文件ID列表
     * @return 文件信息列表
     */
    public List<FileInfo> batchGetFileInfos(List<String> fileIds) {
        List<CompletableFuture<FileInfo>> futures = fileIds.stream()
                .map(fileId -> CompletableFuture.supplyAsync(() -> {
                    // 使用 QueryWrapper 按 file_id 查询
                    return fileInfoMapper.selectOneByQuery(QueryWrapper.create().eq(FileInfo::getFileId, fileId));
                }, virtualThreadExecutor))
                .collect(Collectors.toList());

        return futures.stream()
                .map(CompletableFuture::join) // 等待所有虚拟线程完成
                .collect(Collectors.toList());
    }
}
