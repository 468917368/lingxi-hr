package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * 内部岗位校验详情响应（成员 A/C/D 依赖）
 * <p>不返回 jd_text，canApply = PUBLISHED && 未删除 && availableHc &gt; 0</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class InternalJobValidationResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long jobId;
    private Long companyId;
    private String title;
    private String status;
    private Boolean deleted;
    private Integer totalHc;
    private Integer reservedHc;
    private Integer confirmedHc;
    private Integer availableHc;
    private Boolean canApply;
    private Long salaryMinAmount;
    private Long salaryMaxAmount;
    private Boolean salaryNegotiable;

    // 岗位画像信息（用于匹配计算）
    private String educationRequirement;
    private Integer minExperienceYears;
    private String jdText;
    private Map<String, Object> profile;
}
