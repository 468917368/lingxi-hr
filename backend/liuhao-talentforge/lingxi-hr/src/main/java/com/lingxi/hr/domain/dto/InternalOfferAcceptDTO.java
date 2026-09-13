package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * C 端投递页反向接受 Offer 入参（C 仅持有投递记录ID）
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class InternalOfferAcceptDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID（C 侧 resume_application.id；D 侧按此定位 SENT Offer） */
    @NotNull(message = "applicationId 不能为空")
    private Long applicationId;
}
