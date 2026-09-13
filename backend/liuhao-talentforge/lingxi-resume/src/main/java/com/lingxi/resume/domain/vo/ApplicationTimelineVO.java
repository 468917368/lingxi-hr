package com.lingxi.resume.domain.vo;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递状态时间线项视图对象
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class ApplicationTimelineVO {

    /** 原状态（首次投递时为 null） */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 操作人角色：CANDIDATE/HR/SYSTEM */
    private String operatorRole;

    /** 变更原因 */
    private String reason;

    /** 变更时间 */
    private LocalDateTime createdAt;
}
