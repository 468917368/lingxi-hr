package com.lingxi.user.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 用户注册请求
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "手机号不能为空")
    @Phone
    private String phone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;

    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须在8-20位之间")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z0-9]).{8,20}$",
             message = "密码须包含大小写字母、数字和特殊字符")
    private String password;

    @NotBlank(message = "姓名不能为空")
    @Size(min = 1, max = 32, message = "姓名长度须在1-32字符之间")
    private String name;

    /** 角色：CANDIDATE（求职者）/ HR，注册时选择 */
    @NotBlank(message = "请选择角色")
    private String role;
}
