package com.lingxi.admin.config;

import com.lingxi.admin.interceptor.AdminAuthInterceptor;
import com.lingxi.common.interceptor.AuthInterceptor;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 管理后台 Web MVC 配置
 *
 * @author 成员E
 * @since 2026-08-04
 */
@Configuration
@RequiredArgsConstructor
public class AdminWebMvcConfig implements WebMvcConfigurer {

    private final AdminAuthInterceptor adminAuthInterceptor;
    private final AuthInterceptor authInterceptor;

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Token解析 + @RequireRole校验
        registry.addInterceptor(authInterceptor)
                .addPathPatterns("/api/v1/admin/**");
        // 管理员信息注入ThreadLocal
        registry.addInterceptor(adminAuthInterceptor)
                .addPathPatterns("/api/v1/admin/**");
    }
}
