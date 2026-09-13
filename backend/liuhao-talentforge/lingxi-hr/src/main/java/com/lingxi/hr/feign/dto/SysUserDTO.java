package com.lingxi.hr.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 用户信息 DTO（来自 lingxi-user）
 * <p>lingxi-user {@code /internal/users/{id}} 返回 {@code UserInfoVO}，比本 DTO 多
 * nameUpdatedAt/companyId/company/profile 等字段，忽略未知字段兜底。</p>
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SysUserDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String phone;
    private String name;
    private String avatar;
    private String email;
    private String role;
    private String status;
}
