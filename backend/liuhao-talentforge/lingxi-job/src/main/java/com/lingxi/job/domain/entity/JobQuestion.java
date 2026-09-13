package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业私有题库表实体
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobQuestion implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业题目ID */
    private Long id;

    /** 企业ID（题库隔离） */
    private Long companyId;

    /** 通用岗位类型 */
    private String jobType;

    /** 标准化技能标签数组 JSON */
    private String skillTags;

    /** 题目类型：BASIC/PROJECT/BOUNDARY/COMPREHENSIVE */
    private String questionType;

    /** 难度：EASY/MEDIUM/HARD */
    private String difficulty;

    /** 题目内容 */
    private String content;

    /** 标准化题干SHA-256（企业内精确去重） */
    private String contentSha256;

    /** 考察要点 */
    private String keyPoints;

    /** 参考答案 */
    private String referenceAnswer;

    /** 结构化评分要点 JSON */
    private String evaluationPoints;

    /** 来源：DEMO_SEED/HR_CREATED/AI_GENERATED */
    private String source;

    /** 状态：PENDING_REVIEW/ACTIVE/INACTIVE/REJECTED */
    private String status;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建人用户ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;

    /** 逻辑删除时间 */
    private LocalDateTime deletedAt;

    /** 审核人用户ID */
    private Long reviewedBy;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 审核意见/拒绝原因 */
    private String reviewReason;
}
