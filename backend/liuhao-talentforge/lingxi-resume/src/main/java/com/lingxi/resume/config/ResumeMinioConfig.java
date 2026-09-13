package com.lingxi.resume.config;

import io.minio.MinioClient;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 简历模块自有 MinIO 客户端
 *
 * <p>main 分支的 common MinioUtil 仅提供 upload/delete/getFileUrl（无 getInputStream/presigned URL），
 * 简历模块需要 SDK 直连读取原件与生成签名 URL，故在本模块内自建 MinioClient。
 * 配置复用 common 的 storage.minio.*（application-dev.yml）。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Configuration
public class ResumeMinioConfig {

    @Bean("resumeMinioClient")
    public MinioClient resumeMinioClient(
            @Value("${storage.minio.endpoint}") String endpoint,
            @Value("${storage.minio.access-key}") String accessKey,
            @Value("${storage.minio.secret-key}") String secretKey) {
        return MinioClient.builder()
                .endpoint(endpoint)
                .credentials(accessKey, secretKey)
                .build();
    }
}
