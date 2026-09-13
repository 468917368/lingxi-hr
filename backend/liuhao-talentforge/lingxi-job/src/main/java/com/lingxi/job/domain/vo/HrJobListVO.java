package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HR 岗位分页列表项
 * <p>薪资以扁平字段承接 MyBatis 映射，Service 组装成嵌套 SalaryVO 后清空扁平字段。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class HrJobListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 岗位状态 */
    private String status;

    /** 城市编码 */
    private String cityCode;

    /** 城市展示名称 */
    private String cityName;

    // ==================== 薪资（扁平承接，Service 组装后清空） ====================
    private Long salaryMinAmount;
    private Long salaryMaxAmount;
    private String salaryCurrency;
    private String salaryPeriod;
    private Integer salaryMonths;
    private Integer salaryNegotiable;
    private String salaryRawText;

    /** 薪资（嵌套展示，Service 组装） */
    private SalaryVO salary;

    /** 总HC */
    private Integer totalHc;

    /** 待确认Offer预冻结HC */
    private Integer reservedHc;

    /** 已接受Offer正式占用HC */
    private Integer confirmedHc;

    /** 可用HC */
    private Integer availableHc;

    /** 标准化岗位类型（来自 job_profile） */
    private String jobType;

    /** 核心技能原始JSON（承接 job_profile.core_skills，Service 解析后清空） */
    private String skillTagsRaw;

    /** 技能标签（Service 解析 skillTagsRaw 后填充） */
    private List<String> skillTags;

    /** 岗位版本 */
    private Integer version;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 创建人用户ID（复用 job_post.created_by） */
    private Long createdBy;

    /** 创建人姓名（Service 组装；用户服务不可用/缺失时降级为 "用户"+id） */
    private String createdByName;

    /**
     * 薪资展示结构
     */
    @Data
    public static class SalaryVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long minAmount;
        private Long maxAmount;
        private String currency;
        private String period;
        private Integer months;
        private Boolean negotiable;
        private String rawText;
    }
}
