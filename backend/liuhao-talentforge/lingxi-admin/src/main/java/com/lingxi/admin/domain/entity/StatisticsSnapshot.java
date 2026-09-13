package com.lingxi.admin.domain.entity;

import lombok.Data;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 统计快照实体
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Data
public class StatisticsSnapshot {

    /** 主键ID */
    private Long id;

    /** 快照日期 */
    private LocalDate snapshotDate;

    /** 用户总数 */
    private Integer userCount;

    /** 企业总数 */
    private Integer enterpriseCount;

    /** 岗位总数 */
    private Integer positionCount;

    /** 投递总数 */
    private Integer applicationCount;

    /** 面试总数 */
    private Integer interviewCount;

    /** Offer总数 */
    private Integer offerCount;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
