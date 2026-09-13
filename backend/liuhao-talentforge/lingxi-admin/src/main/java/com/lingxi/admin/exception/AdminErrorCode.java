package com.lingxi.admin.exception;

import com.lingxi.common.exception.BaseExceptionInterface;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 管理服务业务错误码枚举（5100-5999）
 * <p>
 * 管理员登录相关错误码（5001-5009）定义在 lingxi-user 的 AdminLoginErrorCode 中，
 * 本类仅包含管理后台业务错误码（审核、用户管理等）。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Getter
@AllArgsConstructor
public enum AdminErrorCode implements BaseExceptionInterface {

    // ==================== 审核模块 5100-5199 ====================
    CERT_NOT_FOUND(5100, "认证申请不存在"),
    CERT_ALREADY_REVIEWED(5101, "该申请已审核"),

    // ==================== 用户管理 5200-5299 ====================
    USER_NOT_FOUND(5200, "用户不存在"),
    USER_ALREADY_DISABLED(5201, "用户已被禁用"),

    // ==================== 密码相关 5009-5011 ====================
    CURRENT_PASSWORD_ERROR(5009, "当前密码错误"),
    PASSWORD_MISMATCH(5010, "两次输入的新密码不一致"),
    PASSWORD_FORMAT_INVALID(5011, "新密码格式不符合要求"),

    // ==================== 公告相关 5008 ====================
    ANNOUNCEMENT_ALREADY_ARCHIVED(5008, "该公告已归档"),

    // ==================== 配置相关 5200-5299 ====================
    CONFIG_NOT_FOUND(5200, "配置项不存在"),
    CONFIG_VALUE_INVALID(5201, "配置值格式错误"),
    ;

    private final int errorCode;
    private final String errorMessage;
}
