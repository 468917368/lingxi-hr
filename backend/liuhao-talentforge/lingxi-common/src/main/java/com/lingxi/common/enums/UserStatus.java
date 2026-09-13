package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 用户状态枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum UserStatus {

    /** 正常 */
    ACTIVE("ACTIVE", "正常"),

    /** 禁用 */
    DISABLED("DISABLED", "禁用");

    private final String code;
    private final String desc;

    UserStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static UserStatus fromCode(String code) {
        for (UserStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的用户状态: " + code);
    }
}
