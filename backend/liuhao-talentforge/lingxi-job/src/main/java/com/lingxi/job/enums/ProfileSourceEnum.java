package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 岗位画像来源枚举
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum ProfileSourceEnum {

    AI("AI", "AI生成"),
    MANUAL("MANUAL", "手动填写"),
    MIXED("MIXED", "混合"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static ProfileSourceEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (ProfileSourceEnum source : values()) {
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
