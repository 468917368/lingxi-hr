package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 验证并修改手机号 Feign 入参（调用 lingxi-user {@code POST /api/v1/user/phone}）
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class ChangePhoneFeignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String newPhone;
    private String code;
}
