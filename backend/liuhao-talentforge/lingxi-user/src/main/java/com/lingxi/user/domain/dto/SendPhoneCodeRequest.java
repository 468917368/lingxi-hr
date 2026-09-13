package com.lingxi.user.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 发送手机号验证码请求（用于修改手机号）
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Data
public class SendPhoneCodeRequest {

    /** 新手机号 */
    @NotBlank(message = "新手机号不能为空")
    @Phone
    private String newPhone;
}
