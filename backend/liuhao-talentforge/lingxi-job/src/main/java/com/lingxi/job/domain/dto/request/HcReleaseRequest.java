package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * HC 释放回退请求
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class HcReleaseRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID（必须与岗位所属企业一致） */
    @NotNull(message = "企业ID不能为空")
    private Long companyId;

    /** Offer业务ID */
    @NotNull(message = "OfferID不能为空")
    private Long offerId;

    /** 释放原因：REJECTED/EXPIRED/NOT_ONBOARDED/D_PERSIST_FAILED */
    @NotBlank(message = "释放原因不能为空")
    private String reason;
}
