package com.lingxi.hr.agent.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模拟面试报告 VO
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockReportVO {

    /** 会话ID */
    private String sessionId;

    /** 综合评分(0-100) */
    private BigDecimal overallScore;

    /** 综合等级：EXCELLENT/GOOD/AVERAGE/NEED_IMPROVE */
    private String overallLevel;

    /** 技术准确度均分 */
    private BigDecimal techAccuracyScore;

    /** 表达逻辑均分 */
    private BigDecimal expressionScore;

    /** 知识深度均分 */
    private BigDecimal knowledgeDepthScore;

    /** 项目经验均分（=PROJECT 题型均分） */
    private BigDecimal projectScore;

    /** 亮点 */
    private List<String> highlights;

    /** 短板 */
    private List<String> weaknesses;

    /** 提升方案 */
    private List<String> improvementPlan;

    /** 已答题数 */
    private Integer answeredCount;

    /** 跳过题数 */
    private Integer skippedCount;

    /** 总用时（秒） */
    private Integer totalDurationSec;
}
