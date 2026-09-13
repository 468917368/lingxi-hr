package com.lingxi.job.domain.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C端岗位发现列表卡片
 * <p>薪资以扁平字段承接 MyBatis 映射，Service 组装成嵌套 SalaryVO 后清空扁平字段；
 * skillTagsRaw 承接 job_profile.core_skills，Service 解析成 skillTags 后清空。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobCardVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 企业名称（由创建人批量信息在 Service 组装；无法确认归属时降级为"招聘企业"） */
    private String companyName;

    /** 岗位录入责任人姓名（C 端展示为招聘负责人；降级为"招聘负责人"） */
    private String creatorName;

    /** 企业ID（仅 Service 组装用，序列化不输出） */
    @JsonIgnore
    private Long companyId;

    /** 创建人用户ID（仅 Service 组装用，序列化不输出） */
    @JsonIgnore
    private Long createdBy;

    /** 一级行业编码 */
    private String industryGroupCode;

    /** 具体行业编码（第一版与一级行业相同） */
    private String industryCode;

    /** 行业展示名称 */
    private String industryName;

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

    /** 核心技能原始JSON（承接 job_profile.core_skills，Service 解析后清空） */
    private String skillTagsRaw;

    /** 技能标签（Service 解析 skillTagsRaw 后填充） */
    private List<String> skillTags;

    /** 最低经验要求（年） */
    private Integer minExperienceYears;

    /** 学历要求编码 */
    private String educationRequirement;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;

    /** 推荐分（SQL 临时计算，仅 sortBy=RECOMMENDED 时排序依据） */
    private Integer recommendScore;

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
