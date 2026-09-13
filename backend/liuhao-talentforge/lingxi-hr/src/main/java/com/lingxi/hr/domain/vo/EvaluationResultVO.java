package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 录入面试评估出参（系分文档 5.5.3）
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Data
public class EvaluationResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评估记录ID（草稿覆盖转正式时同上） */
    private Long evaluationId;

    /** 面试结论：PASS/PENDING/REJECT */
    private String conclusion;

    /** AI 生成的反馈（本期延后，恒 null） */
    private String feedback;

    /** 正式提交后投递最新状态：OFFERABLE/REJECTED/INTERVIEWING */
    private String applicationStatus;

    /** 通知是否发送成功（best-effort） */
    private Boolean notificationSent;
}
