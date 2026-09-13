package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 个人中心 - 账号信息 VO
 * <p>position 不展示（2026-08-08 用户确认）；操作日志不展示。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class AccountProfileVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String phone;
    private String email;
    private String avatar;
    private String department;
    private String companyName;
    private String role;
}
