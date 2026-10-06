package com.easypan.unit.config;

import com.easypan.config.EncryptionConfig;
import com.easypan.utils.JasyptEncryptionUtil;
import org.jasypt.encryption.StringEncryptor;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EncryptionConfigTest {

    private final EncryptionConfig config = new EncryptionConfig();

    @Test
    void shouldRequireConfiguredEncryptionPassword() {
        assertThatThrownBy(() -> config.stringEncryptor(new MockEnvironment()))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("JASYPT_ENCRYPTOR_PASSWORD");
    }

    @Test
    void shouldUsePasswordFromSpringEnvironment() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jasypt.encryptor.password", "test-only-jasypt-password");

        StringEncryptor encryptor = config.stringEncryptor(environment);
        String encrypted = encryptor.encrypt("sensitive-value");

        assertThat(encrypted).isNotEqualTo("sensitive-value");
        assertThat(encryptor.decrypt(encrypted)).isEqualTo("sensitive-value");
    }

    @Test
    void shouldRemainCompatibleWithCommandLineEncryptionUtility() {
        String password = "test-only-jasypt-password";
        MockEnvironment environment = new MockEnvironment()
                .withProperty("jasypt.encryptor.password", password);

        StringEncryptor encryptor = config.stringEncryptor(environment);
        String encrypted = JasyptEncryptionUtil.encrypt(password, "sensitive-value");

        assertThat(encryptor.decrypt(encrypted)).isEqualTo("sensitive-value");
    }
}
