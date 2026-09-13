package com.lingxi.user.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;

/**
 * 验证并修改手机号请求
 * <p>
 * 用于 POST /api/v1/user/phone 接口
 * 发送验证码请使用 SendPhoneCodeRequest
 * </p>
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Data
public class ChangePhoneRequest {

    @NotBlank(message = "新手机号不能为空")
    @Phone
    private String newPhone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;
}
