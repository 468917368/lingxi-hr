package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.Email;
import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 个人中心 - 发送邮箱验证码入参（修改邮箱，验证码发送到新邮箱）
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class SendEmailCodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "邮箱不能为空")
    @Email(message = "邮箱格式不正确")
    private String email;
}
