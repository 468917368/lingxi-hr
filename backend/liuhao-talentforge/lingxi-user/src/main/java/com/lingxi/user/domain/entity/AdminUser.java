package com.lingxi.user.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 管理员账号表实体（admin_user）
 * <p>
 * 管理员独立账号表，与 sys_user 分离。
 * 管理员登录时查询此表。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class AdminUser implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 管理员ID */
    private Long id;

    /** 登录用户名 */
    private String username;

    /** 密码哈希 */
    private String passwordHash;

    /** 管理员姓名 */
    private String name;

    /** 状态：ACTIVE/DISABLED */
    private String status;

    /** 首次登录标记：0=否 1=是（默认密码未修改） */
    private Integer firstLogin;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
