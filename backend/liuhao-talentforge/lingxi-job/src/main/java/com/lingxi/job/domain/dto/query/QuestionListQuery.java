package com.lingxi.job.domain.dto.query;

import lombok.Data;

import java.io.Serializable;

/**
 * HR 题库分页列表查询条件（阶段6.1）
 * <p>companyId 由 Service 从 UserContext 注入，显式传 Mapper（不信任前端）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class QuestionListQuery implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位类型（可选） */
    private String jobType;

    /** 题目类型（可选，传了须 ∈ 4 标准题型） */
    private String questionType;

    /** 难度（可选，未传=不过滤；传了须 ∈ EASY/MEDIUM/HARD） */
    private String difficulty;

    /** 状态（可选，传了须 ∈ 状态枚举） */
    private String status;

    /** 页码（1~100） */
    private Integer page;

    /** 每页条数（1~50） */
    private Integer size;
}
