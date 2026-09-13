package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * C端岗位详情
 * <p>profile 为脱敏画像（不含 hiddenRequirements/basis/confidence/confirmedBy/confirmedAt/profileSource/version），
 * 画像缺失时 profile 对象始终存在、各字段为默认值（jobType=null、列表=[]、profileConfirmed=false）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 企业名称（由创建人批量信息在 Service 组装；无法确认归属时降级为"招聘企业"） */
    private String companyName;

    /** 岗位录入责任人姓名（C 端展示为招聘负责人；降级为"招聘负责人"） */
    private String creatorName;

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

    /** 最低经验要求（年） */
    private Integer minExperienceYears;

    /** 学历要求编码 */
    private String educationRequirement;

    /** 薪资（嵌套展示） */
    private SalaryVO salary;

    /** JD原文 */
    private String jdText;

    /** JD摘要 */
    private String jdSummary;

    /** 脱敏画像 */
    private Profile profile;

    /** 首次发布时间 */
    private LocalDateTime publishedAt;

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

    /**
     * 脱敏画像（仅 C 端可见字段）
     */
    @Data
    public static class Profile implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 标准化岗位类型 */
        private String jobType;

        /** 核心技能（脱敏：仅 name/level/required） */
        private List<CoreSkill> coreSkills;

        /** 软能力（脱敏：仅 name/importance） */
        private List<SoftSkill> softSkills;

        /** 行业经验 */
        private String industryExperience;

        /** 考察重点 */
        private List<String> interviewFocus;

        /** 画像是否经 HR 确认（映射 confirmed_at IS NOT NULL） */
        private Boolean profileConfirmed;
    }

    /**
     * 核心技能（脱敏）
     */
    @Data
    public static class CoreSkill implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;
        private String level;
        private Boolean required;
    }

    /**
     * 软能力（脱敏）
     */
    @Data
    public static class SoftSkill implements Serializable {

        private static final long serialVersionUID = 1L;

        private String name;
        private String importance;
    }
}
