package com.lingxi.hr.agent.feign;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * 简历列表项 DTO（来自 lingxi-resume /api/v1/resumes，取 isDefault 简历用）
 *
 * @author 成员D
 * @since 2026-08-04
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResumeListItemDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 简历ID */
    private Long id;

    /** 是否默认简历：1=是 0=否 */
    private Integer isDefault;

    /** 解析状态 */
    private String parseStatus;
}
