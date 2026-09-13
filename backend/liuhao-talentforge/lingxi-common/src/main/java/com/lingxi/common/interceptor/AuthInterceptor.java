package com.lingxi.common.interceptor;

import com.lingxi.common.annotation.RequireCertification;
import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.annotation.RequireRole;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.context.UserDTO;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.common.util.JwtUtil;
import io.jsonwebtoken.Claims;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * 权限拦截器
 * <p>
 * 所有业务服务共用的用户鉴权拦截器，负责：
 * 1. 从请求头提取用户信息（Gateway注入或直接解析Token）
 * 2. 校验方法级权限注解（@RequireLogin, @RequireRole, @RequireCertification）
 * 3. 存储用户信息到ThreadLocal（UserContext），供Controller/Service使用
 * 4. 请求完成后清理ThreadLocal，防止内存泄漏
 * </p>
 * <p>
 * 用户信息提取支持两种方式：
 * - 方式1：Gateway已解析Token，注入了X-User-Id等请求头（生产环境）
 * - 方式2：直接携带Authorization: Bearer xxx头（开发/测试/Feign调用）
 * </p>
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Slf4j
@Component
public class AuthInterceptor implements HandlerInterceptor {

    // ==================== 请求头常量 ====================
    /** Gateway注入的用户ID */
    private static final String HEADER_USER_ID = "X-User-Id";
    /** Gateway注入的用户角色 */
    private static final String HEADER_USER_ROLE = "X-User-Role";
    /** Gateway注入的手机号 */
    private static final String HEADER_PHONE = "X-Phone";
    /** Gateway注入的企业ID（HR/面试官有值） */
    private static final String HEADER_COMPANY_ID = "X-Company-Id";
    /** Gateway注入的用户状态 */
    private static final String HEADER_USER_STATUS = "X-User-Status";
    /** 标准Authorization头 */
    private static final String HEADER_AUTHORIZATION = "Authorization";
    /** Bearer前缀 */
    private static final String BEARER_PREFIX = "Bearer ";

    /**
     * 请求处理前的拦截
     * <p>
     * 执行顺序：
     * 1. 非Controller方法（静态资源等）直接放行
     * 2. 查找权限注解（方法级优先于类级）
     * 3. 提取用户信息
     * 4. 校验登录状态
     * 5. 检查用户是否被禁用
     * 6. 设置UserContext
     * 7. 校验角色权限
     * 8. 校验企业认证状态
     * </p>
     *
     * @return true=放行, false=拒绝（本拦截器通过抛异常拒绝，不返回false）
     */
    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) {
        // ① 非控制器方法直接放行（静态资源、WebSocket等）
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }

        HandlerMethod handlerMethod = (HandlerMethod) handler;

        // ② 查找 @RequireLogin 注解（方法级优先，其次类级）
        RequireLogin requireLogin = handlerMethod.getMethodAnnotation(RequireLogin.class);
        if (requireLogin == null) {
            requireLogin = handlerMethod.getBeanType().getAnnotation(RequireLogin.class);
        }

        // ③ 提取用户信息（两种方式：Gateway请求头 或 直接解析Token）
        UserDTO user = extractUser(request);
        boolean hasUser = user != null;

        // ④ 需要登录但未登录 → 401
        if (requireLogin != null && !hasUser) {
            log.warn("需要登录但未登录: path={}", request.getRequestURI());
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }

        // ⑤ 有用户信息 → 检查状态 + 设置上下文
        if (hasUser) {
            // 检查用户是否被禁用
            String status = user.getStatus();
            if ("DISABLED".equals(status)) {
                log.warn("用户已被禁用: userId={}, path={}", user.getUserId(), request.getRequestURI());
                throw new BusinessException(ErrorCode.FORBIDDEN.getErrorCode(), "账号已被禁用，请联系管理员");
            }
            // 存入ThreadLocal，供Controller/Service使用
            UserContext.set(user);
        }

        // ⑥ 校验角色权限（方法级优先于类级）
        RequireRole requireRole = handlerMethod.getMethodAnnotation(RequireRole.class);
        if (requireRole == null) {
            requireRole = handlerMethod.getBeanType().getAnnotation(RequireRole.class);
        }
        if (requireRole != null) {
            if (!hasUser) {
                throw new BusinessException(ErrorCode.UNAUTHORIZED);
            }
            // 检查用户角色是否在允许列表中
            String[] allowedRoles = requireRole.value();
            boolean hasRole = false;
            for (String allowedRole : allowedRoles) {
                if (allowedRole.equals(user.getRole())) {
                    hasRole = true;
                    break;
                }
            }
            if (!hasRole) {
                log.warn("角色权限不足: userId={}, role={}, required={}, path={}",
                        user.getUserId(), user.getRole(), String.join(",", allowedRoles), request.getRequestURI());
                throw new BusinessException(ErrorCode.FORBIDDEN);
            }
        }

        // ⑦ 校验企业认证状态（仅HR/面试官需要，ADMIN和CANDIDATE跳过）
        RequireCertification requireCert = handlerMethod.getMethodAnnotation(RequireCertification.class);
        if (requireCert != null && hasUser) {
            if (user.isHrRole()) {
                String certStatus = user.getCertStatus();
                if (!"APPROVED".equals(certStatus)) {
                    log.warn("企业认证未通过: userId={}, certStatus={}, path={}",
                            user.getUserId(), certStatus, request.getRequestURI());
                    throw new BusinessException(ErrorCode.CERT_REQUIRED);
                }
            }
        }

        return true;
    }

    /**
     * 请求完成后的清理
     * <p>
     * 必须清理ThreadLocal，否则Tomcat线程池复用线程时，
     * 下一个请求会读到上一个请求的用户信息（信息泄露）。
     * </p>
     */
    @Override
    public void afterCompletion(HttpServletRequest request,
                                HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }

    /**
     * 从请求中提取用户信息
     * <p>
     * 支持两种方式：
     * 1. Gateway注入的请求头（X-User-Id, X-User-Role, X-Phone, X-Company-Id）
     *    → 生产环境使用，Gateway已解析Token并注入请求头，业务服务直接读取
     * 2. 直接调用时的Authorization头（Bearer Token）
     *    → 开发测试、Feign服务间调用时使用，业务服务自行解析Token
     * </p>
     *
     * @param request HTTP请求
     * @return 用户信息，未登录返回null
     */
    private UserDTO extractUser(HttpServletRequest request) {
        // 方式1：从Gateway注入的请求头获取（优先）
        String userIdStr = request.getHeader(HEADER_USER_ID);
        if (userIdStr != null && !userIdStr.isEmpty()) {
            return buildUserFromHeaders(request, userIdStr);
        }

        // 方式2：从Authorization头解析Token（兜底）
        String authHeader = request.getHeader(HEADER_AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith(BEARER_PREFIX)) {
            return buildUserFromToken(authHeader.substring(BEARER_PREFIX.length()));
        }

        return null;
    }

    /**
     * 从Gateway注入的请求头构建用户信息
     *
     * @param request   HTTP请求（从中读取各请求头）
     * @param userIdStr 用户ID字符串
     * @return 用户信息，解析失败返回null
     */
    private UserDTO buildUserFromHeaders(HttpServletRequest request, String userIdStr) {
        try {
            UserDTO user = new UserDTO();
            user.setUserId(Long.parseLong(userIdStr));
            user.setRole(request.getHeader(HEADER_USER_ROLE));
            user.setPhone(request.getHeader(HEADER_PHONE));
            user.setStatus(request.getHeader(HEADER_USER_STATUS));

            String companyIdStr = request.getHeader(HEADER_COMPANY_ID);
            if (companyIdStr != null && !companyIdStr.isEmpty()) {
                user.setCompanyId(Long.parseLong(companyIdStr));
            }

            return user;
        } catch (NumberFormatException e) {
            log.warn("请求头解析失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从JWT Token构建用户信息
     *
     * @param token JWT Token字符串（不含Bearer前缀）
     * @return 用户信息，解析失败返回null
     */
    private UserDTO buildUserFromToken(String token) {
        try {
            Claims claims = JwtUtil.parseToken(token);

            UserDTO user = new UserDTO();
            user.setUserId(claims.get("userId", Long.class));
            user.setRole(claims.get("role", String.class));
            user.setPhone(claims.get("phone", String.class));
            user.setStatus(claims.get("status", String.class));

            Object companyIdObj = claims.get("companyId");
            if (companyIdObj != null) {
                user.setCompanyId(Long.parseLong(String.valueOf(companyIdObj)));
            }

            log.debug("Token解析成功: userId={}, role={}, status={}", user.getUserId(), user.getRole(), user.getStatus());
            return user;
        } catch (Exception e) {
            log.warn("Token解析失败: token={}, error={}", token.substring(0, Math.min(20, token.length())), e.getMessage());
            return null;
        }
    }
}
