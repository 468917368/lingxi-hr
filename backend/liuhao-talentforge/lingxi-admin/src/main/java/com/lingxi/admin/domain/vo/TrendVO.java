package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 投递趋势VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class TrendVO {

    /** 日期列表 */
    private List<String> dates;

    /** 数量列表 */
    private List<Integer> counts;

    /** 总数 */
    private Integer total;

    /** 平均值 */
    private Integer average;
}
