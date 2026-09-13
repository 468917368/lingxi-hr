package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位状态变更历史视图（status-history 接口返回）
 * <p>字段对齐系分 F-25：GET /api/v1/hr/jobs/{jobId}/status-history。</p>
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Data
public class JobStatusLogVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 日志ID */
    private Long id;

    /** 原状态（首次创建时为 NULL） */
    private String fromStatus;

    /** 目标状态 */
    private String toStatus;

    /** 变更原因编码（MANUAL_PUBLISH/MANUAL/REOPEN/VIOLATION/EXPIRED/HC_* 等） */
    private String reason;

    /** 补充说明（违规下架 remark 等） */
    private String reasonDetail;

    /** 操作人（0=SYSTEM） */
    private Long operatorId;

    /** 操作人角色：HR/SYSTEM/ADMIN */
    private String operatorRole;

    /** 请求追踪ID（本阶段统一 null） */
    private String requestId;

    /** 变更时间 */
    private LocalDateTime createdAt;
}
