package com.lingxi.resume.domain.entity;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 投递记录实体（resume_application 表）
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class ResumeApplication {

    /** 投递记录ID */
    private Long id;

    /** 岗位ID */
    private Long jobId;

    /** 求职者用户ID */
    private Long candidateId;

    /** 使用的简历ID */
    private Long resumeId;

    /** 投递状态：SUBMITTED/VIEWED/SCREENED/INTERVIEWING/OFFERABLE/OFFERED/OFFER_ACCEPTED/OFFER_DECLINED/REJECTED/WITHDRAWN */
    private String status;

    /** 投递匹配度(0-100)，Job Agent投递时计算并快照 */
    private BigDecimal matchScore;

    /** AI评估分(0-100)，求职助手(A)计算并写入 */
    private BigDecimal aiScore;

    /** AI详细分析(JSON)，求职助手(A)计算并写入 */
    private String aiAnalysis;

    /** AI落选反馈(JSON)：{"reason":"原因","suggestions":["建议"]} */
    private String rejectFeedback;

    /** 投递时间 */
    private LocalDateTime submittedAt;

    /** HR首次查看时间 */
    private LocalDateTime viewedAt;

    /** 筛选通过时间 */
    private LocalDateTime screenedAt;

    /** 进入面试时间 */
    private LocalDateTime interviewingAt;

    /** 可录用时间 */
    private LocalDateTime offerableAt;

    /** 已录用时间 */
    private LocalDateTime offeredAt;

    /** 淘汰时间 */
    private LocalDateTime rejectedAt;

    /** 撤回时间 */
    private LocalDateTime withdrawnAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    // ==================== JOIN 冗余字段（非表列，仅查询返回承载） ====================

    /** 岗位名称（JOIN job_post.title） */
    private String jobTitle;

    /** 城市展示名（JOIN job_post.city_name） */
    private String cityName;

    /** 企业ID（JOIN job_post.company_id） */
    private Long companyId;

    /** 企业名称（JOIN hr_company.name） */
    private String companyName;

    /** 企业Logo（JOIN hr_company.logo_url） */
    private String companyLogo;

    /** 候选人姓名（JOIN sys_user.name，HR端列表用） */
    private String candidateName;
}
