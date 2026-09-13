package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目类型枚举
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Getter
@AllArgsConstructor
public enum QuestionTypeEnum {

    BASIC("BASIC", "基础验证"),
    PROJECT("PROJECT", "项目深挖"),
    BOUNDARY("BOUNDARY", "能力边界"),
    COMPREHENSIVE("COMPREHENSIVE", "综合素养"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static QuestionTypeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QuestionTypeEnum type : values()) {
            if (type.code.equals(code)) {
                return type;
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
