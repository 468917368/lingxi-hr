package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 岗位列表项VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class JobVO {

    /** 岗位ID */
    private Long id;

    /** 岗位名称 */
    private String title;

    /** 城市 */
    private String city;

    /** 状态 */
    private String status;

    /** 薪资范围（格式化字符串，如 "15K-25K"） */
    private String salary;

    /** 投递数 */
    private Integer applyCount;

    /** HC信息：如 "5/2" */
    private String headcount;

    /** 发布时间 */
    private LocalDateTime publishTime;
}
