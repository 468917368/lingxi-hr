package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 修改密码 Feign 入参（调用 lingxi-user {@code POST /api/v1/auth/change-password}）
 * <p>仅透传旧/新密码；confirmPassword 由 D 侧校验一致性，不透传。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class ChangePasswordFeignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String oldPassword;
    private String newPassword;
}
