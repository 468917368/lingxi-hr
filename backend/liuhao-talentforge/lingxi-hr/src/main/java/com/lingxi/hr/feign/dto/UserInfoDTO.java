package com.lingxi.hr.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户信息 DTO（对应 lingxi-user UserInfoVO，仅取 HR 注册所需字段）
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 手机号 */
    private String phone;

    /** 姓名 */
    private String name;

    /** 角色 */
    private String role;

    /** 头像 */
    private String avatar;

    /** 邮箱 */
    private String email;

    /** 企业ID（HR/面试官有值，注册后为null） */
    private Long companyId;
}
