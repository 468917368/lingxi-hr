package com.lingxi.common.util;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import com.aliyun.oss.model.PutObjectRequest;
import io.minio.*;
import io.minio.http.Method;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * 文件存储工具类
 * <p>
 * 支持三种存储方式：
 * 1. MinIO - 开发环境
 * 2. 阿里云OSS - 生产环境
 * 3. 本地存储 - 备用方案
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
@Component
@RefreshScope
public class MinioUtil {

    /** 存储类型：minio / aliyun-oss */
    @Value("${storage.type:minio}")
    private String storageType;

    // ==================== MinIO 配置 ====================
    @Value("${storage.minio.endpoint:}")
    private String minioEndpoint;

    @Value("${storage.minio.access-key:}")
    private String minioAccessKey;

    @Value("${storage.minio.secret-key:}")
    private String minioSecretKey;

    @Value("${storage.minio.bucket-name:hrms}")
    private String minioBucketName;

    // ==================== 阿里云OSS 配置 ====================
    @Value("${storage.aliyun-oss.endpoint:}")
    private String ossEndpoint;

    @Value("${storage.aliyun-oss.access-key-id:}")
    private String ossAccessKeyId;

    @Value("${storage.aliyun-oss.access-key-secret:}")
    private String ossAccessKeySecret;

    @Value("${storage.aliyun-oss.bucket-name:}")
    private String ossBucketName;

    /** 本地存储路径 */
    private static final String LOCAL_STORAGE_PATH = "./uploads/";

    /** 最大文件大小：10MB */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024L;

    private MinioClient minioClient;
    private OSS ossClient;

    @PostConstruct
    public void init() {
        if ("minio".equals(storageType) && isNotEmpty(minioEndpoint)) {
            // 初始化MinIO客户端
            this.minioClient = MinioClient.builder()
                    .endpoint(minioEndpoint)
                    .credentials(minioAccessKey, minioSecretKey)
                    .build();
            log.info("存储类型: MinIO, endpoint={}, bucket={}", minioEndpoint, minioBucketName);
        } else if ("aliyun-oss".equals(storageType) && isNotEmpty(ossEndpoint)) {
            // 初始化阿里云OSS客户端
            this.ossClient = new OSSClientBuilder().build(ossEndpoint, ossAccessKeyId, ossAccessKeySecret);
            log.info("存储类型: 阿里云OSS, endpoint={}, bucket={}", ossEndpoint, ossBucketName);
        } else {
            log.info("存储类型: 本地存储, path={}", LOCAL_STORAGE_PATH);
            File dir = new File(LOCAL_STORAGE_PATH);
            if (!dir.exists()) {
                dir.mkdirs();
            }
        }
    }

    /**
     * 上传文件
     *
     * @param objectName  对象名称（如 avatars/2/uuid.jpg）
     * @param inputStream 文件流
     * @param contentType 文件类型
     * @return 文件访问URL
     */
    public String upload(String objectName, InputStream inputStream, String contentType) {
        switch (storageType) {
            case "minio":
                return uploadToMinio(objectName, inputStream, contentType);
            case "aliyun-oss":
                return uploadToOss(objectName, inputStream, contentType);
            default:
                return uploadToLocal(objectName, inputStream);
        }
    }

    /**
     * 删除文件
     */
    public void delete(String objectName) {
        switch (storageType) {
            case "minio":
                deleteFromMinio(objectName);
                break;
            case "aliyun-oss":
                deleteFromOss(objectName);
                break;
            default:
                deleteFromLocal(objectName);
                break;
        }
    }

    /**
     * 获取文件访问URL
     */
    public String getFileUrl(String objectName) {
        switch (storageType) {
            case "minio":
                return minioEndpoint + "/" + minioBucketName + "/" + objectName;
            case "aliyun-oss":
                return "https://" + ossBucketName + "." + ossEndpoint + "/" + objectName;
            default:
                return "/files/" + objectName;
        }
    }

    // ==================== MinIO 实现 ====================

    private String uploadToMinio(String objectName, InputStream inputStream, String contentType) {
        try {
            ensureMinioBucket();
            PutObjectArgs args = PutObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(objectName)
                    .stream(inputStream, -1, MAX_FILE_SIZE)
                    .contentType(contentType)
                    .build();
            minioClient.putObject(args);
            String url = getFileUrl(objectName);
            log.info("MinIO上传成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("MinIO上传失败: object={}", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private void deleteFromMinio(String objectName) {
        try {
            RemoveObjectArgs args = RemoveObjectArgs.builder()
                    .bucket(minioBucketName)
                    .object(objectName)
                    .build();
            minioClient.removeObject(args);
            log.info("MinIO删除成功: object={}", objectName);
        } catch (Exception e) {
            log.error("MinIO删除失败: object={}", objectName, e);
        }
    }

    private void ensureMinioBucket() throws Exception {
        boolean exists = minioClient.bucketExists(
                BucketExistsArgs.builder().bucket(minioBucketName).build());
        if (!exists) {
            minioClient.makeBucket(
                    MakeBucketArgs.builder().bucket(minioBucketName).build());
            log.info("创建MinIO桶: {}", minioBucketName);
        }
    }

    // ==================== 阿里云OSS 实现 ====================

    private String uploadToOss(String objectName, InputStream inputStream, String contentType) {
        try {
            PutObjectRequest putObjectRequest = new PutObjectRequest(ossBucketName, objectName, inputStream);
            ossClient.putObject(putObjectRequest);
            String url = getFileUrl(objectName);
            log.info("OSS上传成功: {}", url);
            return url;
        } catch (Exception e) {
            log.error("OSS上传失败: object={}", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private void deleteFromOss(String objectName) {
        try {
            ossClient.deleteObject(ossBucketName, objectName);
            log.info("OSS删除成功: object={}", objectName);
        } catch (Exception e) {
            log.error("OSS删除失败: object={}", objectName, e);
        }
    }

    // ==================== 本地存储实现 ====================

    private String uploadToLocal(String objectName, InputStream inputStream) {
        try {
            String filePath = LOCAL_STORAGE_PATH + objectName;
            File file = new File(filePath);
            file.getParentFile().mkdirs();

            try (FileOutputStream fos = new FileOutputStream(file)) {
                byte[] buffer = new byte[8192];
                int bytesRead;
                while ((bytesRead = inputStream.read(buffer)) != -1) {
                    fos.write(buffer, 0, bytesRead);
                }
            }

            String url = "/files/" + objectName;
            log.info("本地上传成功: path={}, url={}", filePath, url);
            return url;
        } catch (Exception e) {
            log.error("本地上传失败: object={}", objectName, e);
            throw new RuntimeException("文件上传失败", e);
        }
    }

    private void deleteFromLocal(String objectName) {
        try {
            String filePath = LOCAL_STORAGE_PATH + objectName;
            File file = new File(filePath);
            if (file.exists()) {
                file.delete();
                log.info("本地删除成功: path={}", filePath);
            }
        } catch (Exception e) {
            log.error("本地删除失败: object={}", objectName, e);
        }
    }

    // ==================== 工具方法 ====================

    private boolean isNotEmpty(String str) {
        return str != null && !str.isEmpty();
    }
}
