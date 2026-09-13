package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 企业成员 DTO（创建面试官）
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrMemberDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 手机号 */
    @NotBlank(message = "手机号不能为空")
    private String phone;

    /** 短信验证码（创建面试官注册用，复用 lingxi-user register） */
    @NotBlank(message = "验证码不能为空")
    @Pattern(regexp = "^\\d{6}$", message = "验证码格式不正确")
    private String code;

    /** 初始密码 */
    @NotBlank(message = "密码不能为空")
    @Size(min = 8, max = 20, message = "密码长度须在8-20位之间")
    private String password;

    /** 姓名 */
    @NotBlank(message = "姓名不能为空")
    private String name;

    /** 所属部门 */
    @NotBlank(message = "所属部门不能为空")
    private String department;

    /** 技术方向 */
    private String techDirection;

    /** 邮箱 */
    private String email;
}
