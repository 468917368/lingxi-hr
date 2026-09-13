package com.lingxi.common.enums;

import lombok.Getter;

/**
 * Offer状态枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum OfferStatus {

    /** 草稿 */
    DRAFT("DRAFT", "草稿"),

    /** 已发送 */
    SENT("SENT", "已发送"),

    /** 已接受 */
    ACCEPTED("ACCEPTED", "已接受"),

    /** 已拒绝 */
    REJECTED("REJECTED", "已拒绝"),

    /** 已过期 */
    EXPIRED("EXPIRED", "已过期"),

    /** 已撤回 */
    WITHDRAWN("WITHDRAWN", "已撤回");

    private final String code;
    private final String desc;

    OfferStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static OfferStatus fromCode(String code) {
        for (OfferStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的Offer状态: " + code);
    }

    /**
     * 判断是否为终态
     */
    public boolean isFinal() {
        return this == ACCEPTED || this == REJECTED || this == EXPIRED || this == WITHDRAWN;
    }
}
