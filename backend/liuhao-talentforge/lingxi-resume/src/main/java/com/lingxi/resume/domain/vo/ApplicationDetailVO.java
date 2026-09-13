package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投递详情视图对象（C端）
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2 投递详情响应，
 * 含岗位信息（JOIN）、投递状态时间戳与状态时间线。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class ApplicationDetailVO {

    /** 投递记录ID（雪花ID字符串序列化，防 JS 精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long id;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称（JOIN job_post） */
    private String jobTitle;

    /** 城市展示名（JOIN job_post） */
    private String cityName;

    /** 企业ID（JOIN job_post） */
    private Long companyId;

    /** 企业名称（JOIN hr_company） */
    private String companyName;

    /** 企业Logo（JOIN hr_company） */
    private String companyLogo;

    /** 使用的简历ID */
    private Long resumeId;

    /** 投递状态 */
    private String status;

    /** 投递匹配度(0-100)，Job Agent投递时计算并快照 */
    private BigDecimal matchScore;

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

    /** 状态时间线（resume_status_log，正序） */
    private List<ApplicationTimelineVO> timeline;
}
