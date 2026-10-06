package com.easypan.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.http.apache.ApacheHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

import java.net.URI;
import java.time.Duration;

/**
 * S3 客户端配置类.
 * 配置 MinIO (S3 兼容) 客户端，支持虚拟线程优化.
 */
@Configuration
@Slf4j
public class S3Config {

    @Value("${minio.endpoint}")
    private String endpoint;

    @Value("${minio.accessKey}")
    private String accessKey;

    @Value("${minio.secretKey}")
    private String secretKey;

    @Value("${virtual-threads.enabled:true}")
    private boolean virtualThreadsEnabled;

    @Value("${minio.max-connections:1000}")
    private int maxConnections;

    @Value("${minio.connection-timeout:10000}")
    private int connectionTimeout;

    @Value("${minio.socket-timeout:30000}")
    private int socketTimeout;

    /**
         * 创建 S3 客户端 Bean，配置为虚拟线程兼容模式.
         *
         * @return 配置好的 S3Client 实例
         */
    @Bean
    public S3Client s3Client() {
        log.info("配置 S3 客户端以支持虚拟线程: {}", virtualThreadsEnabled);
        log.info("S3 连接配置 - 最大连接数: {}, 连接超时: {}ms, 套接字超时: {}ms",
                maxConnections, connectionTimeout, socketTimeout);

        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                .serviceConfiguration(S3Configuration.builder()
                        .pathStyleAccessEnabled(true)
                        .chunkedEncodingEnabled(false)
                        .build())
                .httpClientBuilder(ApacheHttpClient.builder()
                        .maxConnections(maxConnections)
                        .connectionTimeout(Duration.ofMillis(connectionTimeout))
                        .socketTimeout(Duration.ofMillis(socketTimeout)))
                .build();
    }

    /**
         * 创建 S3 预签名客户端 Bean，用于生成带时效的直连下载链接.
         *
         * @return 配置好的 S3Presigner 实例
         */
    @Bean
    public software.amazon.awssdk.services.s3.presigner.S3Presigner s3Presigner() {
        log.info("配置 S3 预签名客户端");
        return software.amazon.awssdk.services.s3.presigner.S3Presigner.builder()
                .endpointOverride(URI.create(endpoint))
                .credentialsProvider(StaticCredentialsProvider.create(
                        AwsBasicCredentials.create(accessKey, secretKey)))
                .region(Region.US_EAST_1)
                .build();
    }
}
