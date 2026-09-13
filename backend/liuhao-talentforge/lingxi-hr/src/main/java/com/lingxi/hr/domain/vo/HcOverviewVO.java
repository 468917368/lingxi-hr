package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * HC 概览出参（系分 5.5.4，jobId 可空=公司级聚合，2026-08-07）
 *
 * @author 成员D
 * @since 2026-08-07
 */
@Data
public class HcOverviewVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID（公司级聚合时为 null） */
    private Long jobId;

    /** 岗位名称（公司级聚合时为"全公司"） */
    private String jobTitle;

    /** HC 总量 */
    private Integer totalHc;

    /** 已预冻结 HC */
    private Integer reservedHc;

    /** 已确认占用 HC */
    private Integer confirmedHc;

    /** 可用 HC */
    private Integer availableHc;
}
