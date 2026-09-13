package com.lingxi.job.domain.dto.request;

import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import lombok.Data;

import javax.validation.Valid;
import javax.validation.constraints.Future;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 岗位创建请求
 * <p>嵌套设计对齐系分：岗位基础字段 + 薪资 + 岗位画像。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobCreateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位名称 */
    @NotBlank(message = "岗位名称不能为空")
    @Size(max = 100, message = "岗位名称不能超过100字符")
    private String title;

    /** 岗位一级行业编码 */
    @NotBlank(message = "行业分组编码不能为空")
    private String industryGroupCode;

    /** 岗位具体行业编码 */
    @NotBlank(message = "行业编码不能为空")
    private String industryCode;

    /** 城市编码 */
    @NotBlank(message = "城市编码不能为空")
    private String cityCode;

    /** 城市展示名称（可空：后端以 cityCode 为唯一真值，按城市字典回填；保留字段兼容旧前端） */
    @Size(max = 64, message = "城市名称不能超过64字符")
    private String cityName;

    /** 最低工作年限 */
    @Min(value = 0, message = "最低工作年限不能为负")
    private Integer minExperienceYears = 0;

    /** 学历要求编码 */
    private String educationRequirement = "NONE";

    /** 薪资信息 */
    @Valid
    @NotNull(message = "薪资信息不能为空")
    private SalaryDTO salary;

    /** 岗位总HC */
    @Min(value = 1, message = "总HC至少为1")
    private Integer totalHc = 1;

    /** JD原文 */
    @Size(max = 20000, message = "JD原文不能超过20000字符")
    private String jdText;

    /** 岗位到期时间（P1可选，若提供须晚于当前时间） */
    @Future(message = "岗位到期时间必须晚于当前时间")
    private LocalDateTime expiresAt;

    /** 画像是否已确认 */
    @NotNull(message = "画像确认状态不能为空")
    private Boolean profileConfirmed;

    /** 岗位画像 */
    @Valid
    @NotNull(message = "岗位画像不能为空")
    private JobProfileDTO profile;

    // ==================== 嵌套结构 ====================

    /**
     * 薪资信息
     */
    @Data
    public static class SalaryDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 最低薪资（最小货币单位；CNY为分） */
        private Long minAmount;

        /** 最高薪资（最小货币单位；CNY为分） */
        private Long maxAmount;

        /** 币种 */
        private String currency = "CNY";

        /** 薪资周期：HOUR/DAY/MONTH/YEAR */
        private String period;

        /** 年薪月数，如13薪 */
        private Integer months;

        /** 是否面议 */
        @NotNull(message = "是否面议不能为空")
        private Boolean negotiable;

        /** JD中的原始薪资文本 */
        @Size(max = 128, message = "薪资原文不能超过128字符")
        private String rawText;
    }

    /**
     * 岗位画像（技能结构复用 JobRequirementResponse.CoreSkill/SoftSkill，保证与阶段一读写一致）
     */
    @Data
    public static class JobProfileDTO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 标准化岗位类型 */
        private String jobType;

        /** 核心技能 */
        @Size(max = 10, message = "核心技能最多10项")
        private List<JobRequirementResponse.CoreSkill> coreSkills;

        /** 软能力 */
        @Size(max = 10, message = "软能力最多10项")
        private List<JobRequirementResponse.SoftSkill> softSkills;

        /** 行业或业务经验要求 */
        private String industryExperience;

        /** 隐性要求 */
        @Size(max = 10, message = "隐性要求最多10项")
        private List<HiddenRequirement> hiddenRequirements;

        /** 面试考察重点 */
        @Size(max = 10, message = "面试考察重点最多10项")
        private List<String> interviewFocus;

        /** 画像来源：AI/MANUAL/MIXED */
        private String profileSource;
    }

    /**
     * 隐性要求项
     */
    @Data
    public static class HiddenRequirement implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 要求描述 */
        private String requirement;

        /** 依据 */
        private String basis;

        /** 是否推断 */
        private Boolean inferred;

        /** 置信度 */
        private Double confidence;

        /** HR是否确认 */
        private Boolean hrConfirmed;
    }
}
