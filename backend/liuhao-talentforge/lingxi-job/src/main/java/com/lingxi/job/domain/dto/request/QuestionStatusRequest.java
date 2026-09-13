package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 题库启停请求（阶段6.1，action=ENABLE/DISABLE）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class QuestionStatusRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 操作：ENABLE=启用 / DISABLE=停用 */
    @NotBlank(message = "操作类型不能为空")
    private String action;

    /** 乐观锁版本号 */
    @NotNull(message = "版本号不能为空")
    private Integer version;
}
