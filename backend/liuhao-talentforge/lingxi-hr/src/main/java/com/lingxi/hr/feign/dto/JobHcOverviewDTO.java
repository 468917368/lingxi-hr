package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 岗位 HC 概览 DTO（映射 lingxi-job InternalJobValidationResponse，2026-08-07 新增）
 * <p>供 hc-overview 单岗/公司级聚合 + 发起 Offer 薪资软提示使用。</p>
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class JobHcOverviewDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String title;

    /** 岗位状态：DRAFT/PUBLISHED/PAUSED/CLOSED */
    private String status;

    /** 是否已删除 */
    private Boolean deleted;

    /** HC 总量 */
    private Integer totalHc;

    /** 已预冻结 HC */
    private Integer reservedHc;

    /** 已确认占用 HC */
    private Integer confirmedHc;

    /** 可用 HC */
    private Integer availableHc;

    /** 薪资下限（分） */
    private Long salaryMinAmount;

    /** 薪资上限（分） */
    private Long salaryMaxAmount;

    /** 薪资是否面议 */
    private Boolean salaryNegotiable;
}
