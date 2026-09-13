package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 企业岗位列表项（映射 lingxi-job InternalCompanyJobVO，2026-08-07 新增）
 * <p>供 hc-overview 公司级聚合：取本企业全部 jobId 后逐个查 HC。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class CompanyJobDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 岗位状态：DRAFT/PUBLISHED/PAUSED/CLOSED（调用方自行筛选） */
    private String status;

    /** 可用 HC（total-reserved-confirmed） */
    private Integer availableHc;
}
