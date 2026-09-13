package com.lingxi.admin.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 拒绝审核DTO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class RejectDTO {

    /**
     * 拒绝原因
     */
    @NotBlank(message = "拒绝原因不能为空")
    private String reason;
}
