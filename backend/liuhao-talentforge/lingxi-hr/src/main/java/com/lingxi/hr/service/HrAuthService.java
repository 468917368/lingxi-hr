package com.lingxi.hr.service;

import com.lingxi.hr.domain.dto.HrRegisterRequestDTO;
import com.lingxi.hr.feign.dto.LoginResponseDTO;

/**
 * HR 认证服务（账号注册，企业创建分离）
 *
 * @author 成员D
 * @since 2026-08-02
 */
public interface HrAuthService {

    /**
     * HR 注册（复用 lingxi-user AuthController#register，角色固定 HR）
     */
    LoginResponseDTO registerHr(HrRegisterRequestDTO request);
}
