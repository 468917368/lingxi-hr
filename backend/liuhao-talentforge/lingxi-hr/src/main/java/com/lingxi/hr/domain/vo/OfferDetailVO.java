package com.lingxi.hr.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 候选人版 Offer 详情出参（跨端方案A，2026-08-07）
 * <p>供 C 端投递追踪页「Offer 待确认」卡片展示；offerId 为 Snowflake 大数，JSON 序列化为字符串。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class OfferDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Offer ID（Snowflake，JSON 字符串） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long offerId;

    /** 投递记录ID */
    private Long applicationId;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称（降级"岗位"+jobId） */
    private String jobTitle;

    /** 企业名称（来自 hr_company，查询失败置 null） */
    private String companyName;

    /** 月薪（元） */
    private Integer salary;

    /** 预计入职日期 */
    private LocalDate entryDate;

    /** 职级 */
    private String level;

    /** 备注 */
    private String remark;

    /** Offer状态：SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN */
    private String status;

    /** 状态描述（OfferStatus.getDesc） */
    private String statusDesc;

    /** 有效期截止时间 */
    private LocalDateTime expiresAt;

    /** 确认时间 */
    private LocalDateTime acceptedAt;

    /** 拒绝时间 */
    private LocalDateTime rejectedAt;

    /** 拒绝原因（选填） */
    private String rejectReason;

    /** 发起时间 */
    private LocalDateTime createdAt;
}
