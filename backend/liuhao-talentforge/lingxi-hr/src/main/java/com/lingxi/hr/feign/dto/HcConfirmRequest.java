package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * HC 确认占用请求（对齐 lingxi-job HcConfirmRequest 契约，2026-08-07 新增）
 * <p>B 侧必填：companyId、offerId。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class HcConfirmRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID（必须与岗位所属企业一致） */
    private Long companyId;

    /** Offer业务ID */
    private Long offerId;
}
