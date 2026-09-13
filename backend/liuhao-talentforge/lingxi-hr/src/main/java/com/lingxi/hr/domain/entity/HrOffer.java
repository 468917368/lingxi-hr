package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer记录表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrOffer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Offer ID（Snowflake应用侧生成，非自增） */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 投递记录ID */
    private Long applicationId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 岗位ID */
    private Long jobId;

    /** 月薪（元） */
    private Integer salary;

    /** 预计入职日期 */
    private LocalDate entryDate;

    /** 职级 */
    private String level;

    /** 备注 */
    private String remark;

    /** Offer状态：SENT/ACCEPTED/REJECTED/EXPIRED */
    private String status;

    /** Offer有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 确认时间 */
    private LocalDateTime acceptedAt;

    /** 拒绝时间 */
    private LocalDateTime rejectedAt;

    /** 拒绝原因 */
    private String rejectReason;

    /** 催促次数 */
    private Integer urgeCount;

    /** 最近一次催促时间 */
    private LocalDateTime lastUrgeAt;

    /** 最后一次HC补偿对账时间 */
    private LocalDateTime lastSyncTime;

    /** 发起时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
