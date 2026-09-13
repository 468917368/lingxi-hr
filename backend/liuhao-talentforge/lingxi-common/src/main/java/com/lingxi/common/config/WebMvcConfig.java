package com.lingxi.common.config;

import com.lingxi.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web MVC 配置
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Configuration
@RequiredArgsConstructor
public class WebMvcConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/**")
                .excludePathPatterns(
                        "/api/v1/auth/send-code",
                        "/api/v1/auth/register",
                        "/api/v1/auth/login",
                        "/api/v1/auth/login-by-password",
                        "/api/v1/auth/refresh-token",
                        "/api/v1/admin-auth/login",
                        "/internal/**",
                        "/files/**",  // 静态资源不需要鉴权
                        "/actuator/**",
                        "/error"
                );
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 将 /files/** 路径映射到本地 ./uploads/ 目录
        registry.addResourceHandler("/files/**")
                .addResourceLocations("file:./uploads/");
    }
}
