package com.lingxi.hr.feign;

import feign.RequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

/**
 * 服务身份 Feign 配置（仅 JobFeignClient 使用）
 * <p>
 * 为调用 lingxi-job 的 {@code /internal/**} 接口附加 {@code X-Caller-Service} /
 * {@code X-Service-Token} 头，通过 {@code InternalServiceAuthInterceptor} 服务身份认证。
 * </p>
 * <p>注意：作为 {@code @FeignClient(configuration = ...)} 的配置类，不标注
 * {@code @Configuration}，避免被主上下文全局扫描导致所有 FeignClient 都带上该头。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
public class ServiceAuthFeignConfig {

    @Bean
    public RequestInterceptor serviceAuthFeignInterceptor(
            @Value("${service-auth.caller:lingxi-hr}") String caller,
            @Value("${service-auth.token:}") String token) {
        return template -> {
            template.header("X-Caller-Service", caller);
            if (token != null && !token.isEmpty()) {
                template.header("X-Service-Token", token);
            }
        };
    }
}
