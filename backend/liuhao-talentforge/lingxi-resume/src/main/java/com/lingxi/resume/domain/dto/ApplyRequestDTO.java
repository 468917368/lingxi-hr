package com.lingxi.resume.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotNull;

/**
 * 一键投递请求DTO
 *
 * <p>对齐契约：《后端总体系分文档.md》4.4.2-6 投递岗位。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class ApplyRequestDTO {

    /** 岗位ID（必传） */
    @NotNull(message = "jobId不能为空")
    private Long jobId;

    /** 使用的简历ID（可选，为空则使用默认简历） */
    private Long resumeId;
}
