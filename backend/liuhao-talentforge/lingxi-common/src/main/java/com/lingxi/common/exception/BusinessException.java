package com.lingxi.common.exception;

import lombok.Getter;

/**
 * 业务异常
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public class BusinessException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** 错误码 */
    private final int code;

    /**
     * 通过异常接口构造
     */
    public BusinessException(BaseExceptionInterface exception) {
        super(exception.getErrorMessage());
        this.code = exception.getErrorCode();
    }

    /**
     * 通过错误码和消息构造
     */
    public BusinessException(int code, String message) {
        super(message);
        this.code = code;
    }

    /**
     * 通过错误码、消息和原因构造
     */
    public BusinessException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }
}
