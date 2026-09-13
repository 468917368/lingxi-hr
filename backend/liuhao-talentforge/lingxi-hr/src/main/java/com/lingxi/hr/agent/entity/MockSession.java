package com.lingxi.hr.agent.entity;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 模拟面试会话表
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
public class MockSession implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话唯一标识，如 mock-20260804-001 */
    private String sessionId;

    /** 求职者用户ID */
    private Long candidateId;

    /** 目标岗位ID */
    private Long jobId;

    /** 目标岗位名称（冗余，便于历史查询） */
    private String jobTitle;

    /** 总题数：5 或 8 */
    private Integer totalQuestions;

    /** 综合评分(0-100) */
    private BigDecimal overallScore;

    /** 状态：IN_PROGRESS/COMPLETED */
    private String status;

    /** 开始时间 */
    private LocalDateTime startedAt;

    /** 完成时间 */
    private LocalDateTime completedAt;
}
