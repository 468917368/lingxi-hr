package com.lingxi.resume.domain.dto;

import lombok.Data;

/**
 * 拒绝 Offer 请求（C端，可选 body：rejectReason 透传至 lingxi-hr）
 *
 * @author 成员C
 * @since 2026-08-07
 */
@Data
public class OfferRejectDTO {

    /** 拒绝原因（可选，前端当前不传） */
    private String rejectReason;
}
