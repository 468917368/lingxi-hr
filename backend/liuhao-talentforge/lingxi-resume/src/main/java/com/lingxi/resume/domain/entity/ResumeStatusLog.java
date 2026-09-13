package com.lingxi.resume.domain.entity;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 投递状态变更日志实体（resume_status_log 表）
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class ResumeStatusLog {

    /** 主键ID */
    private Long id;

    /** 投递记录ID */
    private Long applicationId;

    /** 原状态 */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 操作人用户ID */
    private Long operatorId;

    /** 操作人角色：CANDIDATE/HR/SYSTEM */
    private String operatorRole;

    /** 变更原因 */
    private String reason;

    /** 幂等键 */
    private String idempotentKey;

    /** 变更时间 */
    private LocalDateTime createdAt;
}
