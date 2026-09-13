package com.lingxi.hr.controller;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.domain.dto.HrRegisterRequestDTO;
import com.lingxi.hr.feign.dto.LoginResponseDTO;
import com.lingxi.hr.service.HrAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * HR 认证控制器（注册账号）
 * <p>
 * 账号注册与创建企业分离：注册走本接口（内部复用 lingxi-user 注册），
 * 企业创建/认证走 {@code /api/v1/hr/company/certification}。
 * </p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/hr")
@RequiredArgsConstructor
public class HrAuthController {

    private final HrAuthService hrAuthService;

    /**
     * HR 注册（注册即登录，返回Token；companyId 为 null，待企业认证后绑定）
     */
    @PostMapping("/register")
    public Result<LoginResponseDTO> register(@Validated @RequestBody HrRegisterRequestDTO request) {
        return Result.success(hrAuthService.registerHr(request));
    }
}
