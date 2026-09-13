package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * 岗位统计数值响应（成员 E 依赖）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobStatisticsValueResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 统计值（岗位总数/今日新增） */
    private Long value;
}
