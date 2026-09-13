package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 岗位类型分布VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class JobTypeVO {

    /** 类型名称 */
    private String type;

    /** 数量 */
    private Integer count;
}
