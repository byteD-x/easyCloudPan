package com.easypan.strategy.impl;

import com.easypan.entity.config.AppConfig;
import com.easypan.exception.BusinessException;
import com.easypan.strategy.StorageStrategy;
import jakarta.annotation.Resource;
import org.apache.commons.io.FileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 本地存储策略实现类.
 */
@Service
public class LocalStorageStrategy implements StorageStrategy {

    private static final Logger logger = LoggerFactory.getLogger(LocalStorageStrategy.class);

    @Resource
    private AppConfig appConfig;

    @Override
    public void upload(MultipartFile file, String path) {
        try {
            String fullPath = getFullPath(path);
            File folder = new File(new File(fullPath).getParent());
            if (!folder.exists() && !folder.mkdirs()) {
                logger.error("Failed to create directory: {}", folder.getAbsolutePath());
                throw new BusinessException("创建目录失败");
            }
            file.transferTo(new File(fullPath));
        } catch (IOException e) {
            logger.error("Upload file to local failed", e);
            throw new BusinessException("文件上传失败，请重试");
        }
    }

    @Override
    public void upload(File file, String path) {
        try {
            String fullPath = getFullPath(path);
            File targetFile = new File(fullPath);
            File folder = targetFile.getParentFile();
            if (folder != null && !folder.exists() && !folder.mkdirs()) {
                logger.error("Failed to create directory: {}", folder.getAbsolutePath());
                throw new BusinessException("创建目录失败");
            }
            if (file.getAbsolutePath().equals(targetFile.getAbsolutePath())) {
                return;
            }
            FileUtils.copyFile(file, targetFile);
        } catch (IOException e) {
            logger.error("Upload file to local failed", e);
            throw new BusinessException("文件上传失败，请重试");
        }
    }

    @Override
    public void uploadDirectory(String prefix, File directory) {
        try {
            String fullPath = getFullPath(prefix);
            File targetDir = new File(fullPath);
            if (directory.getAbsolutePath().equals(targetDir.getAbsolutePath())) {
                return;
            }
            FileUtils.copyDirectory(directory, targetDir);
        } catch (IOException e) {
            logger.error("Upload directory to local failed", e);
            throw new BusinessException("目录上传失败，请重试");
        }
    }

    @Override
    public InputStream download(String path) {
        try {
            return new FileInputStream(getFullPath(path));
        } catch (IOException e) {
            logger.error("Download file from local failed", e);
            throw new BusinessException("文件下载失败，请重试");
        }
    }

    @Override
    public void delete(String path) {
        File file = new File(getFullPath(path));
        if (file.exists() && !file.delete()) {
            logger.warn("Failed to delete file: {}", file.getAbsolutePath());
        }
    }

    @Override
    public void deleteDirectory(String path) {
        File file = new File(getFullPath(path));
        if (file.exists()) {
            try {
                FileUtils.deleteDirectory(file);
            } catch (IOException e) {
                logger.error("Delete directory failed: {}", path, e);
                throw new BusinessException("目录删除失败，请重试");
            }
        }
    }

    @Override
    public String getUrl(String path) {
        // 返回本地/相对路径，实际文件输出由 Controller 统一处理
        return path;
    }

    @Override
    public void init() {
        String root = appConfig.getFileRootPath();
        File rootFile = new File(root);
        if (!rootFile.exists() && !rootFile.mkdirs()) {
            logger.error("Failed to create root directory: {}", root);
        }
    }

    private String getFullPath(String path) {
        if (path == null) {
            return appConfig.getFileRootPath();
        }
        String normalizedPath = path.replace("\\", "/");
        while (normalizedPath.startsWith("/")) {
            normalizedPath = normalizedPath.substring(1);
        }
        Path root = Paths.get(appConfig.getFileRootPath()).toAbsolutePath().normalize();
        Path candidate = root.resolve(normalizedPath).normalize();
        if (!candidate.startsWith(root)) {
            throw new BusinessException("非法的存储路径");
        }
        return candidate.toString();
    }
}
