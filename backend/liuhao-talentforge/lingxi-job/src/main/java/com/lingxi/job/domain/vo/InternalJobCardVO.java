package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 内部搜索岗位卡片（成员 A 依赖）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class InternalJobCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 企业ID */
    private Long companyId;

    /** 企业名称 */
    private String companyName;

    /** 城市编码 */
    private String cityCode;

    /** 城市展示名称 */
    private String cityName;

    /** 行业编码 */
    private String industryCode;

    /** 标准化岗位类型（来自 job_profile） */
    private String jobType;

    /** 最低薪资 */
    private Long salaryMinAmount;

    /** 最高薪资 */
    private Long salaryMaxAmount;

    /** 是否面议：0=否 1=是 */
    private Integer salaryNegotiable;

    /** 核心技能原始JSON（承接 job_profile.core_skills，供 MyBatis 映射，Service 解析后清空） */
    private String skillTagsRaw;

    /** 技能标签（Service 解析 skillTagsRaw 后填充，MyBatis 不映射此列） */
    private List<String> skillTags;

    /** 岗位状态 */
    private String status;

    /** 总HC */
    private Integer totalHc;

    /** 可用HC */
    private Integer availableHc;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;
}
