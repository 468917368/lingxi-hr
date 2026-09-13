package com.lingxi.user.agent.recommend;

import lombok.Data;

/**
 * 岗位推荐结果
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Data
public class JobRecommendVO {

    /** 岗位ID */
    private Long jobId;

    /** 岗位标题 */
    private String title;

    /** 公司名称 */
    private String companyName;

    /** 城市 */
    private String cityName;

    /** 薪资范围 */
    private String salaryRange;

    /** 匹配度 */
    private Integer matchScore;
}
