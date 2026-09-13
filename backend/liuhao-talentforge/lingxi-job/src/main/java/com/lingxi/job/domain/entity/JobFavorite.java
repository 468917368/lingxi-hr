package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位收藏表实体
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobFavorite implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 收藏ID */
    private Long id;

    /** 求职者用户ID */
    private Long candidateId;

    /** 岗位ID */
    private Long jobId;

    /** 收藏时间 */
    private LocalDateTime createdAt;
}
