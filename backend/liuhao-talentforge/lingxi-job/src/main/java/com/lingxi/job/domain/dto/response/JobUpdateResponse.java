package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位编辑响应
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobUpdateResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位状态 */
    private String status;

    /** 更新后岗位版本 */
    private Integer version;

    /** 更新后岗位画像版本（未改画像时返回当前值） */
    private Integer profileVersion;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
