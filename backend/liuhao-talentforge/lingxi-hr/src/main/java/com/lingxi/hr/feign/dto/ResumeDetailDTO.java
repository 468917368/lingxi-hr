package com.lingxi.hr.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 简历详情 DTO（来自 lingxi-resume 内部接口 {@code GET /internal/resumes/{id}/detail}）
 *
 * <p>全量字段镜像 lingxi-resume {@code ResumeDetailVO}（15 字段，含 {@code facePhotoUrl}）。
 * ⚠️ 注意：与 agent 包 {@code com.lingxi.hr.agent.feign.ResumeDetailDTO}（出题用精简 6 字段版）
 * 同名不同包，导入时勿混用。
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简历ID */
    private Long id;

    /** 原始文件名 */
    private String fileName;

    /** 文件格式 */
    private String fileFormat;

    /** 文件大小（字节） */
    private Integer fileSize;

    /** 是否默认简历：1=是 0=否 */
    private Integer isDefault;

    /** 解析状态：COMPLETED/FAILED/PENDING/PARSING */
    private String parseStatus;

    /** Agent 解析产出的 MD 文件 URL */
    private String resumeMdUrl;

    /** 人脸照片 URL（解析提取，可为 null） */
    private String facePhotoUrl;

    /** 卡片渲染指令（JSON 对象） */
    private Object cardStructure;

    /** 联系电话（用户手动填写） */
    private String phone;

    /** 联系邮箱（用户手动填写） */
    private String email;

    /** 微信号（用户手动填写） */
    private String wechat;

    /** 姓名（用户手动填写或解析预填） */
    private String candidateName;

    /** 上传时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 是否开启盲选模式（HR端扩展字段，非数据库字段） */
    private Boolean blindMode;
}
