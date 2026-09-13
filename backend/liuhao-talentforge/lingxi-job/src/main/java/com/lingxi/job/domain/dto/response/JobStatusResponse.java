package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位状态变更响应
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobStatusResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 变更后岗位状态 */
    private String status;

    /** 变更后岗位版本 */
    private Integer version;

    /** 发布时间（REOPEN 后刷新为当前时间） */
    private LocalDateTime publishedAt;

    /** 关闭时间 */
    private LocalDateTime closedAt;

    /** 关闭原因（REOPEN 后清空） */
    private String closeReason;
}
