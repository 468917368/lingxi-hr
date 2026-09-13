package com.lingxi.job.config;

import com.lingxi.job.interceptor.InternalServiceAuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 内部服务认证拦截器注册
 * <p>
 * 将 InternalServiceAuthInterceptor 挂到 /internal/**（排除 /internal/agent/**，
 * 阶段4 的 Agent Tool 用 X-Run-Token 单独校验）。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Configuration
@RequiredArgsConstructor
public class InternalServiceAuthConfig implements WebMvcConfigurer {

    private final InternalServiceAuthInterceptor internalServiceAuthInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(internalServiceAuthInterceptor)
                .addPathPatterns("/internal/**")
                .excludePathPatterns("/internal/agent/**");
    }
}
