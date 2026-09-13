package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * HC 预冻结请求（对齐 lingxi-job HcReserveRequest 契约，2026-08-07 修正）
 * <p>B 侧必填：companyId（必须与岗位所属企业一致）、offerId（业务ID）、candidateId。</p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HcReserveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID（必须与岗位所属企业一致） */
    private Long companyId;

    /** Offer业务ID */
    private Long offerId;

    /** 候选人用户ID */
    private Long candidateId;
}
