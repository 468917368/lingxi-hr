package com.lingxi.common.enums;

import lombok.Getter;

/**
 * 岗位状态枚举
 *
 * @author lingxi-team
 * @since 2026-07-31
 */
@Getter
public enum JobStatus {

    /** 草稿 */
    DRAFT("DRAFT", "草稿"),

    /** 已发布 */
    PUBLISHED("PUBLISHED", "已发布"),

    /** 已暂停 */
    PAUSED("PAUSED", "已暂停"),

    /** 已关闭 */
    CLOSED("CLOSED", "已关闭");

    private final String code;
    private final String desc;

    JobStatus(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    /**
     * 根据code获取枚举
     */
    public static JobStatus fromCode(String code) {
        for (JobStatus status : values()) {
            if (status.getCode().equals(code)) {
                return status;
            }
        }
        throw new IllegalArgumentException("未知的岗位状态: " + code);
    }

    /**
     * 判断是否可编辑
     */
    public boolean isEditable() {
        return this == DRAFT;
    }

    /**
     * 判断是否可删除
     */
    public boolean isDeletable() {
        return this == DRAFT;
    }

    /**
     * 判断是否可发布
     */
    public boolean isPublishable() {
        return this == DRAFT;
    }

    /**
     * 判断是否可关闭
     */
    public boolean isCloseable() {
        return this == PUBLISHED || this == PAUSED;
    }

    /**
     * 判断是否可重新开放
     */
    public boolean isReopenable() {
        return this == CLOSED;
    }
}
