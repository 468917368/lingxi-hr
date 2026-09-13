package com.lingxi.admin.domain.dto;

import lombok.Data;
import javax.validation.constraints.NotBlank;

/**
 * 修改管理员个人信息DTO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class ProfileUpdateDTO {

    /**
     * 管理员姓名
     */
    @NotBlank(message = "管理员名称不能为空")
    private String name;

    /**
     * 头像URL
     */
    private String avatar;

    /**
     * 邮箱
     */
    private String email;
}
