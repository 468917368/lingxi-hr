package com.lingxi.user.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 刷新Token请求
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class RefreshTokenRequest {

    @NotBlank(message = "refreshToken不能为空")
    private String refreshToken;
}
