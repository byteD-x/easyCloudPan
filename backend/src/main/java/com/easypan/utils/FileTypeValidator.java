package com.easypan.utils;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 文件类型校验工具类.
 *
 * <p>通过文件头（Magic Number）验证真实文件类型
 */
public class FileTypeValidator {

    private static final Logger logger = LoggerFactory.getLogger(FileTypeValidator.class);

    // 文件头魔数映射表
    private static final Map<String, List<String>> FILE_TYPE_MAP = new HashMap<>();

    // 危险文件扩展名黑名单
    private static final Set<String> DANGEROUS_EXTENSIONS = new HashSet<>(Arrays.asList(
            "exe", "bat", "cmd", "com", "pif", "scr", "vbs", "js", "jar",
            "sh", "bash", "ps1", "app", "deb", "rpm", "dmg", "pkg"
    ));

    static {
        // 图片类型
        FILE_TYPE_MAP.put("jpg", Arrays.asList("FFD8FF"));
        FILE_TYPE_MAP.put("jpeg", Arrays.asList("FFD8FF"));
        FILE_TYPE_MAP.put("png", Arrays.asList("89504E47"));
        FILE_TYPE_MAP.put("gif", Arrays.asList("47494638"));
        FILE_TYPE_MAP.put("bmp", Arrays.asList("424D"));
        FILE_TYPE_MAP.put("webp", Arrays.asList("52494646"));

        // 文档类型
        FILE_TYPE_MAP.put("pdf", Arrays.asList("25504446"));
        FILE_TYPE_MAP.put("doc", Arrays.asList("D0CF11E0"));
        FILE_TYPE_MAP.put("docx", Arrays.asList("504B0304"));
        FILE_TYPE_MAP.put("xls", Arrays.asList("D0CF11E0"));
        FILE_TYPE_MAP.put("xlsx", Arrays.asList("504B0304"));
        FILE_TYPE_MAP.put("ppt", Arrays.asList("D0CF11E0"));
        FILE_TYPE_MAP.put("pptx", Arrays.asList("504B0304"));

        // 压缩文件
        FILE_TYPE_MAP.put("zip", Arrays.asList("504B0304", "504B0506", "504B0708"));
        FILE_TYPE_MAP.put("rar", Arrays.asList("526172211A07"));
        FILE_TYPE_MAP.put("7z", Arrays.asList("377ABCAF271C"));
        FILE_TYPE_MAP.put("tar", Arrays.asList("7573746172"));
        FILE_TYPE_MAP.put("gz", Arrays.asList("1F8B08"));

        // 视频类型
        FILE_TYPE_MAP.put("mp4", Arrays.asList("00000018", "00000020"));
        FILE_TYPE_MAP.put("avi", Arrays.asList("52494646"));
        FILE_TYPE_MAP.put("mkv", Arrays.asList("1A45DFA3"));
        FILE_TYPE_MAP.put("mov", Arrays.asList("00000014", "00000018", "00000020"));

        // 音频类型
        FILE_TYPE_MAP.put("mp3", Arrays.asList("494433", "FFFB", "FFF3"));
        FILE_TYPE_MAP.put("wav", Arrays.asList("52494646"));
        FILE_TYPE_MAP.put("flac", Arrays.asList("664C6143"));

        // 文本类型
        FILE_TYPE_MAP.put("txt", Arrays.asList("EFBBBF", "FFFE", "FEFF")); // UTF-8 BOM、UTF-16
    }

    /**
     * 验证文件类型是否匹配.
     *
     * @param inputStream 文件输入流
     * @param extension 声称的文件扩展名
     * @return true 如果文件类型匹配
     */
    public static boolean validateFileType(InputStream inputStream, String extension) {
        if (inputStream == null || extension == null) {
            return false;
        }

        String ext = extension.toLowerCase().replace(".", "");

        // 检查是否为危险文件类型
        if (DANGEROUS_EXTENSIONS.contains(ext)) {
            logger.warn("Dangerous file extension detected: {}", ext);
            return false;
        }

        // 纯文本文件可能不包含 BOM，直接放行以避免误判.
        if ("txt".equals(ext)) {
            return true;
        }

        // ISO Base Media File Format（mp4/mov）头部随 box 大小变化.
        // 常见模式为 [size:4][ftyp:4][brand:4]...
        if ("mp4".equals(ext) || "mov".equals(ext)) {
            try {
                String header = getFileHeader(inputStream, 12);
                boolean ok = header.length() >= 16 && "66747970".equalsIgnoreCase(header.substring(8, 16));
                if (!ok) {
                    logger.warn("File type mismatch: extension={}, header={}", ext, header);
                }
                return ok;
            } catch (IOException e) {
                logger.error("Error reading file header", e);
                return false;
            }
        }

        // TAR 魔数 "ustar" 不在偏移 0 位置，直接放行避免误判.
        if ("tar".equals(ext)) {
            return true;
        }

        // 对于未定义魔数的文件类型，允许通过（如 txt 无 BOM）
        if (!FILE_TYPE_MAP.containsKey(ext)) {
            logger.debug("File type {} not in validation map, allowing", ext);
            return true;
        }

        try {
            String fileHeader = getFileHeader(inputStream, 10);
            List<String> validHeaders = FILE_TYPE_MAP.get(ext);

            for (String validHeader : validHeaders) {
                if (fileHeader.startsWith(validHeader)) {
                    return true;
                }
            }

            logger.warn("File type mismatch: extension={}, header={}", ext, fileHeader);
            return false;

        } catch (IOException e) {
            logger.error("Error reading file header", e);
            return false;
        }
    }

    /**
     * 读取文件头.
     *
     * @param inputStream 文件输入流
     * @param length 读取长度（字节）
     * @return 文件头十六进制字符串
     * @throws IOException IO 异常
     */
    private static String getFileHeader(InputStream inputStream, int length) throws IOException {
        // 部分 InputStream（如 servlet multipart 流）不支持 mark/reset.
        // 这里包一层 BufferedInputStream，保证头部探测可回退且不破坏后续读取.
        InputStream in = inputStream;
        if (!in.markSupported()) {
            in = new java.io.BufferedInputStream(in);
        }

        byte[] bytes = new byte[length];
        in.mark(length);
        int readLength = in.read(bytes, 0, length);
        in.reset();

        if (readLength == -1) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < readLength; i++) {
            // Java 的 byte 为有符号值，按位与 0xFF 可避免符号扩展（例如 0x89 -> FFFFFF89）
            sb.append(String.format("%02X", bytes[i] & 0xFF));
        }
        return sb.toString();
    }

    /**
     * 检查文件扩展名是否在黑名单中.
     *
     * @param extension 文件扩展名
     * @return true 如果是危险文件类型
     */
    public static boolean isDangerousFileType(String extension) {
        if (extension == null) {
            return false;
        }
        String ext = extension.toLowerCase().replace(".", "");
        return DANGEROUS_EXTENSIONS.contains(ext);
    }

    /**
     * 获取支持的文件类型列表.
     *
     * @return 支持的文件扩展名集合
     */
    public static Set<String> getSupportedFileTypes() {
        return FILE_TYPE_MAP.keySet();
    }
}
