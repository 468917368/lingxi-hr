package com.lingxi.job.domain.vo;

import com.lingxi.job.domain.dto.EvaluationPointDTO;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HR 题库详情（阶段6.1，完整字段含审核信息）
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class HrQuestionDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 题目ID */
    private Long id;

    /** 通用岗位类型 */
    private String jobType;

    /** 标准化技能标签 */
    private List<String> skillTags;

    /** 题目类型 */
    private String questionType;

    /** 难度 */
    private String difficulty;

    /** 题干 */
    private String content;

    /** 考察要点 */
    private String keyPoints;

    /** 参考答案 */
    private String referenceAnswer;

    /** 评分要点 */
    private List<EvaluationPointDTO> evaluationPoints;

    /** 来源 */
    private String source;

    /** 状态 */
    private String status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建人用户ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 审核人用户ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 审核意见/拒绝原因 */
    private String reviewReason;
}
