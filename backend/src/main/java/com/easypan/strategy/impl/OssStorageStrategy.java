package com.easypan.strategy.impl;

import com.easypan.component.S3Component;
import com.easypan.entity.config.AppConfig;
import com.easypan.exception.BusinessException;
import com.easypan.strategy.StorageStrategy;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

/**
 * OSS 存储策略实现类.
 */
@Service
public class OssStorageStrategy implements StorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(OssStorageStrategy.class);

    @Resource
    private S3Component s3Component;

    @Resource
    private AppConfig appConfig;

    @Override
    public void upload(MultipartFile file, String path) {
        try {
            // S3Component 已封装 PutObjectRequest，这里直接按字节上传 MultipartFile。
            s3Component.uploadBytes(path, file.getBytes());
        } catch (IOException e) {
            logger.error("Upload file to OSS failed", e);
            throw new BusinessException("文件上传失败，请重试");
        }
    }

    @Override
    public void upload(File file, String path) {
        s3Component.uploadFile(path, file);
    }

    @Override
    public void uploadDirectory(String prefix, File directory) {
        s3Component.uploadDirectory(prefix, directory);
    }

    @Override
    public InputStream download(String path) {
        return s3Component.getInputStream(path);
    }

    @Override
    public void delete(String path) {
        s3Component.deleteFile(path);
    }

    @Override
    public void deleteDirectory(String path) {
        s3Component.deleteDirectory(path);
    }

    @Override
    public void deleteBatch(java.util.List<String> keys) {
        s3Component.deleteObjects(keys);
    }

    @Override
    public String getUrl(String path) {
        // 当前与本地存储策略保持一致，返回相对路径交给 Controller 统一处理。
        // 若未来切换前端直连下载，可改为返回预签名 URL 或 CDN URL。
        return path;
    }

    @Override
    public String generatePresignedUrl(String path, String fileName) {
        return s3Component.generatePresignedUrl(path, fileName);
    }

    @Override
    public void init() {
        // S3Component 在 Spring 启动阶段已完成初始化。
    }
}
