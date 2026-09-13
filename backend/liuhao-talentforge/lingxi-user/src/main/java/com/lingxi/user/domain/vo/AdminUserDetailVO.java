package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 管理端用户详情VO（供成员E调用）
 *
 * @author 成员A
 * @since 2026-08-04
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdminUserDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 手机号（不脱敏，内部接口） */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：ACTIVE/DISABLED */
    private String status;

    /** 注册时间 */
    private String createdAt;

    /** 最后登录时间 */
    private String lastLoginAt;

    /** 工作经验 */
    private String experience;

    /** 期望岗位 */
    private String desiredJob;

    /** 期望城市 */
    private String desiredCity;

    /** 期望最低薪资（元/月） */
    private Integer desiredSalaryMin;

    /** 期望最高薪资（元/月） */
    private Integer desiredSalaryMax;

    // TODO: 需要 Feign 调用 lingxi-resume 获取
    // private Integer resumeCount;
    // private Integer applicationCount;
}
