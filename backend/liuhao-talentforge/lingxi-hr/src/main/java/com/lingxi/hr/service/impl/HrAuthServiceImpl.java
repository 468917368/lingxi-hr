package com.lingxi.hr.service.impl;

import com.lingxi.common.domain.Result;
import com.lingxi.common.exception.BusinessException;
import com.lingxi.common.exception.ErrorCode;
import com.lingxi.hr.domain.dto.HrRegisterRequestDTO;
import com.lingxi.hr.feign.AuthFeignClient;
import com.lingxi.hr.feign.dto.LoginResponseDTO;
import com.lingxi.hr.feign.dto.RegisterRequestDTO;
import com.lingxi.hr.service.HrAuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * HR 认证服务实现
 * <p>
 * 复用 lingxi-user 的注册逻辑：短信验证码校验、创建用户、BCrypt 密码、签发 Token。
 * lingxi-hr 仅透传并固定角色为 HR，账号创建与后续企业创建（submitCertification）解耦。
 * </p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class HrAuthServiceImpl implements HrAuthService {

    private static final String ROLE_HR = "HR";

    private final AuthFeignClient authFeignClient;

    @Override
    public LoginResponseDTO registerHr(HrRegisterRequestDTO request) {
        RegisterRequestDTO registerRequest = new RegisterRequestDTO();
        registerRequest.setPhone(request.getPhone());
        registerRequest.setCode(request.getCode());
        registerRequest.setPassword(request.getPassword());
        registerRequest.setName(request.getName());
        registerRequest.setRole(ROLE_HR);

        Result<LoginResponseDTO> result = authFeignClient.register(registerRequest);
        if (result == null || !result.isSuccess()) {
            int code = result != null ? result.getCode() : ErrorCode.SYSTEM_ERROR.getErrorCode();
            String message = result != null ? result.getMessage() : "注册服务异常，请重试";
            log.warn("HR注册失败: code={}, message={}", code, message);
            throw new BusinessException(code, message);
        }

        LoginResponseDTO data = result.getData();
        if (data != null) {
            Long userId = data.getUser() != null ? data.getUser().getId() : null;
            log.info("HR注册成功: userId={}, phone={}", userId, request.getPhone());
        }
        return data;
    }
}
