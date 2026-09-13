package com.lingxi.resume.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 简历诊断报告实体（resume_diagnosis_report 表，Markdown正文存MinIO）
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class ResumeDiagnosisReport {

    /** 诊断报告ID */
    private Long id;

    /** 简历ID（关联 resume 表） */
    private Long resumeId;

    /** 目标职业名（不关联平台岗位） */
    private String career;

    /** MinIO文件路径（含时间戳） */
    private String fileUrl;

    /** 综合匹配度(0-100)，5维加权计算 */
    private BigDecimal matchScore;

    /** 诊断时间 */
    private LocalDateTime createdAt;
}
