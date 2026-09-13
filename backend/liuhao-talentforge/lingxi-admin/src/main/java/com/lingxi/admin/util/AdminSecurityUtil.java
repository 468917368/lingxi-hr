package com.lingxi.admin.util;

import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

/**
 * 管理员安全工具类
 * <p>
 * 用于在请求处理链路中传递当前登录管理员信息。
 * 拦截器解析Token后将管理员信息写入ThreadLocal，业务层通过此工具类获取。
 * 请求完成后必须调用 clear() 清理，防止内存泄漏。
 * </p>
 *
 * @author 成员E
 * @since 2026-08-01
 */
public class AdminSecurityUtil {

    private AdminSecurityUtil() {
    }

    private static final ThreadLocal<Long> CURRENT_ADMIN_ID = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ADMIN_USERNAME = new ThreadLocal<>();
    private static final ThreadLocal<String> CURRENT_ADMIN_NAME = new ThreadLocal<>();

    /**
     * 设置当前管理员ID
     */
    public static void setCurrentAdminId(Long adminId) {
        CURRENT_ADMIN_ID.set(adminId);
    }

    /**
     * 获取当前管理员ID
     */
    public static Long getCurrentAdminId() {
        return CURRENT_ADMIN_ID.get();
    }

    /**
     * 设置当前管理员用户名
     */
    public static void setCurrentAdminUsername(String username) {
        CURRENT_ADMIN_USERNAME.set(username);
    }

    /**
     * 获取当前管理员用户名
     */
    public static String getCurrentAdminUsername() {
        return CURRENT_ADMIN_USERNAME.get();
    }

    /**
     * 设置当前管理员姓名
     */
    public static void setCurrentAdminName(String name) {
        CURRENT_ADMIN_NAME.set(name);
    }

    /**
     * 获取当前管理员姓名
     */
    public static String getCurrentAdminName() {
        return CURRENT_ADMIN_NAME.get();
    }

    /**
     * 清理ThreadLocal（请求结束时必须调用）
     */
    public static void clear() {
        CURRENT_ADMIN_ID.remove();
        CURRENT_ADMIN_USERNAME.remove();
        CURRENT_ADMIN_NAME.remove();
    }

    /**
     * 获取客户端IP地址
     *
     * @return IP地址
     */
    public static String getIp() {
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes == null) {
            return "unknown";
        }

        HttpServletRequest request = attributes.getRequest();

        // 优先从代理头获取
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("WL-Proxy-Client-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }

        // 多个代理时取第一个
        if (ip != null && ip.contains(",")) {
            ip = ip.split(",")[0].trim();
        }

        return ip;
    }
}
