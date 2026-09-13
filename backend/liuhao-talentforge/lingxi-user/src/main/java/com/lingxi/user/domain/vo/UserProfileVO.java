package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * 用户画像视图对象
 * <p>
 * 嵌套在 UserInfoVO 中，对齐系统分析文档
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 性别：MALE/FEMALE */
    private String gender;

    /** 所在城市 */
    private String city;

    /** 工作年限：FRESH/1-3/3-5/5-10/10+ */
    private String workYears;

    /** 最高学历：COLLEGE/BACHELOR/MASTER/PHD */
    private String education;

    /** 求职状态：JOB_SEEKING/EMPLOYED_LOOKING/NOT_LOOKING */
    private String jobStatus;

    /** 期望岗位 */
    private String desiredJob;

    /** 期望城市 */
    private String desiredCity;

    /** 期望最低薪资 */
    private Integer desiredSalaryMin;

    /** 期望最高薪资 */
    private Integer desiredSalaryMax;

    /** 到岗时间 */
    private LocalDate availableFrom;

    /** 简历是否公开 */
    private Boolean resumePublic;

    /** 盲选模式 */
    private Boolean blindMode;
}
