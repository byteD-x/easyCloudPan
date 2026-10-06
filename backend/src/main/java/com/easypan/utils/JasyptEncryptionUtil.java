package com.easypan.utils;

import org.jasypt.encryption.pbe.PooledPBEStringEncryptor;
import org.jasypt.iv.RandomIvGenerator;

/**
 * Jasypt 加密工具类.
 *
 * <p>用于生成配置文件中敏感信息的加密值
 *
 * <p>使用方式：java -cp target/classes com.easypan.utils.JasyptEncryptionUtil &lt;secretKey&gt; &lt;valueToEncrypt&gt;
 */
@SuppressWarnings("checkstyle:NoConsoleOutput")
public class JasyptEncryptionUtil {

    private JasyptEncryptionUtil() {
    }

    /**
     * 主方法，用于命令行加密.
     *
     * @param args 命令行参数
     */
    public static void main(String[] args) {
        if (args.length < 2) {
            System.out.println("Usage: java JasyptEncryptionUtil <secretKey> <valueToEncrypt>");
            System.out.println("Example: java JasyptEncryptionUtil mySecretKey myPassword123");
            return;
        }

        String secretKey = args[0];
        String valueToEncrypt = args[1];

        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPoolSize(1);
        encryptor.setPassword(secretKey);
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());

        String encryptedValue = encryptor.encrypt(valueToEncrypt);
        System.out.println("Encrypted value: " + encryptedValue);
        System.out.println("Use in properties file: ENC(" + encryptedValue + ")");
    }

    /**
     * 加密字符串.
     *
     * @param secretKey 密钥
     * @param value 待加密的字符串
     * @return 加密后的字符串
     */
    public static String encrypt(String secretKey, String value) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPoolSize(1);
        encryptor.setPassword(secretKey);
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        return encryptor.encrypt(value);
    }

    /**
     * 解密字符串.
     *
     * @param secretKey 密钥
     * @param encryptedValue 加密的字符串
     * @return 解密后的字符串
     */
    public static String decrypt(String secretKey, String encryptedValue) {
        PooledPBEStringEncryptor encryptor = new PooledPBEStringEncryptor();
        encryptor.setPoolSize(1);
        encryptor.setPassword(secretKey);
        encryptor.setAlgorithm("PBEWITHHMACSHA512ANDAES_256");
        encryptor.setIvGenerator(new RandomIvGenerator());
        return encryptor.decrypt(encryptedValue);
    }
}
