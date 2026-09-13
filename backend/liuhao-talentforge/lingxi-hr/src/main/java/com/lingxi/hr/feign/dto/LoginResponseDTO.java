package com.lingxi.hr.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 登录/注册响应 DTO（对应 lingxi-user LoginResponse）
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class LoginResponseDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** AccessToken */
    private String accessToken;

    /** RefreshToken */
    private String refreshToken;

    /** AccessToken有效期（秒） */
    private Long expiresIn;

    /** 用户信息 */
    private UserInfoDTO user;
}
