package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位创建响应
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class JobCreateResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 岗位状态 */
    private String status;

    /** 岗位版本 */
    private Integer version;

    /** 岗位画像版本 */
    private Integer profileVersion;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
