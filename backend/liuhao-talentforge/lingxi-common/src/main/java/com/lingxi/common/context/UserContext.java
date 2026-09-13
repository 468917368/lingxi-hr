package com.lingxi.common.context;

/**
 * 用户上下文（ThreadLocal）
 * <p>
 * 用于在请求处理链路中传递当前登录用户信息。
 * 网关解析Token后将用户ID写入请求头，下游服务通过拦截器提取并存入ThreadLocal。
 * 请求完成后必须调用 clear() 清理，防止内存泄漏。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
public class UserContext {

    private UserContext() {
    }

    private static final ThreadLocal<UserDTO> USER_HOLDER = new ThreadLocal<>();

    /**
     * 设置当前用户
     */
    public static void set(UserDTO user) {
        USER_HOLDER.set(user);
    }

    /**
     * 获取当前用户
     */
    public static UserDTO get() {
        return USER_HOLDER.get();
    }

    /**
     * 获取当前用户ID
     */
    public static Long getUserId() {
        UserDTO user = USER_HOLDER.get();
        return user != null ? user.getUserId() : null;
    }

    /**
     * 获取当前用户角色
     */
    public static String getRole() {
        UserDTO user = USER_HOLDER.get();
        return user != null ? user.getRole() : null;
    }

    /**
     * 获取当前企业ID
     */
    public static Long getCompanyId() {
        UserDTO user = USER_HOLDER.get();
        return user != null ? user.getCompanyId() : null;
    }

    /**
     * 清理用户上下文（请求结束时必须调用）
     */
    public static void clear() {
        USER_HOLDER.remove();
    }
}
