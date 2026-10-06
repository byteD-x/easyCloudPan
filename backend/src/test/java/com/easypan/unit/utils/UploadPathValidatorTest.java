package com.easypan.unit.utils;

import com.easypan.exception.BusinessException;
import com.easypan.utils.UploadPathValidator;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class UploadPathValidatorTest {

    @Test
    void shouldResolveSafeUploadFolder() {
        Path path = UploadPathValidator.resolveTempFolder("target/test-files/", "user-1", "abc_123");
        assertEquals("abc_123", path.getFileName().toString().substring("user-1".length()));
    }

    @Test
    void shouldRejectPathTraversalUploadId() {
        assertThrows(BusinessException.class,
                () -> UploadPathValidator.resolveTempFolder("target/test-files/", "user-1", "../escape"));
    }

    @Test
    void shouldRejectPathTraversalUserId() {
        assertThrows(BusinessException.class,
                () -> UploadPathValidator.resolveTempFolder("target/test-files/", "../user", "abc123"));
    }
}
