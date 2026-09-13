package com.lingxi.resume.domain.vo;

import lombok.Data;

import java.util.List;

/**
 * 投递趋势视图对象（管理后台数据看板）
 *
 * <p>对齐契约：《API需求-成员C-lingxi-resume.md》§2.2，
 * dates/counts 同下标对应，total/average 为时间段内汇总，todayCount 供看板卡片展示。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class ApplicationTrendVO {

    /** 日期列表（yyyy-MM-dd，升序） */
    private List<String> dates;

    /** 对应日期的投递数 */
    private List<Integer> counts;

    /** 时间段内投递总数 */
    private Integer total;

    /** 日均投递数 */
    private Integer average;

    /** 今日投递数 */
    private Integer todayCount;
}
