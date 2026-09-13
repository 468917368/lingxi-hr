package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 岗位状态变更请求
 * <p>action 取值：PUBLISH/CLOSE/REOPEN；profileVersion 仅在 PUBLISH 时必填（Service 按 action 校验）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobStatusActionRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 状态操作：PUBLISH/CLOSE/REOPEN */
    @NotBlank(message = "状态操作不能为空")
    private String action;

    /** 岗位乐观锁版本 */
    @NotNull(message = "岗位版本不能为空")
    private Integer version;

    /** 岗位画像版本（PUBLISH 时必填，与画像当前版本一致校验） */
    private Integer profileVersion;
}
