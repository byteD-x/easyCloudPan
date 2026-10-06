package com.easypan;

import com.easypan.entity.enums.VerifyRegexEnum;
import com.easypan.utils.VerifyUtils;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class PasswordValidationTest {

    @Test
    public void testPasswordRegex() {
        String regex = VerifyRegexEnum.PASSWORD.getRegex();

        // Valid passwords
        Assertions.assertTrue(VerifyUtils.verify(regex, "12345678a!"), "Should pass: Number + Letter + Special");
        Assertions.assertTrue(VerifyUtils.verify(regex, "Password123#"), "Should pass: Mixed case + Number + Special");
        Assertions.assertTrue(VerifyUtils.verify(regex, "Test@1234"), "Should pass: Valid complex password");

        // Invalid passwords
        Assertions.assertFalse(VerifyUtils.verify(regex, "12345678"), "Should fail: Only numbers");
        Assertions.assertFalse(VerifyUtils.verify(regex, "abcdefgh"), "Should fail: Only letters");
        Assertions.assertFalse(VerifyUtils.verify(regex, "1234abcd"), "Should fail: No special char");
        Assertions.assertFalse(VerifyUtils.verify(regex, "1234!@#$"), "Should fail: No letters");
        Assertions.assertFalse(VerifyUtils.verify(regex, "abcd!@#$"), "Should fail: No numbers");
        Assertions.assertFalse(VerifyUtils.verify(regex, "Ab1!"), "Should fail: Too short");
    }
}
