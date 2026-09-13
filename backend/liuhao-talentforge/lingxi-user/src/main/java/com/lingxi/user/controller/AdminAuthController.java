package com.lingxi.user.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.user.domain.dto.AdminLoginRequest;
import com.lingxi.user.domain.dto.ChangePasswordRequest;
import com.lingxi.user.domain.vo.LoginResponse;
import com.lingxi.user.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;

/**
 * 管理员认证控制器
 * <p>
 * 路径使用 /api/v1/admin-auth 与管理后台 /api/v1/admin 分离，
 * 避免网关路由冲突。
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/admin-auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthService authService;

    /**
     * 管理员账号密码登录
     * POST /api/v1/admin-auth/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> adminLogin(@RequestBody @Valid AdminLoginRequest request,
                                            HttpServletRequest httpRequest) {
        String clientIp = getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        LoginResponse response = authService.adminLogin(request.getUsername(), request.getPassword(), clientIp, userAgent);
        return Result.success(response);
    }

    private String getClientIp(HttpServletRequest request) {
        String ip = request.getHeader("X-Forwarded-For");
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getHeader("X-Real-IP");
        }
        if (ip == null || ip.isEmpty() || "unknown".equalsIgnoreCase(ip)) {
            ip = request.getRemoteAddr();
        }
        return ip;
    }

    /**
     * 管理员修改密码（首次登录）
     * POST /api/v1/admin-auth/change-password
     */
    @RequireLogin
    @PostMapping("/change-password")
    public Result<Void> adminChangePassword(@RequestBody @Valid ChangePasswordRequest request,
                                            HttpServletRequest httpRequest) {
        // 优先从UserContext获取，如果为空则从请求头获取（兜底方案）
        Long adminId = UserContext.getUserId();
        if (adminId == null) {
            String userIdStr = httpRequest.getHeader("X-User-Id");
            if (userIdStr != null && !userIdStr.isEmpty()) {
                adminId = Long.parseLong(userIdStr);
            }
        }
        authService.adminChangePassword(adminId, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }
}
