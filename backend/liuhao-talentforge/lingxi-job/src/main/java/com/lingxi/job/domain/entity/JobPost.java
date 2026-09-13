package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位表实体
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobPost implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 岗位名称 */
    private String title;

    /** 岗位一级行业编码 */
    private String industryGroupCode;

    /** 岗位具体行业编码 */
    private String industryCode;

    /** 城市编码 */
    private String cityCode;

    /** 城市展示名称 */
    private String cityName;

    /** 最低工作年限 */
    private Integer minExperienceYears;

    /** 学历要求编码 */
    private String educationRequirement;

    /** 最低薪资（最小货币单位；CNY为分） */
    private Long salaryMinAmount;

    /** 最高薪资（最小货币单位；CNY为分） */
    private Long salaryMaxAmount;

    /** 币种 */
    private String salaryCurrency;

    /** 薪资周期：HOUR/DAY/MONTH/YEAR */
    private String salaryPeriod;

    /** 年薪月数，如13薪 */
    private Integer salaryMonths;

    /** 是否面议：0=否 1=是 */
    private Integer isSalaryNegotiable;

    /** JD中的原始薪资文本 */
    private String salaryRawText;

    /** 岗位总HC */
    private Integer totalHc;

    /** 待确认Offer预冻结HC */
    private Integer reservedHc;

    /** 已接受Offer正式占用HC */
    private Integer confirmedHc;

    /** JD原文 */
    private String jdText;

    /** 经安全清洗的JD摘要，用于关键词检索 */
    private String jdSummary;

    /** 岗位状态：DRAFT/PUBLISHED/PAUSED/CLOSED */
    private String status;

    /** 暂停原因 */
    private String pauseReason;

    /** 关闭原因 */
    private String closeReason;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 岗位到期时间 */
    private LocalDateTime expiresAt;

    /** 岗位乐观锁版本号 */
    private Integer version;

    /** 创建人用户ID */
    private Long createdBy;

    /** 最后更新人用户ID */
    private Long updatedBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除时间 */
    private LocalDateTime deletedAt;
}
