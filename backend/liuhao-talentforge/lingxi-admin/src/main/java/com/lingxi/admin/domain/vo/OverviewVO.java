package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 数据看板概览VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class OverviewVO {

    /** 用户总数 */
    private Integer userCount;
    /** 今日新增用户 */
    private Integer userCountToday;
    /** 用户增长率 */
    private Double userCountGrowth;

    /** 企业总数 */
    private Integer enterpriseCount;
    /** 今日新增企业 */
    private Integer enterpriseCountToday;
    /** 企业增长率 */
    private Double enterpriseCountGrowth;

    /** 岗位总数 */
    private Integer positionCount;
    /** 今日新增岗位 */
    private Integer positionCountToday;
    /** 岗位增长率 */
    private Double positionCountGrowth;

    /** 投递总数 */
    private Integer applicationCount;
    /** 今日新增投递 */
    private Integer applicationCountToday;
    /** 投递增长率 */
    private Double applicationCountGrowth;

    /** 面试总数 */
    private Integer interviewCount;
    /** 今日新增面试 */
    private Integer interviewCountToday;
    /** 面试增长率 */
    private Double interviewCountGrowth;

    /** Offer总数 */
    private Integer offerCount;
    /** 今日新增Offer */
    private Integer offerCountToday;
    /** Offer增长率 */
    private Double offerCountGrowth;
}
