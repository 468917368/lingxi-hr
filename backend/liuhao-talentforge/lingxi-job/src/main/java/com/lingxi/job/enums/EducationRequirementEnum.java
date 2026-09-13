package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 学历要求枚举
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum EducationRequirementEnum {

    NONE("NONE", "不限", 0),
    JUNIOR_HIGH("JUNIOR_HIGH", "初中", 1),
    HIGH_SCHOOL("HIGH_SCHOOL", "高中", 2),
    ASSOCIATE("ASSOCIATE", "大专", 3),
    BACHELOR("BACHELOR", "本科", 4),
    MASTER("MASTER", "硕士", 5),
    DOCTOR("DOCTOR", "博士", 6),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /** 学历等级（0最低~6最高，用于推荐分接近度计算） */
    private final int level;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static EducationRequirementEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (EducationRequirementEnum education : values()) {
            if (education.code.equals(code)) {
                return education;
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
