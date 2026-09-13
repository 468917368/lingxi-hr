package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 投递状态枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum ApplicationStatus {

    /** 已投递 */
    SUBMITTED("SUBMITTED", "已投递"),

    /** 已查看 */
    VIEWED("VIEWED", "已查看"),

    /** 已筛选 */
    SCREENED("SCREENED", "已筛选"),

    /** 面试中 */
    INTERVIEWING("INTERVIEWING", "面试中"),

    /** 可录用 */
    OFFERABLE("OFFERABLE", "可录用"),

    /** 待录用（HR 已发 Offer，候选人未表态） */
    OFFERED("OFFERED", "待录用"),

    /** 已录用（候选人接受 Offer，终态） */
    OFFER_ACCEPTED("OFFER_ACCEPTED", "已录用"),

    /** 已拒绝（候选人拒绝 Offer，终态） */
    OFFER_DECLINED("OFFER_DECLINED", "已拒绝"),

    /** 已淘汰 */
    REJECTED("REJECTED", "已淘汰"),

    /** 已撤回 */
    WITHDRAWN("WITHDRAWN", "已撤回");

    private final String code;
    private final String desc;

    ApplicationStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static ApplicationStatus fromCode(String code) {
        for (ApplicationStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的投递状态: " + code);
    }

    /**
     * 判断是否为终态（OFFERED 为待回应过程态，不再是终态）
     */
    public boolean isFinal() {
        return this == REJECTED || this == WITHDRAWN
                || this == OFFER_ACCEPTED || this == OFFER_DECLINED;
    }

    /**
     * 判断是否可撤回：仅投递后、面试前的三个早期态（面试安排后一律不可撤回）
     */
    public boolean isWithdrawable() {
        return this == SUBMITTED || this == VIEWED || this == SCREENED;
    }
}
