package com.lingxi.admin.domain.dto;

import lombok.Data;

/**
 * 下架岗位DTO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class OfflineDTO {

    /**
     * 下架原因（固定 VIOLATION）
     */
    private String reason;

    /**
     * 下架说明（1~500字）
     */
    private String remark;
}
