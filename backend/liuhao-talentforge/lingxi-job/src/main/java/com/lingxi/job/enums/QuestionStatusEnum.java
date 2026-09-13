package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目状态枚举
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Getter
@AllArgsConstructor
public enum QuestionStatusEnum {

    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    ACTIVE("ACTIVE", "已启用"),
    INACTIVE("INACTIVE", "已停用"),
    REJECTED("REJECTED", "已拒绝"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static QuestionStatusEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QuestionStatusEnum status : values()) {
            if (status.code.equals(code)) {
                return status;
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
