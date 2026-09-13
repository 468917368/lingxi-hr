package com.lingxi.hr.agent.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟面试报告表
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockReport implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话ID */
    private String sessionId;

    /** 求职者用户ID */
    private Long candidateId;

    /** 综合评分(0-100) */
    private BigDecimal overallScore;

    /** 综合等级：EXCELLENT/GOOD/AVERAGE/NEED_IMPROVE */
    private String overallLevel;

    /** 技术准确度评分 */
    private BigDecimal techAccuracyScore;

    /** 表达逻辑评分 */
    private BigDecimal expressionScore;

    /** 知识深度评分 */
    private BigDecimal knowledgeDepthScore;

    /** 项目经验评分 */
    private BigDecimal projectScore;

    /** 亮点列表（JSON 数组） */
    private String highlights;

    /** 短板列表（JSON 数组） */
    private String weaknesses;

    /** 提升方案（JSON 数组） */
    private String improvementPlan;

    /** 总用时（秒） */
    private Integer totalDurationSec;

    /** 已答题数 */
    private Integer answeredCount;

    /** 跳过题数 */
    private Integer skippedCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
