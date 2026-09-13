package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 系统配置项VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class ConfigVO {

    /** 配置ID */
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;
}
