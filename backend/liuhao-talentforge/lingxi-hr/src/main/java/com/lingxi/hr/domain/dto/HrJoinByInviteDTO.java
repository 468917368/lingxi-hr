package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.io.Serializable;

/**
 * 邀请码加入企业请求 DTO
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrJoinByInviteDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 6位邀请码（大写字母+数字） */
    @NotBlank(message = "邀请码不能为空")
    @Pattern(regexp = "^[A-Z0-9]{6}$", message = "邀请码格式不正确")
    private String inviteCode;
}
