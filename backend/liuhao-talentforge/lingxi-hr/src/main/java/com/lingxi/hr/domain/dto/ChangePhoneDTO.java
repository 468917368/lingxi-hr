package com.lingxi.hr.domain.dto;

import com.lingxi.common.annotation.Phone;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 个人中心 - 验证并修改手机号入参
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class ChangePhoneDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "新手机号不能为空")
    @Phone
    private String newPhone;

    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;
}
