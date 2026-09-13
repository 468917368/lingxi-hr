package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试评估详情（查看评估接口出参，2026-08-06 新增对齐前端「查看评估」）
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Data
public class EvaluationDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 面试记录ID */
    private Long interviewId;

    /** 面试结论：PASS/PENDING/REJECT */
    private String conclusion;

    /** 技术能力评分(1-5) */
    private Integer techScore;

    /** 沟通表达评分(1-5) */
    private Integer communicationScore;

    /** 岗位匹配评分(1-5) */
    private Integer matchScore;

    /** 发展潜力评分(1-5) */
    private Integer potentialScore;

    /** 面试评语 */
    private String comment;

    /** AI 生成的反馈（本期延后，恒 null） */
    private String feedback;

    /** 是否草稿 */
    private Boolean isDraft;

    /** 评估人用户ID */
    private Long evaluatorId;

    /** 评估人姓名 */
    private String evaluatorName;

    /** 评估时间 */
    private LocalDateTime createdAt;
}
