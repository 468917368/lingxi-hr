package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 验证并更新邮箱 Feign 入参（调用 lingxi-user {@code POST /api/v1/user/email/verify}）
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class VerifyEmailFeignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String email;
    private String code;
}
