package com.lingxi.user.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 手机号+验证码登录请求
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class LoginRequest {

    @NotBlank(message = "手机号不能为空")
    @Phone
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;
}
