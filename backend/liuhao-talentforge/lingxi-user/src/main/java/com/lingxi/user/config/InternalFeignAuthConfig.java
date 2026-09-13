package com.lingxi.user.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 内部服务认证 Feign 拦截器
 * 为调用其他内部服务的请求注入服务身份头
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Configuration
public class InternalFeignAuthConfig {

    private static final String CALLER_SERVICE = "lingxi-user";
    private static final String SERVICE_TOKEN = "user-token-dev";

    @Bean
    public RequestInterceptor internalServiceAuthInterceptor() {
        return (RequestTemplate template) -> {
            template.header("X-Caller-Service", CALLER_SERVICE);
            template.header("X-Service-Token", SERVICE_TOKEN);
        };
    }
}
