package com.lingxi.job.domain.dto.request;

import lombok.Data;

import javax.validation.constraints.NotNull;
import java.io.Serializable;

/**
 * 收藏/取消收藏请求
 * <p>favorited 必须显式传入（包装类型 Boolean + @NotNull）：漏传/传 null → HTTP 400，
 * 不会被误判为取消收藏（false）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class FavoriteRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 目标收藏状态：true=收藏，false=取消收藏（均幂等） */
    @NotNull(message = "favorited 不能为空")
    private Boolean favorited;
}
