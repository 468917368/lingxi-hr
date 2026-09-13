package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 求职者详情VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CandidateDetailVO {

    /** 用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 城市 */
    private String city;

    /** 求职状态 */
    private String jobStatus;

    /** 经验 */
    private String experience;

    /** 期望岗位 */
    private String expectedPosition;

    /** 期望城市 */
    private String expectedCity;

    /** 期望薪资 */
    private String expectedSalary;

    /** 状态 */
    private String status;

    /** 注册时间 */
    private LocalDateTime registerTime;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 简历数 */
    private Integer resumeCount;

    /** 投递数 */
    private Integer applicationCount;
}
