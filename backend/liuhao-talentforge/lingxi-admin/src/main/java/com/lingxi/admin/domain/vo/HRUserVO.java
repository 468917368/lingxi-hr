package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * HR用户VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class HRUserVO {

    /** 用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 手机号 */
    private String phone;

    /** 部门 */
    private String department;

    /** 职位 */
    private String position;

    /** 邮箱 */
    private String email;

    /** 状态 */
    private String status;
}
