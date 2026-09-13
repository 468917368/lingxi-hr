package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.util.List;

/**
 * 题库搜索结果投影 VO（供百宝箱 Tool3 透传给 LLM 参考）
 * <p>
 * <b>安全边界：不包含 {@code referenceAnswer}/{@code id}/{@code companyId}</b>——
 * 题库题目内容可给 LLM 参考，但参考答案/企业标识不得外泄。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class QuestionPromptVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE */
    private String questionType;

    /** 难度：EASY/MEDIUM/HARD */
    private String difficulty;

    /** 题目内容 */
    private String content;

    /** 考察要点 */
    private String keyPoints;

    /** 题干 SHA-256（内容指纹，去重标识） */
    private String contentHash;

    /** 标准化技能标签列表 */
    private List<String> normalizedSkillTags;

    /** 结构化能力点（从 evaluation_points 解析，字符串化） */
    private String sanitizedAbilityPoints;
}
