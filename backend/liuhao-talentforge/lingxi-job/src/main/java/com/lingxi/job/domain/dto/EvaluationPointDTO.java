package com.lingxi.job.domain.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 评分要点 DTO（跨层传输：QuestionCreateRequest/UpdateRequest 输入、HrQuestionDetailVO 展示、Service 校验）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class EvaluationPointDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 评分维度名称 */
    private String name;

    /** 权重（0~1） */
    private Double weight;
}
