package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 管理员简要信息VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AdminUserVO {

    /** 管理员ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 姓名 */
    private String name;

    /** 角色 */
    private String role;
}
