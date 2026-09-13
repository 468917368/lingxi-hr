package com.lingxi.job.feign.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * 投递记录内部 DTO（Feign 调 lingxi-resume {@code GET /internal/applications/{id}} 返回）
 * <p>字段对齐 resume 的 {@code InternalApplicationVO}（Feign 按字段名反序列化），
 * 供 Interview Agent 组装投递上下文（岗位/候选人/简历/企业归属/状态）。</p>
 *
 * @author 成员B
 * @since 2026-08-05
 */
@Data
public class InternalApplicationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String jobTitle;

    /** 投递归属企业ID（JOIN job_post.company_id，跨企业隔离校验） */
    private Long companyId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 使用的简历ID */
    private Long resumeId;

    /** 投递状态（出题白名单：SCREENED/INTERVIEWING） */
    private String status;

    /** 投递匹配度(0-100) */
    private BigDecimal matchScore;
}
