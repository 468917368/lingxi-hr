package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 岗位暂停原因枚举
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum PauseReasonEnum {

    HC_RESERVED_FULL("HC_RESERVED_FULL", "HC预占满"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static PauseReasonEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (PauseReasonEnum reason : values()) {
            if (reason.code.equals(code)) {
                return reason;
            }
        }
        return null;
    }

    /**
     * 判断code是否合法
     */
    public static boolean isValid(String code) {
        return fromCode(code) != null;
    }
}
