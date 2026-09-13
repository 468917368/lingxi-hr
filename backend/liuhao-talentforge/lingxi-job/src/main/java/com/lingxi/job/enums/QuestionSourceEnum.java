package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 题目来源枚举
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Getter
@AllArgsConstructor
public enum QuestionSourceEnum {

    DEMO_SEED("DEMO_SEED", "演示种子"),
    HR_CREATED("HR_CREATED", "HR创建"),
    AI_GENERATED("AI_GENERATED", "AI生成"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static QuestionSourceEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (QuestionSourceEnum source : values()) {
            if (source.code.equals(code)) {
                return source;
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
