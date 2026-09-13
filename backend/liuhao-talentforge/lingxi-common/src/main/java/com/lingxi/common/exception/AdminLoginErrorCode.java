package com.lingxi.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 管理员登录相关错误码枚举（5001-5099）
 * <p>
 * 管理员登录相关错误码定义在 lingxi-common，
 * 管理后台业务错误码保留在 lingxi-admin 的 AdminErrorCode。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Getter
@AllArgsConstructor
public enum AdminLoginErrorCode implements BaseExceptionInterface {

    // ==================== 管理员登录 5001-5099 ====================
    LOGIN_FAILED(5001, "账号或密码错误"),
    ADMIN_DISABLED(5002, "账号已被禁用"),
    PASSWORD_WEAK(5003, "密码强度不足"),
    FIRST_LOGIN(5004, "请先修改初始密码"),
    ;

    private final int errorCode;
    private final String errorMessage;
}
