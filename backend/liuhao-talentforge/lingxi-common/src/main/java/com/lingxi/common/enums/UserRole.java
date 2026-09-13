package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 用户角色枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum UserRole {

    /** 求职者 */
    CANDIDATE("CANDIDATE", "求职者"),

    /** HR */
    HR("HR", "HR"),

    /** 面试官 */
    INTERVIEWER("INTERVIEWER", "面试官"),

    /** 管理员 */
    ADMIN("ADMIN", "管理员");

    private final String code;
    private final String desc;

    UserRole(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static UserRole fromCode(String code) {
        for (UserRole role : values()) {
            if (role.getCode().equals(code)) {
                return role;
            }
        }
        throw new IllegalArgumentException("未知的用户角色: " + code);
    }

    /**
     * 判断是否为HR角色（HR或面试官）
     */
    public boolean isHrRole() {
        return this == HR || this == INTERVIEWER;
    }
}
