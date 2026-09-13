package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 管理员详细信息VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AdminProfileVO {

    /** 管理员ID */
    private Long id;

    /** 用户名 */
    private String username;

    /** 姓名 */
    private String name;

    /** 头像URL */
    private String avatar;

    /** 手机号 */
    private String phone;

    /** 邮箱 */
    private String email;

    /** 最后登录时间 */
    private LocalDateTime lastLoginAt;

    /** 最后登录IP */
    private String lastLoginIp;
}
