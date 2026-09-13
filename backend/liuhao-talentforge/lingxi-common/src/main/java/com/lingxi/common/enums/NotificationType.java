package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 通知类型枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum NotificationType {

    /** 投递通知 */
    APPLICATION("APPLICATION", "投递通知"),

    /** 面试通知 */
    INTERVIEW("INTERVIEW", "面试通知"),

    /** Offer通知 */
    OFFER("OFFER", "Offer通知"),

    /** 系统通知 */
    SYSTEM("SYSTEM", "系统通知"),

    /** 岗位推荐 */
    JOB_RECOMMEND("JOB_RECOMMEND", "岗位推荐");

    private final String code;
    private final String desc;

    NotificationType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static NotificationType fromCode(String code) {
        for (NotificationType type : values()) {
            if (type.getCode().equals(code)) {
                return type;
            }
        }
        throw new IllegalArgumentException("未知的通知类型: " + code);
    }
}
