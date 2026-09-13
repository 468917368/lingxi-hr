package com.lingxi.job.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 简历详情 DTO（Feign 调 lingxi-resume {@code GET /api/v1/resumes/{id}} 返回）
 * <p>字段对齐 resume 的 {@code ResumeDetailVO}；{@code cardStructure} 为简历结构化卡片
 * JSON（{@code sections[].points[]}），供 Interview Agent 提取亮点与计算完整度。</p>
 *
 * @author 成员B
 * @since 2026-08-05
 */
@Data
public class ResumeDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简历ID */
    private Long id;

    /** 解析状态（PARSED/FAILED 等） */
    private String parseStatus;

    /** 简历结构化卡片 JSON（sections[].points[]） */
    private Object cardStructure;
}
