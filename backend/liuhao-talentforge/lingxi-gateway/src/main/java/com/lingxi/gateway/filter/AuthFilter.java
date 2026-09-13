package com.lingxi.gateway.filter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.lingxi.common.domain.Result;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

/**
 * 鉴权过滤器（Gateway层）
 * <p>
 * 所有请求经过Gateway时的第一个鉴权关卡，负责：
 * 1. 白名单放行（登录、验证码、内部接口、WebSocket等不需要Token）
 * 2. 从Authorization头解析JWT Token
 * 3. 验证Token有效性（签名+过期+Redis中是否存在）
 * 4. 将用户信息注入请求头传递给下游业务服务
 * </p>
 * <p>
 * 执行顺序：order=-100，在RequestLogFilter(-200)之后、SentinelFilter(-1)之前。
 * </p>
 * <p>
 * 与业务服务的AuthInterceptor的区别：
 * - AuthFilter：Gateway层，校验Token有效性，注入请求头
 * - AuthInterceptor：业务服务层，读取请求头，校验注解权限
 * </p>
 *
 * @author 成员A
 * @since 2026-07-31
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthFilter implements GlobalFilter, Ordered {

    /** 过滤器优先级（-100，在RequestLogFilter之后、SentinelFilter之前） */
    private static final int FILTER_ORDER = -100;

    /** JWT签名密钥（从配置文件读取） */
    @Value("${jwt.secret}")
    private String jwtSecret;

    /** Redis客户端（用于校验Token是否在Redis中） */
    private final StringRedisTemplate redisTemplate;

    /** 路径匹配器（支持Ant风格通配符） */
    private final AntPathMatcher pathMatcher = new AntPathMatcher();

    /** JSON序列化器 */
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 白名单路径列表
     * <p>
     * 这些路径不需要Token，直接放行：
     * - 认证相关：登录、注册、验证码、刷新Token
     * - 管理员登录
     * - HR注册
     * - Agent工具接口（百宝箱工作流回调）
     * - 内部接口（/internal/**，服务间调用用服务身份认证）
     * - WebSocket（握手时校验Token）
     * - 监控和文档
     * </p>
     */
    private static final List<String> WHITE_LIST = Arrays.asList(
            "/api/v1/auth/send-code",
            "/api/v1/auth/register",
            "/api/v1/auth/login",
            "/api/v1/auth/login-by-password",
            "/api/v1/auth/refresh-token",
            "/api/v1/admin-auth/login",
            "/api/v1/hr/register",
            "/api/v1/agent/tools/**",  // Agent工具接口（百宝箱工作流调用）
            "/internal/**",
            "/ws/**",
            "/actuator/**",
            "/doc.html",
            "/swagger-resources/**",
            "/webjars/**"
    );

    /**
     * 过滤请求，校验Token并注入用户信息
     * <p>
     * 执行流程：
     * 1. 白名单放行 → 不需要Token的路径直接放行
     * 2. 获取Token → 从Authorization: Bearer xxx头提取
     * 3. 解析Token → 验证签名和过期时间
     * 4. Redis校验 → 确认Token未被清除（改密码/登出会清除Redis中的Token）
     * 5. 注入请求头 → 将用户信息注入X-User-Id等请求头，供下游服务使用
     * </p>
     *
     * @param exchange 请求交换对象
     * @param chain    过滤器链
     * @return Mono<Void>
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        ServerHttpRequest request = exchange.getRequest();
        String path = request.getURI().getPath();

        // ① 白名单放行（登录、注册、内部接口等不需要Token）
        if (isWhiteListed(path)) {
            return chain.filter(exchange);
        }

        // ② 获取Token（从Authorization头）
        String authHeader = request.getHeaders().getFirst(HttpHeaders.AUTHORIZATION);
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return unauthorizedResponse(exchange, "未携带Token");
        }

        String token = authHeader.substring(7);  // 去掉 "Bearer " 前缀

        try {
            // ③ 解析Token（验证签名 + 过期时间）
            SecretKey key = Keys.hmacShaKeyFor(jwtSecret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parserBuilder()
                    .setSigningKey(key)
                    .build()
                    .parseClaimsJws(token)
                    .getBody();

            // ④ 校验Token是否在Redis中（密码修改后Token会被清除）
            Object userIdObj = claims.get("userId");
            if (userIdObj == null) {
                return unauthorizedResponse(exchange, "Token无效");
            }
            String userId = String.valueOf(userIdObj);
            String tokenKey = "user:token:" + userId;

            // 注意：Redis操作是阻塞的，WebFlux中需要调度到boundedElastic线程池
            return Mono.fromCallable(() -> {
                String storedToken = redisTemplate.opsForValue().get(tokenKey);
                return storedToken != null && storedToken.equals(token);
            }).subscribeOn(Schedulers.boundedElastic()).flatMap(isValid -> {
                if (!isValid) {
                    return unauthorizedResponse(exchange, "Token已失效，请重新登录");
                }

                // ⑤ 将用户信息注入请求头，供下游业务服务使用
                ServerHttpRequest.Builder requestBuilder = request.mutate()
                        .header("X-User-Id", String.valueOf(claims.get("userId")))
                        .header("X-User-Role", String.valueOf(claims.get("role")))
                        .header("X-Phone", String.valueOf(claims.get("phone")));

                // 企业ID（HR/面试官有值）
                Object companyId = claims.get("companyId");
                if (companyId != null) {
                    requestBuilder.header("X-Company-Id", String.valueOf(companyId));
                }

                // 认证状态（HR有值：APPROVED/PENDING/REJECTED）
                Object certStatus = claims.get("certStatus");
                if (certStatus != null) {
                    requestBuilder.header("X-Certification-Status", String.valueOf(certStatus));
                }

                log.debug("Token校验通过: userId={}, role={}", claims.get("userId"), claims.get("role"));

                // 放行到下游服务
                return chain.filter(exchange.mutate().request(requestBuilder.build()).build());
            });
        } catch (Exception e) {
            log.warn("Token校验失败: path={}, error={}", path, e.getMessage());
            return unauthorizedResponse(exchange, "Token无效或已过期");
        }
    }

    /**
     * 判断请求路径是否在白名单中
     *
     * @param path 请求路径（如 /api/v1/auth/login）
     * @return true=白名单放行, false=需要校验Token
     */
    private boolean isWhiteListed(String path) {
        return WHITE_LIST.stream().anyMatch(pattern -> pathMatcher.match(pattern, path));
    }

    /**
     * 返回401未授权响应
     * <p>
     * 使用项目统一的Result格式返回JSON错误信息。
     * </p>
     *
     * @param exchange 请求交换对象
     * @param message  错误消息
     * @return Mono<Void>
     */
    private Mono<Void> unauthorizedResponse(ServerWebExchange exchange, String message) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(HttpStatus.UNAUTHORIZED);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);

        Result<?> result = Result.error(401, message);

        try {
            String body = objectMapper.writeValueAsString(result);
            DataBuffer buffer = response.bufferFactory().wrap(body.getBytes(StandardCharsets.UTF_8));
            return response.writeWith(Mono.just(buffer));
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    /**
     * 过滤器优先级
     * <p>
     * 执行顺序（数字越小越先执行）：
     * - RequestLogFilter: -200（最先，记录日志）
     * - AuthFilter: -100（其次，鉴权）
     * - SentinelGatewayFilter: -1（最后，限流）
     * </p>
     */
    @Override
    public int getOrder() {
        return FILTER_ORDER;
    }
}
