package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.Size;
import java.io.Serializable;

/**
 * 个人中心 - 更新账号信息入参
 * <p>均选填，至少传一个。email 不在本接口（A 仅支持验证码流程更新）；position 不展示。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class UpdateAccountProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 姓名（1-32 字符，A 侧校验 + 每月一次修改限制） */
    @Size(max = 32, message = "姓名长度须在1-32字符之间")
    private String name;

    /** 头像 URL */
    @Size(max = 512, message = "头像地址过长")
    private String avatar;

    /** 所属部门（更新 hr_company_member.department） */
    @Size(max = 64, message = "部门长度须在1-64字符之间")
    private String department;
}
