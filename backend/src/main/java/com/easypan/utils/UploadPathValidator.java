package com.easypan.utils;

import com.easypan.exception.BusinessException;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Pattern;

/**
 * 上传会话标识和临时目录路径校验工具。
 *
 * <p>上传 fileId 来自客户端，不能直接参与文件系统路径拼接。</p>
 */
public final class UploadPathValidator {

    private static final Pattern SAFE_UPLOAD_ID = Pattern.compile("[A-Za-z0-9_-]{1,64}");

    private UploadPathValidator() {
    }

    /**
     * 校验上传会话 ID，只允许服务端生成格式。
     *
     * @param fileId 上传会话 ID
     */
    public static void validateFileId(String fileId) {
        if (StringTools.isEmpty(fileId) || !SAFE_UPLOAD_ID.matcher(fileId).matches()) {
            throw new BusinessException("非法的上传会话标识");
        }
    }

    /**
     * 在临时目录根下解析上传目录，并拒绝路径越界。
     *
     * @param projectFolder 项目根目录
     * @param userId        当前登录用户 ID
     * @param fileId        上传会话 ID
     * @return 规范化后的临时目录
     */
    public static Path resolveTempFolder(String projectFolder, String userId, String fileId) {
        validateFileId(fileId);
        if (StringTools.isEmpty(userId) || userId.contains("/") || userId.contains("\\")
                || userId.contains("..")) {
            throw new BusinessException("非法的用户目录标识");
        }

        Path tempRoot = Paths.get(projectFolder + "/temp").toAbsolutePath().normalize();
        Path candidate = tempRoot.resolve(userId + fileId).normalize();
        if (!candidate.startsWith(tempRoot)) {
            throw new BusinessException("上传路径越界");
        }
        return candidate;
    }
}
