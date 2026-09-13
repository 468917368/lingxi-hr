package com.lingxi.resume.config;

import com.aliyun.oss.OSS;
import com.aliyun.oss.OSSClientBuilder;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Date;
import java.util.concurrent.TimeUnit;

/**
 * 简历模块统一存储客户端：按 {@code storage.type}（minio / aliyun-oss）分发文件访问。
 *
 * <p>背景：公共 {@link com.lingxi.common.util.MinioUtil} 支持 MinIO/OSS/本地三种存储，
 * 但简历模块此前硬编码 MinIO——自建 resumeMinioClient（MinIO SDK）直读原件 + 生成 presigned URL，
 * objectName 提取也硬编码桶前缀 {@code /hrms/}。服务器切到 OSS 后：
 * 上传走 MinioUtil 正常，但解析/头像/诊断的签名与下载全部失败（现象：上传成功但解析报
 * 「简历文件不可访问，无法解析」）。本类将 objectName 解析、presigned URL、对象下载统一抽象，
 * 按 storage.type 分发，消除硬编码存储假设。
 *
 * <p>OSS 注意：bucket 公共读时签名 URL 依然有效，故 OSS 路径也一律签名（与 MinIO 行为一致）。
 *
 * @author 成员C
 * @since 2026-08-09
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ResumeStorageClient {

    /** 签名有效期（7 天，与改造前 MinIO presigned 行为一致） */
    private static final long PRESIGN_EXPIRY_MS = 7 * 24 * 3600_000L;

    /** 存储类型：minio（默认，本地开发）/ aliyun-oss（服务器） */
    @Value("${storage.type:minio}")
    private String storageType;

    @Value("${storage.minio.bucket-name:hrms}")
    private String minioBucketName;

    @Value("${storage.aliyun-oss.endpoint:}")
    private String ossEndpoint;

    @Value("${storage.aliyun-oss.access-key-id:}")
    private String ossAccessKeyId;

    @Value("${storage.aliyun-oss.access-key-secret:}")
    private String ossAccessKeySecret;

    @Value("${storage.aliyun-oss.bucket-name:}")
    private String ossBucketName;

    /** MinIO 客户端（ResumeMinioConfig 构建；OSS 模式下存在但不用） */
    private final io.minio.MinioClient resumeMinioClient;

    /** OSS 客户端（懒加载：仅 storage.type=aliyun-oss 时构建） */
    private volatile OSS ossClient;

    /** 当前是否 MinIO 存储 */
    public boolean isMinio() {
        return "minio".equals(storageType);
    }

    /**
     * 从 fileUrl 提取 objectName
     *
     * <p>MinIO：{endpoint}/{bucket}/{objectName}；OSS：https://{bucket}.{endpoint}/{objectName}。
     * 均去掉查询参数（presigned URL 的 ?X-Amz-...），并做 URL 解码（presigned URL 中文被编码，
     * 而存储实际 key 是明文中文，解码后才能命中）。
     *
     * @return objectName；无法解析返回 null（调用方按业务处理）
     */
    public String parseObjectName(String fileUrl) {
        if (fileUrl == null || fileUrl.trim().isEmpty()) {
            return null;
        }
        String objectName;
        if (isMinio()) {
            int idx = fileUrl.lastIndexOf("/" + minioBucketName + "/");
            if (idx < 0) {
                log.warn("fileUrl 无法解析 objectName: {}", fileUrl);
                return null;
            }
            objectName = fileUrl.substring(idx + minioBucketName.length() + 2);
        } else {
            if (ossEndpoint == null || ossEndpoint.isEmpty()) {
                log.warn("OSS endpoint 未配置，无法解析 objectName: {}", fileUrl);
                return null;
            }
            int idx = fileUrl.lastIndexOf(ossEndpoint + "/");
            if (idx < 0) {
                log.warn("fileUrl 无法解析 objectName: {}", fileUrl);
                return null;
            }
            objectName = fileUrl.substring(idx + ossEndpoint.length() + 1);
        }
        int query = objectName.indexOf('?');
        if (query > 0) {
            objectName = objectName.substring(0, query);
        }
        try {
            return URLDecoder.decode(objectName, "UTF-8");
        } catch (UnsupportedEncodingException e) {
            log.warn("objectName URL 解码失败: object={}", objectName, e);
            return null;
        }
    }

    /**
     * 生成对象下载 URL（7 天签名；OSS 对公共读桶同样有效）
     *
     * @return 签名 URL；失败返回 null（调用方按业务处理）
     */
    public String presignedUrl(String objectName) {
        try {
            if (isMinio()) {
                return resumeMinioClient.getPresignedObjectUrl(
                        GetPresignedObjectUrlArgs.builder()
                                .method(Method.GET)
                                .bucket(minioBucketName)
                                .object(objectName)
                                .expiry(7, TimeUnit.DAYS)
                                .build());
            }
            Date expiry = new Date(System.currentTimeMillis() + PRESIGN_EXPIRY_MS);
            return lazyOssClient().generatePresignedUrl(ossBucketName, objectName, expiry).toString();
        } catch (Exception e) {
            log.error("存储生成 presigned URL 失败: object={}, type={}", objectName, storageType, e);
            return null;
        }
    }

    /**
     * 获取对象输入流（下载原件；SDK 直连，桶私有也可读）
     *
     * @throws RuntimeException 获取失败（调用方各自按业务兜底）
     */
    public InputStream getObject(String objectName) {
        try {
            if (isMinio()) {
                return resumeMinioClient.getObject(GetObjectArgs.builder()
                        .bucket(minioBucketName)
                        .object(objectName)
                        .build());
            }
            return lazyOssClient().getObject(ossBucketName, objectName).getObjectContent();
        } catch (Exception e) {
            log.error("存储获取对象流失败: object={}, type={}", objectName, storageType, e);
            throw new RuntimeException("存储获取对象流失败: " + objectName, e);
        }
    }

    /** OSS 客户端懒加载（线程安全；仅 OSS 模式下调用） */
    private OSS lazyOssClient() {
        if (ossClient == null) {
            synchronized (this) {
                if (ossClient == null) {
                    ossClient = new OSSClientBuilder().build(ossEndpoint, ossAccessKeyId, ossAccessKeySecret);
                    log.info("OSS 客户端初始化: endpoint={}, bucket={}", ossEndpoint, ossBucketName);
                }
            }
        }
        return ossClient;
    }
}
