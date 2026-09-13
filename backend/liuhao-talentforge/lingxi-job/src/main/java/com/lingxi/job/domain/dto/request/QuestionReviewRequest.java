package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 题库审核请求（阶段6.1，action=APPROVE/REJECT）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class QuestionReviewRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作：APPROVE=通过 / REJECT=拒绝 */
    @NotBlank(message = "审核操作不能为空")
    private String action;

    /** 拒绝原因（REJECT 时必填） */
    private String reason;

    /** 乐观锁版本号 */
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
