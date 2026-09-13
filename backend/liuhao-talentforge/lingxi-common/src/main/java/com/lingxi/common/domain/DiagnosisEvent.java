package com.lingxi.common.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 诊断事件消息体
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Data
public class DiagnosisEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型：DIAGNOSIS_COMPLETE */
    private String eventType;

    /** 简历ID */
    private Long resumeId;

    /** 候选人ID */
    private Long candidateId;

    /** 目标职业 */
    private String career;

    /** 匹配度 */
    private Double matchScore;

    /** 事件时间 */
    private LocalDateTime eventTime;
}
