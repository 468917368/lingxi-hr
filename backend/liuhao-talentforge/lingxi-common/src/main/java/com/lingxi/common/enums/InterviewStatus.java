package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 面试状态枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum InterviewStatus {

    /** 待安排 */
    PENDING("PENDING", "待安排"),

    /** 已安排 */
    SCHEDULED("SCHEDULED", "已安排"),

    /** 进行中 */
    IN_PROGRESS("IN_PROGRESS", "进行中"),

    /** 已完成 */
    COMPLETED("COMPLETED", "已完成"),

    /** 已取消 */
    CANCELLED("CANCELLED", "已取消");

    private final String code;
    private final String desc;

    InterviewStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static InterviewStatus fromCode(String code) {
        for (InterviewStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的面试状态: " + code);
    }
}
