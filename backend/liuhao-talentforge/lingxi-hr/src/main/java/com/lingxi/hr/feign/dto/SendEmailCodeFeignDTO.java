package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 发送邮箱验证码 Feign 入参（调用 lingxi-user {@code POST /api/v1/user/email/send-code}）
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class SendEmailCodeFeignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
}
