package com.lingxi.common.config;

import feign.Request;
import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.util.StringUtils;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.util.concurrent.TimeUnit;

/**
 * Feign 配置
 * <p>
 * 规范10.3节：
 * - 连接超时：5秒
 * - 读取超时：10秒
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Configuration
public class FeignConfig {

    /**
     * 请求超时配置
     */
    @Bean
    public Request.Options requestOptions() {
        return new Request.Options(
                5, TimeUnit.SECONDS,   // 连接超时
                10, TimeUnit.SECONDS,  // 读取超时
                true                   // 跟随重定向
        );
    }

    /**
     * Token 透传拦截器
     * <p>
     * 将当前请求线程的 {@code Authorization} 头透传给 Feign 调用，
     * 使服务间调用以调用方身份访问对端接口（如 HR 服务以求职者身份调简历服务的用户接口）。
     * </p>
     * <ul>
     *   <li>无请求上下文（MQ 消费者/定时任务/{@code @Async} 线程）时跳过，不附加任何头</li>
     *   <li>已在 Feign 接口上手动设置过 {@code Authorization} 时跳过，保留显式指定</li>
     * </ul>
     */
    @Bean
    public RequestInterceptor tokenRelayInterceptor() {
        return template -> {
            // 已手动设置过 Authorization，则不覆盖
            boolean hasAuth = template.headers().keySet().stream()
                    .anyMatch(h -> h.equalsIgnoreCase(HttpHeaders.AUTHORIZATION));
            if (hasAuth) {
                return;
            }
            // 无请求上下文（异步线程）跳过
            ServletRequestAttributes attrs =
                    (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
            if (attrs == null) {
                return;
            }
            String auth = attrs.getRequest().getHeader(HttpHeaders.AUTHORIZATION);
            if (auth != null && !auth.isEmpty()) {
                template.header(HttpHeaders.AUTHORIZATION, auth);
            }
        };
    }

    /**
     * 服务身份拦截器
     * <p>
     * 为 Feign 调用附加 {@code X-Caller-Service} / {@code X-Service-Token} 头，
     * 用于通过对端服务的 {@code /internal/**} 服务身份认证（如 lingxi-job 的
     * {@code InternalServiceAuthInterceptor}）。
     * </p>
     * <p>各服务在自身 application.yml 配置 {@code service-auth.caller} 与
     * {@code service-auth.token}（与对端 service-auth.tokens 中本服务名对应的值一致）。</p>
     */
    @Bean
    public RequestInterceptor serviceAuthInterceptor(
            @Value("${service-auth.caller:}") String caller,
            @Value("${service-auth.token:}") String token) {
        return template -> {
            if (StringUtils.hasText(caller)) {
                template.header("X-Caller-Service", caller);
            }
            if (StringUtils.hasText(token)) {
                template.header("X-Service-Token", token);
            }
        };
    }
}
