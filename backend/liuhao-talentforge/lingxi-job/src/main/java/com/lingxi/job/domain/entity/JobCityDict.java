package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 城市主数据字典实体
 * <p>城市名称的唯一真值表，job_post.city_name 按 city_code 回填本表名称。</p>
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Data
public class JobCityDict implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键 */
    private Long id;

    /** 城市编码（市辖区/地级市 6 位码，统一编码标准） */
    private String code;

    /** 城市名称 */
    private String name;

    /** 状态：1启用，0停用 */
    private Integer status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
