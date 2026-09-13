package com.lingxi.job.enums;

import com.lingxi.common.exception.BusinessException;

/**
 * 出题难度枚举
 * <p>
 * F-17 请求体 difficulty 规范化：null/空串 → MEDIUM、trim+转大写、非法值 → 400。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
public enum DifficultyEnum {

    /** 基础 */
    EASY("EASY"),
    /** 中等 */
    MEDIUM("MEDIUM"),
    /** 困难 */
    HARD("HARD");

    private final String code;

    DifficultyEnum(String code) {
        this.code = code;
    }

    public String getCode() {
        return code;
    }

    /**
     * 规范化难度值（null/空 → MEDIUM；非法 → 400）
     *
     * @param difficulty 原始难度值
     * @return 规范化后的难度编码
     */
    public static String normalize(String difficulty) {
        if (difficulty == null || difficulty.trim().isEmpty()) {
            return MEDIUM.code;
        }
        String upper = difficulty.trim().toUpperCase();
        for (DifficultyEnum d : values()) {
            if (d.code.equals(upper)) {
                return d.code;
            }
        }
        throw new BusinessException(400, "非法的难度值: " + difficulty);
    }
}
