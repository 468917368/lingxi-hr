package com.lingxi.resume.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投递记录内部视图（HR端返回）
 *
 * <p>字段对齐 lingxi-hr 模块 {@code ResumeFeignClient} 契约的
 * {@code ApplicationDTO}（id/jobId/jobTitle/candidateId/resumeId/status/matchScore/aiScore/appliedAt），
 * Feign 按字段名反序列化。aiScore 为 HR 侧预留字段（DB 无对应列，返回 null）。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class InternalApplicationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 投递归属企业ID（JOIN job_post.company_id，用于跨企业隔离校验 4301） */
    private Long companyId;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称（JOIN job_post.title） */
    private String jobTitle;

    /** 候选人用户ID */
    private Long candidateId;

    /** 使用的简历ID */
    private Long resumeId;

    /** 投递状态 */
    private String status;

    /** 投递匹配度(0-100) */
    private BigDecimal matchScore;

    /** AI评估分(0-100)，求职助手(A)写入 */
    private BigDecimal aiScore;

    /** AI详细分析（求职助手(A)写入，JSON 对象） */
    private Object aiAnalysis;

    /** 投递时间 */
    private LocalDateTime appliedAt;
}
