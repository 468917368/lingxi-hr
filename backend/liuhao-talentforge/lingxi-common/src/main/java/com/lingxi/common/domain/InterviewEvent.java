package com.lingxi.common.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 面试事件消息体
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Data
public class InterviewEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型：INTERVIEW_EVALUATED */
    private String eventType;

    /** 面试记录ID */
    private Long interviewId;

    /** 投递记录ID */
    private Long applicationId;

    /** 候选人ID */
    private Long candidateId;

    /** 岗位ID */
    private Long jobId;

    /** 评估结果：PASS/FAIL */
    private String result;

    /** 事件时间 */
    private LocalDateTime eventTime;
}
