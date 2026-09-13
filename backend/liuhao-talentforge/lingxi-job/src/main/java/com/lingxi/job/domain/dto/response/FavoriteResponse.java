package com.lingxi.job.domain.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 收藏/取消收藏响应（幂等返回当前目标状态）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FavoriteResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位ID */
    private Long jobId;

    /** 目标收藏状态（本次请求 favorited 值，幂等返回） */
    private boolean favorited;
}
