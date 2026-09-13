package com.lingxi.user.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 发送验证码请求
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class SendCodeRequest {

    @NotBlank(message = "手机号不能为空")
    @Phone
    private String phone;
}
