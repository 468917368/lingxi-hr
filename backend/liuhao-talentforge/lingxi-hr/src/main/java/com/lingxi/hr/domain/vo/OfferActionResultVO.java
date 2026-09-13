package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 候选人接受/拒绝 Offer 出参
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class OfferActionResultVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Offer 最新状态：ACCEPTED/REJECTED */
    private String offerStatus;

    /** 投递最新状态：OFFERED/WITHDRAWN/REJECTED（C 落地前兜底） */
    private String applicationStatus;

    /** 通知是否发送成功 */
    private Boolean notificationSent;
}
