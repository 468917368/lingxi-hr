package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录响应
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginResponse {

    /** AccessToken */
    private String accessToken;

    /** RefreshToken */
    private String refreshToken;

    /** AccessToken有效期（秒） */
    private Long expiresIn;

    /** 用户信息 */
    private UserInfoVO user;
}
