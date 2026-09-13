package com.lingxi.hr.feign;

import com.lingxi.common.domain.Result;
import com.lingxi.hr.feign.dto.LoginResponseDTO;
import com.lingxi.hr.feign.dto.RegisterRequestDTO;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * lingxi-user 认证 Feign 客户端
 * <p>
 * 用于复用 lingxi-user 的 {@code AuthController#register}（短信验证码校验 + 创建用户 + 签发Token）。
 * </p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@FeignClient(name = "lingxi-user", contextId = "authFeignClient", path = "/api/v1/auth")
public interface AuthFeignClient {

    /**
     * 注册用户（注册即登录，返回Token）
     */
    @PostMapping("/register")
    Result<LoginResponseDTO> register(@RequestBody RegisterRequestDTO request);
}
