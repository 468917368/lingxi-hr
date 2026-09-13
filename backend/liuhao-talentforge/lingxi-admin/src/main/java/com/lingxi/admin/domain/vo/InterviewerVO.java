package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 面试官VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class InterviewerVO {

    /** 用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 部门 */
    private String department;

    /** 面试次数 */
    private Integer interviewCount;

    /** 状态 */
    private String status;
}
