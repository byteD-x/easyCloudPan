package com.easypan.unit.service;

import com.easypan.service.impl.FileInfoServiceImpl;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DisplayName("FileInfoService 分片合并测试")
class FileInfoServiceChunkMergeTest {

    @TempDir
    Path tempDir;

    @Test
    @DisplayName("unionWithNIO 应按数字顺序合并分片")
    void unionWithNio_shouldMergeChunksByNumericOrder() throws Exception {
        Path chunkDir = tempDir.resolve("chunks");
        Files.createDirectories(chunkDir);

        for (int i = 0; i < 12; i++) {
            Files.writeString(chunkDir.resolve(String.valueOf(i)), i + ",", StandardCharsets.UTF_8);
        }

        Path merged = tempDir.resolve("merged.txt");

        Method method = FileInfoServiceImpl.class.getDeclaredMethod(
                "unionWithNIO", String.class, String.class, String.class, boolean.class);
        method.setAccessible(true);
        method.invoke(null, chunkDir.toString(), merged.toString(), "merged.txt", false);

        String expected = IntStream.range(0, 12)
                .mapToObj(i -> i + ",")
                .collect(Collectors.joining());
        String actual = Files.readString(merged, StandardCharsets.UTF_8);

        assertEquals(expected, actual);
    }
}
