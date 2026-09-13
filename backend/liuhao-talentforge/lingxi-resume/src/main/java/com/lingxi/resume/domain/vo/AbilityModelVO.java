package com.lingxi.resume.domain.vo;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 能力模型查询响应（雷达图数据）
 * <p>resumeId 序列化为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围，前端 JSON.parse 会精度丢失。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Data
public class AbilityModelVO {

    /** 简历ID（字符串序列化，防前端精度丢失） */
    @JsonSerialize(using = ToStringSerializer.class)
    private Long resumeId;

    /** 专业技能分（0-100） */
    private Integer professionalSkillScore;

    /** 工作经验分（0-100） */
    private Integer workExperienceScore;

    /** 行业认知分（0-100） */
    private Integer industryKnowledgeScore;

    /** 综合素质分（0-100） */
    private Integer comprehensiveQualityScore;

    /** 学习成长分（0-100） */
    private Integer learningGrowthScore;

    /** 子维度明细（JSON 对象，如技能列表/工作年限） */
    private Object subDimensions;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
