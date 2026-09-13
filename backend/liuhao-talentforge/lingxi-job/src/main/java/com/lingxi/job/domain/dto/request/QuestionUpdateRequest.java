package com.lingxi.job.domain.dto.request;

import com.lingxi.job.domain.dto.EvaluationPointDTO;
import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.NotNull;
import java.io.Serializable;
import java.util.List;

/**
 * 题库编辑请求（阶段6.1，乐观锁 version）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class QuestionUpdateRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 乐观锁版本号（必填） */
    @NotNull(message = "版本号不能为空")
    private Integer version;

    /** 通用岗位类型（必填） */
    @NotBlank(message = "岗位类型不能为空")
    private String jobType;

    /** 题目类型（必填） */
    @NotBlank(message = "题目类型不能为空")
    private String questionType;

    /** 难度（可选，null/空→MEDIUM） */
    private String difficulty;

    /** 题干（必填，≤2000） */
    @NotBlank(message = "题干不能为空")
    private String content;

    /** 标准化技能标签数组（可选，≤10） */
    private List<String> skillTags;

    /** 考察要点（可选） */
    private String keyPoints;

    /** 参考答案（可选） */
    private String referenceAnswer;

    /** 评分要点（可选，提供则非空数组） */
    private List<EvaluationPointDTO> evaluationPoints;
}
