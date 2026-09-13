package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * HC 预冻结请求
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class HcReserveRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID（必须与岗位所属企业一致） */
    @NotNull(message = "企业ID不能为空")
    private Long companyId;

    /** Offer业务ID */
    @NotNull(message = "OfferID不能为空")
    private Long offerId;

    /** 候选人用户ID */
    @NotNull(message = "候选人ID不能为空")
    private Long candidateId;
}
