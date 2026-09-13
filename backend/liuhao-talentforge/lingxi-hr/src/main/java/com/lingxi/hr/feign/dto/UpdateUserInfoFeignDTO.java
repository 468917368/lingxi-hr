package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 更新用户基本信息 Feign 入参（调用 lingxi-user {@code PUT /api/v1/user/info}）
 * <p>仅透传姓名/头像；email 不走此接口（A 需验证码流程）。</p>
 *
 * @author 成员D
 * @since 2026-08-08
 */
@Data
public class UpdateUserInfoFeignDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private String name;
    private String avatar;
}
