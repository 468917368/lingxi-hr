package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 个人中心 - 修改密码入参
 * <p>confirmPassword 由 D 侧校验一致性；新密码强度由 A 校验（8-20 位含大小写字母数字特殊字符）。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class ChangePasswordDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    @NotBlank(message = "当前密码不能为空")
    private String oldPassword;

    @NotBlank(message = "新密码不能为空")
    private String newPassword;

    @NotBlank(message = "确认新密码不能为空")
    private String confirmPassword;
}
