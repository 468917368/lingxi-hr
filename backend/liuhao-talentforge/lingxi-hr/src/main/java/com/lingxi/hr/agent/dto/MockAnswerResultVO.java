package com.lingxi.hr.agent.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单题评分结果 VO
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockAnswerResultVO {

    /** 题号 */
    private Integer questionNumber;

    /** 技术准确度评分(0-100) */
    private BigDecimal techAccuracyScore;

    /** 表达逻辑评分(0-100) */
    private BigDecimal expressionScore;

    /** 知识深度评分(0-100) */
    private BigDecimal knowledgeDepthScore;

    /** 本题综合评分(0-100)：tech×0.5 + expr×0.3 + depth×0.2 */
    private BigDecimal overallScore;

    /** AI 点评 */
    private String aiComment;
}
