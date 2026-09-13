package com.lingxi.job.exception;

import com.lingxi.common.domain.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * lingxi-job 模块级异常处理器（Agent 链路专用）
 * <p>
 * {@code @Order(HIGHEST_PRECEDENCE)} 优先于 common {@code GlobalExceptionHandler}
 * （无 @Order，默认最低优先级）。处理 {@link AgentHttpException} 时显式指定
 * {@code Content-Type: application/json}——否则 F-17 请求头 {@code Accept:
 * text/event-stream} 下内容协商会把错误包成 SSE 帧或 406。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Slf4j
@RestControllerAdvice(basePackages = "com.lingxi.job.controller")
@Order(Ordered.HIGHEST_PRECEDENCE)
public class JobAgentExceptionHandler {

    /**
     * 处理建流前 Agent 异常（503/409 等非 200 状态）
     *
     * @param e Agent 异常
     * @return 显式 application/json 的错误响应
     */
    @ExceptionHandler(AgentHttpException.class)
    public ResponseEntity<Result<Void>> handleAgentHttpException(AgentHttpException e) {
        log.warn("Agent 请求异常: HTTP={}, code={}, msg={}",
                e.getHttpStatus().value(), e.getCode(), e.getMessage());
        return ResponseEntity.status(e.getHttpStatus())
                .contentType(MediaType.APPLICATION_JSON)
                .body(Result.error(e.getCode(), e.getMessage()));
    }

    /**
     * 请求体反序列化失败（非法 JSON / 字段类型不匹配，如 keyPoints 既非数组也非字符串）
     * → HTTP 400 + code 400，与 common 的 @RequestBody 校验 400 语义一致（不落入 500 兜底）。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Result<?> handleMessageNotReadable(HttpMessageNotReadableException e) {
        log.warn("请求体解析失败: {}", e.getMessage());
        return Result.badRequest("请求体格式错误");
    }
}
