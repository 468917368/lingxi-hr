package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员岗位分页列表项（成员 E 依赖）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class AdminJobListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 企业ID */
    private Long companyId;

    /** 岗位名称 */
    private String title;

    /** 城市名称 */
    private String cityName;

    /** 最低薪资（最小货币单位；CNY为分） */
    private Long salaryMinAmount;

    /** 最高薪资（最小货币单位；CNY为分） */
    private Long salaryMaxAmount;

    /** 是否面议（0=否 1=面议） */
    private Integer salaryNegotiable;

    /** 岗位状态 */
    private String status;

    /** 总HC */
    private Integer totalHc;

    /** 待确认Offer预冻结HC */
    private Integer reservedHc;

    /** 已接受Offer正式占用HC */
    private Integer confirmedHc;

    /** 可用HC */
    private Integer availableHc;

    /** 乐观锁版本（供成员 E 下架前读取最新版本） */
    private Integer version;

    /** 标准化岗位类型 */
    private String jobType;

    /** 核心技能原始JSON（承接 job_profile.core_skills，Service 解析后清空） */
    private String skillTagsRaw;

    /** 技能标签（Service 解析 skillTagsRaw 后填充） */
    private List<String> skillTags;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;
}
