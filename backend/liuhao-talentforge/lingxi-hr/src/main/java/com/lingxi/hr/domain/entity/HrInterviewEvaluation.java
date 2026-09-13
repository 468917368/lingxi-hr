package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试评估表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrInterviewEvaluation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

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

    /** 面试评语（最少20字） */
    private String comment;

    /** AI生成的反馈 */
    private String feedback;

    /** 是否草稿：0=正式提交 1=草稿 */
    private Integer isDraft;

    /** 评估人用户ID */
    private Long evaluatorId;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
