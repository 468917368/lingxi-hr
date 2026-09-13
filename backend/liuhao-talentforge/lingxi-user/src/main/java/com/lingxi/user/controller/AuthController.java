package com.lingxi.user.controller;

import com.lingxi.common.annotation.RequireLogin;
import com.lingxi.common.context.UserContext;
import com.lingxi.common.domain.Result;
import com.lingxi.common.util.IpUtil;
import com.lingxi.user.domain.dto.*;
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
 * 认证控制器
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 发送短信验证码
     * POST /api/v1/auth/send-code
     */
    @PostMapping("/send-code")
    public Result<Void> sendCode(@RequestBody @Valid SendCodeRequest request,
                                 HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        authService.sendVerificationCode(request.getPhone(), clientIp);
        return Result.success();
    }

    /**
     * 用户注册（手机号+验证码+密码+姓名+角色）
     * POST /api/v1/auth/register
     */
    @PostMapping("/register")
    public Result<LoginResponse> register(@RequestBody @Valid RegisterRequest request,
                                          HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        LoginResponse response = authService.register(
                request.getPhone(), request.getCode(), request.getPassword(),
                request.getName(), request.getRole(), clientIp);
        return Result.success(response);
    }

    /**
     * 手机号+验证码登录（登录方式一，仅已注册用户）
     * POST /api/v1/auth/login
     */
    @PostMapping("/login")
    public Result<LoginResponse> login(@RequestBody @Valid LoginRequest request,
                                       HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null) userAgent = "unknown";
        LoginResponse response = authService.loginByCode(request.getPhone(), request.getCode(), clientIp, userAgent);
        return Result.success(response);
    }

    /**
     * 手机号+密码登录（登录方式二，仅已注册用户）
     * POST /api/v1/auth/login-by-password
     */
    @PostMapping("/login-by-password")
    public Result<LoginResponse> loginByPassword(@RequestBody @Valid PasswordLoginRequest request,
                                                 HttpServletRequest httpRequest) {
        String clientIp = IpUtil.getClientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null) userAgent = "unknown";
        LoginResponse response = authService.loginByPassword(request.getPhone(), request.getPassword(), clientIp, userAgent);
        return Result.success(response);
    }

    /**
     * 修改密码（已登录用户）
     * POST /api/v1/auth/change-password
     */
    @RequireLogin
    @PostMapping("/change-password")
    public Result<Void> changePassword(@RequestBody @Valid ChangePasswordRequest request) {
        Long userId = UserContext.getUserId();
        authService.changePassword(userId, request.getOldPassword(), request.getNewPassword());
        return Result.success();
    }

    /**
     * 刷新Token
     * POST /api/v1/auth/refresh-token
     */
    @PostMapping("/refresh-token")
    public Result<LoginResponse> refreshToken(@RequestBody @Valid RefreshTokenRequest request) {
        LoginResponse response = authService.refreshToken(request.getRefreshToken());
        return Result.success(response);
    }
}
