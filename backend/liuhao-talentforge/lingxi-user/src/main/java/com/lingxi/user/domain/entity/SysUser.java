package com.lingxi.user.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户表实体
 *
 * @author 成员A
 * @since 2026-07-31
 */
@Data
public class SysUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 手机号（登录账号） */
    private String phone;

    /** 密码哈希（BCrypt加密，注册时必填） */
    private String passwordHash;

    /** 真实姓名 */
    private String name;

    /** 姓名最后修改时间 */
    private LocalDateTime nameUpdatedAt;

    /** 头像URL */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 角色：CANDIDATE/HR/INTERVIEWER/ADMIN */
    private String role;

    /** 状态：ACTIVE/DISABLED */
    private String status;

    /** 首次登录标记：0=否 1=是（默认密码未修改） */
    private Integer firstLogin;

    /** 注册时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
