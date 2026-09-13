package com.lingxi.admin.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 更新状态DTO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class UpdateStatusDTO {

    /**
     * 状态：ACTIVE/DISABLED
     */
    @NotBlank(message = "状态不能为空")
    private String status;
}
