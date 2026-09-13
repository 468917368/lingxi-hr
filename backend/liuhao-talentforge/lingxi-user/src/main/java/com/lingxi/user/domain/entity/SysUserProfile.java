package com.lingxi.user.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 用户画像表实体（sys_user_profile）
 * <p>
 * 求职者专用信息，与 sys_user 表一对一
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class SysUserProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID */
    private Long userId;

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

    /** 期望最低月薪 */
    private Integer desiredSalaryMin;

    /** 期望最高月薪 */
    private Integer desiredSalaryMax;

    /** 到岗时间 */
    private LocalDate availableFrom;

    /** 简历公开：0=关闭 1=开启 */
    private Boolean resumePublic;

    /** 盲选模式：0=关闭 1=开启 */
    private Boolean blindMode;

    /** 匹配通知：0=关闭 1=开启 */
    private Boolean matchNotify;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
