package com.lingxi.gateway.handler;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.reactive.error.ErrorWebExceptionHandler;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.nio.charset.StandardCharsets;

/**
 * Gateway 全局异常处理器
 * <p>
 * Gateway 基于 WebFlux，使用 ErrorWebExceptionHandler 而非 @RestControllerAdvice
 * </p>
 *
 * @author 成员A
 * @since 2026-07-31
 */
@Slf4j
@Order(-1)
@Component
public class GatewayExceptionHandler implements ErrorWebExceptionHandler {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public Mono<Void> handle(ServerWebExchange exchange, Throwable ex) {
        ServerHttpResponse response = exchange.getResponse();

        // 防止响应已提交后再次写入
        if (response.isCommitted()) {
            return Mono.error(ex);
        }

        String path = exchange.getRequest().getURI().getPath();
        log.error("Gateway异常: path={}, error={}", path, ex.getMessage());

        // 根据异常类型设置不同的状态码
        Result<?> result;
        HttpStatus httpStatus;

        if (isUnauthorizedException(ex)) {
            result = Result.error(ErrorCode.UNAUTHORIZED);
            httpStatus = HttpStatus.UNAUTHORIZED;
        } else if (isForbiddenException(ex)) {
            result = Result.error(ErrorCode.FORBIDDEN);
            httpStatus = HttpStatus.FORBIDDEN;
        } else {
            result = Result.error(ErrorCode.SYSTEM_ERROR);
            httpStatus = HttpStatus.INTERNAL_SERVER_ERROR;
        }

        response.setStatusCode(httpStatus);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        try {
            String body = objectMapper.writeValueAsString(result);
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (JsonProcessingException e) {
            return Mono.error(e);
        }
    }

    /**
     * 判断是否为未登录异常
     */
    private boolean isUnauthorizedException(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("未登录")
                || message.contains("Token")
                || message.contains("token")
                || message.contains("Unauthorized")
                || message.contains("401");
    }

    /**
     * 判断是否为无权限异常
     */
    private boolean isForbiddenException(Throwable ex) {
        String message = ex.getMessage();
        if (message == null) {
            return false;
        }
        return message.contains("无权限")
                || message.contains("Forbidden")
                || message.contains("403");
    }
}
