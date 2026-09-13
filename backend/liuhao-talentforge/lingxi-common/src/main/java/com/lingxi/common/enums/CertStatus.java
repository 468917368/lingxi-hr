package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 企业认证状态枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum CertStatus {

    /** 待审核 */
    PENDING("PENDING", "待审核"),

    /** 已通过 */
    APPROVED("APPROVED", "已通过"),

    /** 已拒绝 */
    REJECTED("REJECTED", "已拒绝");

    private final String code;
    private final String desc;

    CertStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static CertStatus fromCode(String code) {
        for (CertStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的认证状态: " + code);
    }
}
