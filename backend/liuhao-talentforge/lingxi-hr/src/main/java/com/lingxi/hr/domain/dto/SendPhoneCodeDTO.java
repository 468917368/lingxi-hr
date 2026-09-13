package com.lingxi.hr.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 个人中心 - 发送手机号验证码入参（修改手机号，验证码发送到新手机号）
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class SendPhoneCodeDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "新手机号不能为空")
    @Phone
    private String newPhone;
}
