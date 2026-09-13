package com.lingxi.resume.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 简历实体（resume 表）
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class Resume {

    /** 简历ID */
    private Long id;

    /** 求职者用户ID */
    private Long candidateId;

    /** 原始文件名 */
    private String fileName;

    /** 原始文件存储URL（MinIO/OSS） */
    private String fileUrl;

    /** 文件格式：pdf/docx/doc/jpg/png */
    private String fileFormat;

    /** 文件大小（字节） */
    private Integer fileSize;

    /** 是否默认简历：0=否 1=是 */
    private Integer isDefault;

    /** 解析状态：PENDING/PARSING/COMPLETED/FAILED */
    private String parseStatus;

    /** Agent解析产出的MD文件MinIO路径 */
    private String resumeMdUrl;

    /** 人脸照片MinIO路径（解析提取，可为NULL） */
    private String facePhotoUrl;

    /** 卡片渲染JSON（confidenceLOW时保留raw_text） */
    private String cardStructure;

    /** 联系电话（用户手动填写，非解析产物） */
    private String phone;

    /** 联系邮箱（用户手动填写，非解析产物） */
    private String email;

    /** 微信号（用户手动填写，非解析产物） */
    private String wechat;

    /** 姓名（用户手动填写或解析预填） */
    private String candidateName;

    /** 逻辑删除时间（NULL=未删除） */
    private LocalDateTime deletedAt;

    /** 上传时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
