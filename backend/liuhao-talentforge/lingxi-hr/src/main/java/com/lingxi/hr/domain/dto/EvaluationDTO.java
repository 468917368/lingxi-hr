package com.lingxi.hr.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 录入面试评估入参（系分文档 5.5.3）
 *
 * <p>校验规则在 Service 层按 {@code isDraft} 分流：草稿跳过评语 ≥20 字/评分范围校验；
 * 正式提交全量校验。DB 各字段 NOT NULL，草稿也需提交结论 + 四维评分。
 *
 * @author 成员D
 * @since 2026-08-06
 */
@Data
public class EvaluationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 面试结论：PASS/PENDING/REJECT */
    private String conclusion;

    /** 技术能力评分(1-5) */
    private Integer techScore;

    /** 沟通表达评分(1-5) */
    private Integer communicationScore;

    /** 岗位匹配评分(1-5) */
    private Integer matchScore;

    /** 发展潜力评分(1-5) */
    private Integer potentialScore;

    /** 面试评语（正式提交最少20字） */
    private String comment;

    /** 是否草稿：true=保存草稿（跳过必填/评语校验），false/空=正式提交 */
    private Boolean isDraft;
}
