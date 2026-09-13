package com.lingxi.resume.agent;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.Data;

/**
 * Agent 解析产出（card_structure + 5 维能力模型 + resume.md）
 *
 * <p>由 {@link ResumeAgentService} 从 final 输出解析而来，或由
 * {@link RuleParseFallback} 降级产生；最终由 {@link ResumeParseService} 落库。
 *
 * @author 成员C
 * @since 2026-08-03
 */
@Data
public class AgentResult {

    /** 姓名（LLM 从 PDF 中提取，不入 card_structure） */
    private String name;

    /** 手机号（LLM 提取） */
    private String phone;

    /** 邮箱（LLM 提取） */
    private String email;

    /** 微信号（LLM 提取，可为 null） */
    private String wechat;

    /** 卡片渲染 JSON（sections[].points[]） */
    private JsonNode cardStructure;

    /** 专业技能分（0-100） */
    private int professionalSkillScore;

    /** 工作经验分（0-100） */
    private int workExperienceScore;

    /** 行业认知分（0-100） */
    private int industryKnowledgeScore;

    /** 综合素质分（0-100） */
    private int comprehensiveQualityScore;

    /** 学习成长分（0-100） */
    private int learningGrowthScore;

    /** 子维度明细（JSON 字符串，如技能列表/工作年限等） */
    private String subDimensions;

    /** Markdown 版简历正文 */
    private String resumeMd;
}
