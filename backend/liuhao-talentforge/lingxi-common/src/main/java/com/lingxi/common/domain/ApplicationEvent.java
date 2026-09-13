package com.lingxi.common.domain;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 投递事件消息体
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Data
public class ApplicationEvent implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 事件类型：NEW_APPLICATION / APPLICATION_WITHDRAWN */
    private String eventType;

    /** 投递记录ID */
    private Long applicationId;

    /** 候选人ID */
    private Long candidateId;

    /** 岗位ID */
    private Long jobId;

    /** 事件时间 */
    private LocalDateTime eventTime;
}
