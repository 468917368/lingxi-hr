package com.lingxi.job.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 行业编码枚举（一级行业，与二级行业编码一致）
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Getter
@AllArgsConstructor
public enum IndustryCodeEnum {

    IT("IT", "互联网/IT"),
    ECOMMERCE("ECOMMERCE", "电商"),
    FINANCE("FINANCE", "金融"),
    EDUCATION("EDUCATION", "教育"),
    GAME("GAME", "游戏"),
    MEDICAL("MEDICAL", "医疗健康"),
    MANUFACTURING("MANUFACTURING", "制造业"),
    ENTERPRISE_SERVICE("ENTERPRISE_SERVICE", "企业服务"),
    LIFE_SERVICE("LIFE_SERVICE", "生活服务"),
    OTHER("OTHER", "其他"),
    ;

    /** 编码 */
    private final String code;

    /** 中文描述 */
    private final String desc;

    /**
     * 根据code获取枚举，未知返回null
     */
    public static IndustryCodeEnum fromCode(String code) {
        if (code == null) {
            return null;
        }
        for (IndustryCodeEnum industry : values()) {
            if (industry.code.equals(code)) {
                return industry;
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
