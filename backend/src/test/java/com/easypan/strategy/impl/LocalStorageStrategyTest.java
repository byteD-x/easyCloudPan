package com.easypan.strategy.impl;

import com.easypan.entity.config.AppConfig;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class LocalStorageStrategyTest {

    @Test
    void downloadShouldResolvePathWithoutDuplicatedFileSegment() throws Exception {
        Path tempRoot = Files.createTempDirectory("easy-pan-storage");
        Path projectFolder = tempRoot.resolve("file");
        Path dataFile = projectFolder.resolve("202602").resolve("sample.txt");
        Files.createDirectories(dataFile.getParent());
        Files.writeString(dataFile, "ok", StandardCharsets.UTF_8);

        AppConfig appConfig = new AppConfig();
        appConfig.setProjectFolder(projectFolder.toString().replace("\\", "/") + "/");

        LocalStorageStrategy strategy = new LocalStorageStrategy();
        ReflectionTestUtils.setField(strategy, "appConfig", appConfig);

        try (InputStream inputStream = strategy.download("202602/sample.txt")) {
            String value = new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("ok", value);
        }
    }

    @Test
    void downloadShouldRejectPathTraversal() throws Exception {
        Path tempRoot = Files.createTempDirectory("easy-pan-storage");
        AppConfig appConfig = new AppConfig();
        appConfig.setProjectFolder(tempRoot.resolve("file").toString());

        LocalStorageStrategy strategy = new LocalStorageStrategy();
        ReflectionTestUtils.setField(strategy, "appConfig", appConfig);

        assertThrows(com.easypan.exception.BusinessException.class,
                () -> strategy.download("../outside.txt"));
    }
}
