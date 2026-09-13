package com.lingxi.admin.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员实体
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Data
public class AdminUser {

    /** 管理员ID */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码哈希（BCrypt加密） */
    private String password;

    /** 管理员姓名 */
    private String name;

    /** 头像URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 状态：ACTIVE=正常 / DISABLED=禁用 */
    private String status;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最后登录IP */
    private String lastLoginIp;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
