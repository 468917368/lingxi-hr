package com.lingxi.user.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Size;

/**
 * 管理员登录请求
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class AdminLoginRequest {

    @NotBlank(message = "账号不能为空")
    @Size(min = 4, max = 32, message = "账号长度须在4-32字符之间")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须在8-20位之间")
    private String password;
}
