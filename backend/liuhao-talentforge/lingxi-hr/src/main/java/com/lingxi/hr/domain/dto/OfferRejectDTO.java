package com.lingxi.hr.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 候选人拒绝 Offer 入参（rejectReason 选填）
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class OfferRejectDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 拒绝原因（选填） */
    private String rejectReason;
}
