package com.lingxi.job.domain.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * 投递上下文 DTO（Feign 调 lingxi-resume 返回）
 * <p>
 * 基于总体系分 L3423，补充 {@code companyId}——L3425 要求 B 校验 companyId 匹配，
 * 字段清单未列故补充（跨企业数据隔离硬要求，联调前与 lingxi-resume 团队确认）。
 * </p>
 * <p>
 * {@code dataCompleteness} 为 {@code BigDecimal}（0~1）：null/非数字/越界 → 按
 * "完整度不可用"降级通用题（不 503）。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class InterviewContextDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long applicationId;

    /** 岗位ID */
    private Long jobId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 简历ID */
    private Long resumeId;

    /** 企业ID（补充字段，供 B 校验跨企业隔离） */
    private Long companyId;

    /** 投递状态 */
    private String status;

    /** 简历亮点（脱敏前原始数据，B 侧二次脱敏） */
    private List<String> resumeHighlights;

    /** 简历完整度（0~1） */
    private BigDecimal dataCompleteness;
}
