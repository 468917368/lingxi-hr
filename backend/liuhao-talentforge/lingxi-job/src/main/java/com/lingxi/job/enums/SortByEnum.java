package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * C端岗位搜索排序方式枚举
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum SortByEnum {

    RECOMMENDED("RECOMMENDED", "智能推荐"),
    LATEST("LATEST", "最新发布"),
    SALARY_DESC("SALARY_DESC", "薪资从高到低"),
    ;

    /** 排序编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static SortByEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (SortByEnum sort : values()) {
            if (sort.code.equals(code)) {
                return sort;
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
