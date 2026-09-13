package com.lingxi.hr.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Offer 列表项（系分 5.5.4）
 * <p>offerId 为 Snowflake 大数，JSON 序列化为字符串（前端 JS 精度要求，2026-08-07）。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class OfferVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Offer ID（Snowflake，JSON 字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long offerId;

    /** 投递记录ID */
    private Long applicationId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 候选人姓名（来自 lingxi-user，降级"用户"+id） */
    private String candidateName;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称（降级"岗位"+jobId） */
    private String jobTitle;

    /** 月薪（元） */
    private Integer salary;

    /** 预计入职日期 */
    private LocalDate entryDate;

    /** 职级 */
    private String level;

    /** Offer状态：SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN */
    private String status;

    /** 状态描述（OfferStatus.getDesc） */
    private String statusDesc;

    /** 有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 催促次数 */
    private Integer urgeCount;

    /** 拒绝原因（候选人填写/HR撤回，选填） */
    private String rejectReason;

    /** 确认时间 */
    private LocalDateTime acceptedAt;

    /** 拒绝时间 */
    private LocalDateTime rejectedAt;

    /** 发起时间 */
    private LocalDateTime createdAt;
}
