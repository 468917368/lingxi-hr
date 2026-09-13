package com.lingxi.admin.interceptor;

import com.lingxi.admin.util.AdminSecurityUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 管理员认证拦截器
 * <p>
 * 从网关注入的请求头中提取管理员信息，设置到 AdminSecurityUtil ThreadLocal 中。
 * 网关解析 Token 后会注入 X-User-Id、X-User-Role 等请求头。
 * </p>
 *
 * @author 成员E
 * @since 2026-08-04
 */
@Slf4j
@Component
public class AdminAuthInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // 从网关注入的请求头获取用户信息
        String userIdStr = request.getHeader("X-User-Id");
        String role = request.getHeader("X-User-Role");
        String phone = request.getHeader("X-Phone");

        if (userIdStr != null && !userIdStr.isEmpty()) {
            try {
                Long userId = Long.parseLong(userIdStr);
                AdminSecurityUtil.setCurrentAdminId(userId);
                AdminSecurityUtil.setCurrentAdminUsername(phone); // 使用手机号作为用户名标识
                log.debug("管理员认证信息已设置: userId={}, role={}", userId, role);
            } catch (NumberFormatException e) {
                log.warn("用户ID格式错误: {}", userIdStr);
            }
        }

        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        // 清理 ThreadLocal，防止内存泄漏
        AdminSecurityUtil.clear();
    }
}
