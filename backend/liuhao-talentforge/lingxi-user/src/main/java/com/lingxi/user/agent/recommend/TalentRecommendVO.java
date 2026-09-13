package com.lingxi.user.agent.recommend;

import lombok.Data;

/**
 * 人才推荐结果
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Data
public class TalentRecommendVO {

    /** 求职者用户ID */
    private Long userId;

    /** 求职者姓名 */
    private String userName;

    /** 匹配岗位ID */
    private Long jobId;

    /** 匹配岗位标题 */
    private String jobTitle;

    /** 匹配度 */
    private Integer matchScore;

    /** 期望岗位 */
    private String desiredJob;

    /** 期望城市 */
    private String desiredCity;

    /** 期望薪资 */
    private String expectedSalary;
}
