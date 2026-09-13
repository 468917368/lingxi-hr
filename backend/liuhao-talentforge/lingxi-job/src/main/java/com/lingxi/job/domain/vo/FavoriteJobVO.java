package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 我的收藏列表卡片（专用 VO，不污染 JobCardVO/JobDetailVO）
 * <p>jobId 取 f.job_id（收藏表主键）：物理缺失岗位仍保留供前端识别/取消，不可取 j.id（缺失时为 null）。
 * isOffline/deleted 由 SQL CASE 显式计算：物理缺失（j.id IS NULL）/逻辑删除（deleted_at 非空）/
 * 非 PUBLISHED 均 offline；物理缺失/逻辑删除均 deleted。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class FavoriteJobVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID（f.job_id，物理缺失时仍保留） */
    private Long jobId;

    /** 岗位名称（物理缺失时为 null） */
    private String title;

    /** 企业名称（本阶段固定 null，阶段4 建 CompanyFeignClient 后补齐） */
    private String companyName;

    /** 一级行业编码 */
    private String industryGroupCode;

    /** 具体行业编码 */
    private String industryCode;

    /** 行业展示名称（Service 组装：IndustryCodeEnum.fromCode(industryGroupCode).getDesc()） */
    private String industryName;

    /** 城市编码 */
    private String cityCode;

    /** 城市展示名称 */
    private String cityName;

    /** 最低薪资（最小货币单位；CNY为分） */
    private Long salaryMinAmount;

    /** 最高薪资（最小货币单位） */
    private Long salaryMaxAmount;

    /** 币种 */
    private String salaryCurrency;

    /** 薪资周期：HOUR/DAY/MONTH/YEAR */
    private String salaryPeriod;

    /** 年薪月数，如13薪 */
    private Integer salaryMonths;

    /** 是否面议：0=否 1=是 */
    private Integer salaryNegotiable;

    /** JD 中原始薪资文本 */
    private String salaryRawText;

    /** 最低经验要求（年） */
    private Integer minExperienceYears;

    /** 学历要求编码 */
    private String educationRequirement;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;

    /** 收藏时间（f.created_at） */
    private LocalDateTime favoritedAt;

    /** 岗位是否下架/不可见：物理缺失 or 已删除 or 非 PUBLISHED（收藏记录仍保留，供前端取消） */
    private Boolean isOffline;

    /** 岗位是否物理缺失或已逻辑删除 */
    private Boolean deleted;
}
