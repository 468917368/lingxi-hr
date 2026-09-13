package com.lingxi.hr.agent.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 简历能力模型 DTO（来自 lingxi-resume /api/v1/resumes/{id}/ability-model）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AbilityModelDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简历ID */
    private Long resumeId;

    /** 专业技能（0-100） */
    private Integer professionalSkillScore;

    /** 项目与工作经验（0-100） */
    private Integer workExperienceScore;

    /** 行业认知（0-100） */
    private Integer industryKnowledgeScore;

    /** 综合素质（0-100） */
    private Integer comprehensiveQualityScore;

    /** 学习成长（0-100） */
    private Integer learningGrowthScore;

    /** 子维度明细（JSON 对象，如技能列表/工作年限/评分依据） */
    private Object subDimensions;

    private LocalDateTime updatedAt;
}
