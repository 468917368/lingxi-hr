package com.lingxi.user.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 手机号+密码登录请求
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class PasswordLoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Phone
    private String phone;

    @NotBlank(message = "密码不能为空")
    private String password;
}
