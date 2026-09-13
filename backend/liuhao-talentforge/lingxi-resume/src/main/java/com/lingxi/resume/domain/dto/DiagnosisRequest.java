package com.lingxi.resume.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 简历诊断请求
 *
 * @author 成员C
 * @since 2026-08-06
 */
@Data
public class DiagnosisRequest {

    /** 目标职业名（如"高级前端工程师"，不关联平台岗位） */
    @NotBlank(message = "职业名不能为空")
    private String career;

    /**
     * 是否强制重新生成（2026-08-08 新增）
     *
     * <p>false：命中缓存（同简历+同职业且简历未修改）直接返回历史报告；
     * true：跳过缓存，重新调用诊断 Agent 生成新报告。
     */
    private Boolean refresh;
}
