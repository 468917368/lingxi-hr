package com.lingxi.hr.agent.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 简历详情 DTO（来自 lingxi-resume /api/v1/resumes/{id}）
 * <p>仅保留出题需要字段，其余忽略。</p>
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeDetailDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简历ID */
    private Long id;

    /** 是否默认简历：1=是 0=否 */
    private Integer isDefault;

    /** 解析状态：COMPLETED/FAILED/PENDING/PARSING */
    private String parseStatus;

    /** 解析后的 Markdown 简历 URL */
    private String resumeMdUrl;

    /** 卡片结构（JSON 对象，技能/项目/教育等章节） */
    private Object cardStructure;

    /** 候选人姓名 */
    private String candidateName;
}
