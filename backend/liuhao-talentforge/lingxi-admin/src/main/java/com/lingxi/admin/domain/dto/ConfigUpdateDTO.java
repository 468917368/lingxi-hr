package com.lingxi.admin.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 更新配置DTO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class ConfigUpdateDTO {

    /**
     * 配置键
     */
    @NotBlank(message = "配置键不能为空")
    private String configKey;

    /**
     * 配置值
     */
    @NotBlank(message = "配置值不能为空")
    private String configValue;
}
