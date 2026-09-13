package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * JD 解析响应 DTO（画像草稿）
 * <p>
 * 字段结构对齐百宝箱 Agent 的 JSON Schema 输出（见 AiParseServiceImpl 组装 Prompt）。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class JdParseResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 标准化岗位类型，如 JAVA_BACKEND/FRONTEND */
    private String jobType;

    /** 核心技能 */
    private List<CoreSkill> coreSkills;

    /** 软能力 */
    private List<SoftSkill> softSkills;

    /** 最低工作年限 */
    private Integer minExperienceYears;

    /** 学历要求编码：NONE/COLLEGE/BACHELOR/MASTER/PHD */
    private String educationRequirement;

    /** 行业或业务经验要求 */
    private String industryExperience;

    /** 面试考察重点 */
    private List<InterviewFocusItem> interviewFocus;

    /** 隐性要求 */
    private List<HiddenRequirement> hiddenRequirements;

    /** 薪资信息 */
    private Salary salary;

    /** 经安全清洗的 JD 摘要 */
    private String jdSummary;

    /** 解析警告（如薪资无法识别） */
    private List<String> warnings;

    /** 核心技能子项 */
    @Data
    public static class CoreSkill implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 技能名称 */
        private String name;
        /** 级别：1/2/3/4/5 */
        private String level;
        /** 是否硬性要求 */
        private Boolean required;
        /** 依据 */
        private String basis;
        /** 置信度 0~1 */
        private Double confidence;
    }

    /** 软能力子项 */
    @Data
    public static class SoftSkill implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 能力名称 */
        private String name;
        /** 重要度：1/2/3/4/5 */
        private String importance;
        /** 是否推断（非 JD 明示） */
        private Boolean inferred;
        /** 置信度 0~1 */
        private Double confidence;
    }

    /** 面试考察重点子项 */
    @Data
    public static class InterviewFocusItem implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 考察点名称 */
        private String name;
        /** 级别 */
        private String level;
        /** 是否硬性要求 */
        private Boolean required;
    }

    /** 隐性要求子项 */
    @Data
    public static class HiddenRequirement implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 隐性要求描述 */
        private String requirement;
        /** 依据 */
        private String basis;
        /** 是否推断 */
        private Boolean inferred;
        /** 置信度 0~1 */
        private Double confidence;
        /** 是否 HR 确认（草稿阶段恒为 false） */
        private Boolean hrConfirmed;
    }

    /** 薪资信息 */
    @Data
    public static class Salary implements Serializable {
        private static final long serialVersionUID = 1L;
        /** 币种，CNY */
        private String currency;
        /** 薪资周期：HOUR/DAY/MONTH/YEAR */
        private String period;
        /** 最低薪资（最小货币单位，CNY 为分） */
        private Long minAmount;
        /** 最高薪资（最小货币单位，CNY 为分） */
        private Long maxAmount;
        /** JD 原始薪资文本（无法结构化识别时保留） */
        private String rawText;
    }
}
