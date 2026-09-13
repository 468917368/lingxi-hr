package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 注册请求 DTO（透传给 lingxi-user AuthController#register）
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class RegisterRequestDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号 */
    private String phone;

    /** 短信验证码 */
    private String code;

    /** 密码 */
    private String password;

    /** 姓名 */
    private String name;

    /** 角色：CANDIDATE/HR */
    private String role;
}
