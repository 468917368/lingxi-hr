package com.lingxi.hr.agent.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟面试答题记录表
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockAnswer implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 题号（从1开始） */
    private Integer questionNumber;

    /** 题目内容 */
    private String questionContent;

    /** 考察维度 */
    private String questionDimension;

    /** 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE */
    private String questionType;

    /** 求职者作答内容 */
    private String candidateAnswer;

    /** 是否跳过：0=正常作答 1=跳过 */
    private Integer isSkipped;

    /** 技术准确度评分(0-100) */
    private BigDecimal techAccuracyScore;

    /** 表达逻辑评分(0-100) */
    private BigDecimal expressionScore;

    /** 知识深度评分(0-100) */
    private BigDecimal knowledgeDepthScore;

    /** 本题综合评分(0-100) */
    private BigDecimal overallScore;

    /** AI点评 */
    private String aiComment;

    /** 作答时间 */
    private LocalDateTime answeredAt;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
