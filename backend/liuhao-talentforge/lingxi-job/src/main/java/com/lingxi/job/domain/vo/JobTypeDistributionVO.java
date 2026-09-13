package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 岗位类型分布统计项
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobTypeDistributionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位类型（未归类为 UNKNOWN） */
    private String jobType;

    /** 岗位数量 */
    private Long count;
}
