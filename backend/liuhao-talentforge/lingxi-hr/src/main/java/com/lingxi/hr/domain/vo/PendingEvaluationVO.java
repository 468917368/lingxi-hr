package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 待评估列表项（系分文档 5.5.3）
 *
 * <p>2026-08-06 修正：查 IN_PROGRESS 且无正式评估（原 COMPLETED 且无评估恒空）。
 * schema 无 completed_at 列，completedAt 字段删除，逾期用 scheduledAt 兜底。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Data
public class PendingEvaluationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 面试记录ID */
    private Long interviewId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 候选人姓名 */
    private String candidateName;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String jobTitle;

    /** 预约面试时间 */
    private LocalDateTime scheduledAt;

    /** 是否逾期：scheduledAt 距今 >24h 仍未正式评估 */
    private Boolean isOverdue;
}
