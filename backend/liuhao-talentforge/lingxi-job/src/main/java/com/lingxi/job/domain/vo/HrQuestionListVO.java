package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HR 题库列表项（阶段6.1）
 * <p>列表脱敏：不含 referenceAnswer/keyPoints/evaluationPoints（考察评分内容）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class HrQuestionListVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目ID */
    private Long id;

    /** 通用岗位类型 */
    private String jobType;

    /** 题目类型 */
    private String questionType;

    /** 难度 */
    private String difficulty;

    /** 题干 */
    private String content;

    /** 标准化技能标签 */
    private List<String> skillTags;

    /** 来源 */
    private String source;

    /** 状态 */
    private String status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
