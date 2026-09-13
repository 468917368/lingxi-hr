package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 标记候选人合适/不合适出参（系分文档 5.5.2）
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
public class MarkCandidateResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long applicationId;

    /** 标记后的投递状态：SCREENED / REJECTED */
    private String newStatus;

    /** 状态说明：筛选通过 / 已淘汰 */
    private String newStatusDesc;

    /** 候选人通知是否发送成功（MQ best-effort） */
    private Boolean notificationSent;
}
