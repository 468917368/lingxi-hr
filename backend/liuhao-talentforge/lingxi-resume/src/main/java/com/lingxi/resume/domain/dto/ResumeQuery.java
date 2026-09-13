package com.lingxi.resume.domain.dto;

import lombok.Data;

/**
 * 简历条件查询DTO
 *
 * @author 成员C
 * @since 2026-08-01
 */
@Data
public class ResumeQuery {

    /** 求职者用户ID（必传，数据隔离） */
    private Long candidateId;

    /** 解析状态（可选） */
    private String parseStatus;

    /** 是否默认简历（可选） */
    private Integer isDefault;
}
