package com.lingxi.resume.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 能力模型实体（resume_ability_model 表，5通用维度雷达图）
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class ResumeAbilityModel {

    /** 主键ID */
    private Long id;

    /** 求职者用户ID */
    private Long candidateId;

    /** 关联简历ID */
    private Long resumeId;

    /** 专业技能(0-100) */
    private Integer professionalSkillScore;

    /** 项目与工作经验(0-100) */
    private Integer workExperienceScore;

    /** 行业认知(0-100) */
    private Integer industryKnowledgeScore;

    /** 综合素质(0-100) */
    private Integer comprehensiveQualityScore;

    /** 学习成长(0-100) */
    private Integer learningGrowthScore;

    /** AI动态生成的各维度子项分析依据(JSON) */
    private String subDimensions;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
