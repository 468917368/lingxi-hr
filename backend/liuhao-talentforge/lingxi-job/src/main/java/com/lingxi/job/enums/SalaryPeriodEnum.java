package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 薪资周期枚举
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum SalaryPeriodEnum {

    HOUR("HOUR", "时薪"),
    DAY("DAY", "日薪"),
    MONTH("MONTH", "月薪"),
    YEAR("YEAR", "年薪"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static SalaryPeriodEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SalaryPeriodEnum period : values()) {
            if (period.code.equals(code)) {
                return period;
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
