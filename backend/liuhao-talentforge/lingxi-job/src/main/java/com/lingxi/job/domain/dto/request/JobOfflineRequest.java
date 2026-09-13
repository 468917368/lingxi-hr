package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 管理员违规下架岗位请求
 * <p>第一版 reason 固定 VIOLATION（Service 层校验）；remark 1~500 字；version 乐观锁版本必填。</p>
 *
 * @author lingxi-team
 * @since 2026-08-04
 */
@Data
public class JobOfflineRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 下架原因（本版本固定 VIOLATION） */
    @NotBlank(message = "下架原因不能为空")
    @Size(max = 32, message = "下架原因长度不能超过32")
    private String reason;

    /** 管理员下架说明（1~500 字） */
    @NotBlank(message = "下架说明不能为空")
    @Size(max = 500, message = "下架说明长度不能超过500")
    private String remark;

    /** 岗位乐观锁版本 */
    @NotNull(message = "岗位版本不能为空")
    private Integer version;
}
