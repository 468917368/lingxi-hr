package com.lingxi.job.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;

/**
 * Feign 出站服务认证头配置
 * <p>
 * 为 lingxi-job 发起的内部 Feign 调用自动携带 {@code X-Caller-Service} 与
 * {@code X-Service-Token} 头（系分 B-05 服务身份认证）。
 * </p>
 * <p>
 * <b>注意：本类不加 {@code @Configuration} 注解</b>——Feign 官方约定：被
 * {@code @FeignClient(configuration = ...)} 引用的配置类若标 {@code @Configuration}
 * 会被组件扫描全局加载，污染所有 Feign 客户端。仅由 Resume/Company 两个
 * Feign 客户端通过 {@code configuration} 属性显式引用。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
public class InternalFeignAuthConfig {

    /** 本服务名（出站头 X-Caller-Service 取值） */
    private static final String SELF_SERVICE = "lingxi-job";

    /**
     * 出站认证拦截器：为请求模板注入服务身份头
     * <p>
     * token 从 {@link ServicePermissionConfig} 读取（复用 service-auth.tokens 同一份表，
     * 不新建 Properties）。
     * </p>
     *
     * @param permissionConfig 服务凭证配置
     * @return Feign 请求拦截器
     */
    @Bean
    public RequestInterceptor internalFeignAuthInterceptor(ServicePermissionConfig permissionConfig) {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                template.header("X-Caller-Service", SELF_SERVICE);
                String token = permissionConfig.getTokens().get(SELF_SERVICE);
                if (token != null) {
                    template.header("X-Service-Token", token);
                }
            }
        };
    }
}
