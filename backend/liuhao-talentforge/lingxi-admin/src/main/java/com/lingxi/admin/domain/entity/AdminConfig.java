package com.lingxi.admin.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 系统配置实体
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Data
public class AdminConfig {

    /** 主键ID */
    private Long id;

    /** 配置键 */
    private String configKey;

    /** 配置值 */
    private String configValue;

    /** 配置说明 */
    private String description;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
