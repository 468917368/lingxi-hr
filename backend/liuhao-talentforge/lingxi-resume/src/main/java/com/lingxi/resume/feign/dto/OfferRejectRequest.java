package com.lingxi.resume.feign.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 拒绝 Offer 请求（调 lingxi-hr /internal/offers/reject）
 *
 * @author 成员C
 * @since 2026-08-07
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class OfferRejectRequest {

    /** 投递记录ID（D 侧按 applicationId 定位最新一条 hr_offer） */
    private Long applicationId;

    /** 拒绝原因（可空） */
    private String rejectReason;
}
