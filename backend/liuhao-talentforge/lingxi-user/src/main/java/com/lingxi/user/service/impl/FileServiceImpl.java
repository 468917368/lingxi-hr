package com.lingxi.user.service.impl;

import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.util.MinioUtil;
import com.lingxi.user.exception.UserErrorCode;
import com.lingxi.user.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * 文件服务实现
 * <p>
 * 职责：文件上传/删除，不涉及用户实体持久化。
 * 用户头像URL更新由 UserService 负责。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class FileServiceImpl implements FileService {

    private final MinioUtil minioUtil;

    /** 允许的图片类型 */
    private static final List<String> ALLOWED_IMAGE_TYPES = Arrays.asList(
            "image/jpeg", "image/png"
    );

    /** 最大文件大小：2MB */
    private static final long MAX_AVATAR_SIZE = 2 * 1024 * 1024L;

    /** 头像存储路径前缀 */
    private static final String AVATAR_PATH_PREFIX = "avatars/";

    @Override
    public String uploadAvatar(Long userId, MultipartFile file) {
        // 1. 校验文件
        validateFile(file);

        // 2. 生成文件名
        String originalFilename = file.getOriginalFilename();
        String extension = getFileExtension(originalFilename);
        String objectName = AVATAR_PATH_PREFIX + userId + "/" + UUID.randomUUID() + extension;

        // 3. 上传文件（不使用@Transactional，采用手动补偿策略）
        try (InputStream inputStream = file.getInputStream()) {
            String url = minioUtil.upload(objectName, inputStream, file.getContentType());
            log.info("头像文件上传成功: userId={}, objectName={}, url={}", userId, objectName, url);
            return url;
        } catch (Exception e) {
            // 上传失败时尝试清理已上传的文件（补偿策略）
            try {
                minioUtil.delete(objectName);
                log.warn("上传失败，已清理文件: objectName={}", objectName);
            } catch (Exception cleanupEx) {
                log.error("清理文件失败: objectName={}", objectName, cleanupEx);
            }
            log.error("头像文件上传失败: userId={}", userId, e);
            throw new BusinessException(UserErrorCode.SYSTEM_BUSY);
        }
    }

    @Override
    @Async("asyncExecutor")
    public void deleteFile(String objectName) {
        if (objectName == null || objectName.isEmpty()) {
            return;
        }
        try {
            minioUtil.delete(objectName);
            log.info("文件删除成功: objectName={}", objectName);
        } catch (Exception e) {
            // 文件删除失败不影响业务，仅记录日志
            log.warn("文件删除失败（不影响业务）: objectName={}", objectName, e);
        }
    }

    /**
     * 从URL提取对象名称
     * <p>
     * 例: /files/avatars/2/uuid.jpg -> avatars/2/uuid.jpg
     * </p>
     *
     * @param url 文件URL（相对路径或绝对路径）
     * @return 对象名称，提取失败返回null
     */
    @Override
    public String extractObjectName(String url) {
        if (url == null || url.isEmpty()) {
            return null;
        }

        // 相对路径格式: /files/{objectName}
        String relativePrefix = "/files/";
        if (url.startsWith(relativePrefix)) {
            return url.substring(relativePrefix.length());
        }

        // 兼容绝对路径格式: http://localhost:8086/files/{objectName}
        String absolutePrefix = "http://localhost:8086/files/";
        if (url.startsWith(absolutePrefix)) {
            return url.substring(absolutePrefix.length());
        }

        // 兼容其他URL格式：{endpoint}/{bucket}/{objectName}
        int bucketIndex = url.indexOf("/", url.indexOf("//") + 2);
        if (bucketIndex > 0) {
            int objectStart = url.indexOf("/", bucketIndex + 1);
            if (objectStart > 0) {
                return url.substring(objectStart + 1);
            }
        }
        return null;
    }

    /**
     * 校验文件
     *
     * @param file 上传的文件
     * @throws BusinessException 文件校验失败时抛出异常
     */
    private void validateFile(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(UserErrorCode.AVATAR_FILE_EMPTY);
        }

        // 校验文件大小
        if (file.getSize() > MAX_AVATAR_SIZE) {
            throw new BusinessException(UserErrorCode.AVATAR_FILE_TOO_LARGE);
        }

        // 校验文件类型
        String contentType = file.getContentType();
        if (contentType == null || !ALLOWED_IMAGE_TYPES.contains(contentType)) {
            throw new BusinessException(UserErrorCode.AVATAR_FORMAT_INVALID);
        }
    }

    /**
     * 获取文件扩展名
     *
     * @param filename 文件名
     * @return 文件扩展名（包含.），默认返回.jpg
     */
    private String getFileExtension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}
