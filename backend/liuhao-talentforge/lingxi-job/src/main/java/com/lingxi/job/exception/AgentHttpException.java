package com.lingxi.job.exception;

import lombok.Getter;
import org.springframework.http.HttpStatus;

/**
 * Agent 链路建流前异常（携带 HTTP 状态码 + 业务码）
 * <p>
 * 建流前错误需要非 200 的 HTTP 状态：并发满 → 503 + 2304、投递状态非法 → 409 + 2305。
 * 由模块级 {@link JobAgentExceptionHandler} 捕获（{@code @Order} 优先于 common
 * {@code GlobalExceptionHandler}），显式返回 {@code application/json}。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Getter
public class AgentHttpException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    /** HTTP 状态码 */
    private final HttpStatus httpStatus;

    /** 业务错误码 */
    private final int code;

    public AgentHttpException(HttpStatus httpStatus, int code, String message) {
        super(message);
        this.httpStatus = httpStatus;
        this.code = code;
    }

    public AgentHttpException(HttpStatus httpStatus, JobErrorCode errorCode) {
        super(errorCode.getErrorMessage());
        this.httpStatus = httpStatus;
        this.code = errorCode.getErrorCode();
    }
}
