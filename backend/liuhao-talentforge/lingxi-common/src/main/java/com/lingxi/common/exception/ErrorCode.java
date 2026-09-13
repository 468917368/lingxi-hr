package com.lingxi.common.exception;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 通用错误码枚举
 * <p>
 * 各模块专属错误码由各模块自行定义枚举：
 * - lingxi-user:    UserErrorCode (1001-1999)
 * - lingxi-job:     JobErrorCode (2001-2999)
 * - lingxi-resume:  ResumeErrorCode (3001-3999)
 * - lingxi-hr:      HrErrorCode (4001-4999)
 * - lingxi-admin:   AdminErrorCode (5001-5999)
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
@AllArgsConstructor
public enum ErrorCode implements BaseExceptionInterface {

    SUCCESS(0, "success"),
    BAD_REQUEST(400, "参数错误"),
    UNAUTHORIZED(401, "未授权"),
    FORBIDDEN(403, "禁止访问"),
    NOT_FOUND(404, "资源不存在"),
    METHOD_NOT_ALLOWED(405, "请求方法不支持"),
    SYSTEM_ERROR(500, "系统内部错误"),
    SERVICE_UNAVAILABLE(503, "服务不可用"),
    CERT_REQUIRED(4031, "企业认证未通过，请先完成企业认证"),
    ;

    private final int errorCode;
    private final String errorMessage;
}
