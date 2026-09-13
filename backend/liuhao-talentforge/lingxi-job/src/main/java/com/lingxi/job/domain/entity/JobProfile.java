package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 岗位画像表实体（与 job_post 一对一）
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobProfile implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 岗位画像ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 岗位ID */
    private Long jobId;

    /** 标准化岗位类型 */
    private String jobType;

    /** 核心技能 JSON：[{name,level,required,basis,confidence}] */
    private String coreSkills;

    /** 软能力 JSON：[{name,importance,inferred,confidence}] */
    private String softSkills;

    /** 行业或业务经验要求 */
    private String industryExperience;

    /** 隐性要求 JSON：[{requirement,basis,inferred,confidence,hr_confirmed}] */
    private String hiddenRequirements;

    /** 面试考察重点 JSON */
    private String interviewFocus;

    /** 画像来源：AI/MANUAL/MIXED */
    private String profileSource;

    /** 岗位画像版本 */
    private Integer version;

    /** 最后确认画像的HR用户ID */
    private Long confirmedBy;

    /** 最后确认时间 */
    private LocalDateTime confirmedAt;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
