package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位状态变更日志表实体
 * <p>每次状态变更写入一条，与 job_post 状态更新同一事务</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobStatusLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 岗位ID */
    private Long jobId;

    /** 原状态（首次创建时为NULL） */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 变更原因编码 */
    private String reason;

    /** 补充说明 */
    private String reasonDetail;

    /** 操作人（0=SYSTEM） */
    private Long operatorId;

    /** 操作人角色：HR/SYSTEM/ADMIN */
    private String operatorRole;

    /** 请求追踪ID */
    private String requestId;

    /** 变更时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
