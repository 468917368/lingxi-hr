package com.lingxi.user.service;

import org.springframework.web.multipart.MultipartFile;

/**
 * 文件服务接口
 * <p>
 * 职责：文件上传/删除，不涉及用户实体持久化。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-02
 */
public interface FileService {

    /**
     * 上传用户头像文件
     *
     * @param userId 用户ID（用于生成存储路径）
     * @param file   头像文件
     * @return 头像访问URL
     */
    String uploadAvatar(Long userId, MultipartFile file);

    /**
     * 删除文件
     *
     * @param objectName 对象名称（如 avatars/2/uuid.jpg）
     */
    void deleteFile(String objectName);

    /**
     * 从URL提取对象名称
     *
     * @param url 文件URL
     * @return 对象名称，提取失败返回null
     */
    String extractObjectName(String url);
}
