package com.lingxi.gateway.filter;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * 请求日志过滤器
 * <p>
 * 功能：
 * 1. 生成请求ID并注入请求头
 * 2. 记录请求开始日志（方法、路径、来源IP）
 * 3. 记录请求结束日志（耗时、状态码）
 * 4. 慢查询告警
 * </p>
 *
 * @author 成员A
 * @since 2026-07-31
 */
@Slf4j
@Component
public class RequestLogFilter implements GlobalFilter, Ordered {

    /** 过滤器优先级 */
    private static final int FILTER_ORDER = -200;

    /** 请求ID请求头 */
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    /** 慢查询阈值（毫秒） */
    private static final long SLOW_QUERY_THRESHOLD_MS = 3000;

    /**
     * 过滤请求，注入请求ID并记录日志
     *
     * @param exchange 请求交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String requestId = UUID.randomUUID().toString().replace("-", "");

        // 添加请求ID到请求头
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(REQUEST_ID_HEADER, requestId)
                .build();

        long startTime = System.currentTimeMillis();
        String path = request.getURI().getPath();
        String method = request.getMethod() != null ? request.getMethod().name() : "UNKNOWN";
        String remoteAddr = request.getRemoteAddress() != null ?
                request.getRemoteAddress().getAddress().getHostAddress() : "unknown";

        log.info("请求开始: requestId={}, method={}, path={}, remoteAddr={}",
                requestId, method, path, remoteAddr);

        return chain.filter(exchange.mutate().request(mutatedRequest).build())
                .then(Mono.fromRunnable(() -> {
                    long duration = System.currentTimeMillis() - startTime;
                    HttpStatus httpStatus = exchange.getResponse().getStatusCode();
                    int statusCode = httpStatus != null ? httpStatus.value() : 0;

                    if (duration > SLOW_QUERY_THRESHOLD_MS) {
                        log.warn("请求慢查询: requestId={}, duration={}ms, status={}",
                                requestId, duration, statusCode);
                    } else {
                        log.info("请求结束: requestId={}, duration={}ms, status={}",
                                requestId, duration, statusCode);
                    }
                }));
    }

    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
