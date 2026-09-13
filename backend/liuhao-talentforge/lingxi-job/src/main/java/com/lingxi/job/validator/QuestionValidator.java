package com.lingxi.job.validator;

import com.lingxi.common.exception.BusinessException;
import com.lingxi.job.domain.dto.EvaluationPointDTO;
import com.lingxi.job.enums.DifficultyEnum;
import com.lingxi.job.enums.QuestionTypeEnum;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

/**
 * 题库写侧校验器（对齐 JobStateValidator 静态模式）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
public class QuestionValidator {

    private QuestionValidator() {
    }

    /** 技能标签最大个数 */
    private static final int MAX_SKILL_TAGS = 10;

    /** 题干最大长度 */
    private static final int MAX_CONTENT_LENGTH = 2000;

    /** 考察要点最大长度 */
    private static final int MAX_KEY_POINTS_LENGTH = 1000;

    /** 参考答案最大长度 */
    private static final int MAX_REFERENCE_ANSWER_LENGTH = 4000;

    /**
     * 校验并规范化题干：trim + 非空 + 长度限制
     *
     * @return trim 后的题干
     */
    public static String normalizeContent(String content) {
        if (!StringUtils.hasText(content)) {
            throw new BusinessException(400, "题干不能为空");
        }
        String c = content.trim();
        if (c.length() > MAX_CONTENT_LENGTH) {
            throw new BusinessException(400, "题干不能超过 " + MAX_CONTENT_LENGTH + " 字符");
        }
        return c;
    }

    /**
     * 校验题目类型合法性（不在 4 标准题型 → 400）
     */
    public static void validateQuestionType(String questionType) {
        if (!QuestionTypeEnum.isValid(questionType)) {
            throw new BusinessException(400, "题目类型编码不合法");
        }
    }

    /**
     * 规范化难度（复用 DifficultyEnum：null/空→MEDIUM，非法→400）
     */
    public static String normalizeDifficulty(String difficulty) {
        return DifficultyEnum.normalize(difficulty);
    }

    /**
     * 校验并规范化技能标签：逐项 trim、空项 → 400（对齐 C 端 normalizeSkillTags 惯例）、去重，≤10
     */
    public static List<String> normalizeSkillTags(List<String> skillTags) {
        if (skillTags == null || skillTags.isEmpty()) {
            return null;
        }
        LinkedHashSet<String> set = new LinkedHashSet<>();
        for (String tag : skillTags) {
            if (tag == null || !StringUtils.hasText(tag.trim())) {
                throw new BusinessException(400, "技能标签不能为空");
            }
            set.add(tag.trim());
        }
        if (set.size() > MAX_SKILL_TAGS) {
            throw new BusinessException(400, "技能标签最多 " + MAX_SKILL_TAGS + " 个");
        }
        return new ArrayList<>(set);
    }

    /**
     * 校验评分要点：null 可（无评分要点）；提供则非空数组且每项 name 非空 ≤50、weight ∈ [0,1]
     */
    public static void validateEvaluationPoints(List<EvaluationPointDTO> points) {
        if (points == null || points.isEmpty()) {
            return;
        }
        for (EvaluationPointDTO p : points) {
            if (p == null || !StringUtils.hasText(p.getName())) {
                throw new BusinessException(400, "评分要点 name 不能为空");
            }
            if (p.getName().trim().length() > 50) {
                throw new BusinessException(400, "评分要点 name 不能超过 50 字符");
            }
            if (p.getWeight() == null || p.getWeight() < 0 || p.getWeight() > 1) {
                throw new BusinessException(400, "评分要点 weight 需在 0~1 之间");
            }
        }
    }

    /**
     * 校验可选文本长度（考察要点/参考答案）
     */
    public static void validateOptionalText(String keyPoints, String referenceAnswer) {
        if (keyPoints != null && keyPoints.length() > MAX_KEY_POINTS_LENGTH) {
            throw new BusinessException(400, "考察要点不能超过 " + MAX_KEY_POINTS_LENGTH + " 字符");
        }
        if (referenceAnswer != null && referenceAnswer.length() > MAX_REFERENCE_ANSWER_LENGTH) {
            throw new BusinessException(400, "参考答案不能超过 " + MAX_REFERENCE_ANSWER_LENGTH + " 字符");
        }
    }
}
