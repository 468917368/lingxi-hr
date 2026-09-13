package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * HC 释放回退请求（对齐 lingxi-job HcReleaseRequest 契约，2026-08-07 修正）
 * <p>B 侧必填：companyId、offerId、reason；reason 白名单：REJECTED/EXPIRED/NOT_ONBOARDED/D_PERSIST_FAILED。</p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HcReleaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID（必须与岗位所属企业一致） */
    private Long companyId;

    /** Offer业务ID */
    private Long offerId;

    /** 释放原因：REJECTED/EXPIRED/NOT_ONBOARDED/D_PERSIST_FAILED */
    private String reason;
}
