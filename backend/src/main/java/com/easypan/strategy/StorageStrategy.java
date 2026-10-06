package com.easypan.strategy;

import java.io.InputStream;
import org.springframework.web.multipart.MultipartFile;

/**
 * 存储策略接口，定义文件存储的基本操作.
 */
public interface StorageStrategy {
    /**
     * 上传文件.
     *
     * @param file 文件
     * @param path 存储路径
     */
    void upload(MultipartFile file, String path);

    /**
     * 上传文件.
     *
     * @param file 文件
     * @param path 存储路径
     */
    void upload(java.io.File file, String path);

    /**
     * 上传目录.
     *
     * @param prefix    前缀
     * @param directory 目录
     */
    void uploadDirectory(String prefix, java.io.File directory);

    /**
     * 下载文件.
     *
     * @param path 存储路径
     * @return 文件输入流
     */
    InputStream download(String path);

    /**
     * 删除文件.
     *
     * @param key 文件键
     */
    void delete(String key);

    /**
     * 批量删除文件.
     *
     * @param keys 文件键列表
     */
    default void deleteBatch(java.util.List<String> keys) {
        for (String key : keys) {
            delete(key);
        }
    }

    void deleteDirectory(String path);

    String getUrl(String path);

    default String generatePresignedUrl(String path, String fileName) {
        return null;
    }

    void init();
}
