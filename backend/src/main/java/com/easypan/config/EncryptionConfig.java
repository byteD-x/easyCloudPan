package com.easypan.config;

import org.jasypt.encryption.StringEncryptor;
import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.encryption.pbe.config.SimpleStringPBEConfig;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 加密配置类.
 */
@Configuration
public class EncryptionConfig {

    /**
     * 创建字符串加密器.
     *
     * @return 字符串加密器实例
     */
    @Bean("jasyptStringEncryptor")
    public StringEncryptor stringEncryptor(Environment environment) {
        String password = environment.getProperty("jasypt.encryptor.password");
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(
                    "Missing required configuration: jasypt.encryptor.password (JASYPT_ENCRYPTOR_PASSWORD)");
        }

        SimpleStringPBEConfig config = new SimpleStringPBEConfig();
        config.setPassword(password);
        config.setAlgorithm(environment.getProperty("jasypt.encryptor.algorithm",
                "PBEWITHHMACSHA512ANDAES_256"));
        config.setKeyObtentionIterations(environment.getProperty("jasypt.encryptor.key-obtention-iterations", "1000"));
        config.setPoolSize(environment.getProperty("jasypt.encryptor.pool-size", "1"));
        config.setProviderName(environment.getProperty("jasypt.encryptor.provider-name", "SunJCE"));
        config.setSaltGeneratorClassName(environment.getProperty("jasypt.encryptor.salt-generator-classname",
                "org.jasypt.salt.RandomSaltGenerator"));
        config.setIvGeneratorClassName(environment.getProperty("jasypt.encryptor.iv-generator-classname",
                "org.jasypt.iv.RandomIvGenerator"));
        config.setStringOutputType(environment.getProperty("jasypt.encryptor.string-output-type", "base64"));
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setConfig(config);
        return encryptor;
    }
}
