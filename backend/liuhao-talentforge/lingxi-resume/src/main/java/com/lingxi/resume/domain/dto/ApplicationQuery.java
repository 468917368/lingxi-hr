package com.lingxi.resume.domain.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 投递记录条件查询DTO
 *
 * <p>C 端列表必传 candidateId（数据隔离）；
 * HR 端列表必传 companyId，status/jobId 可选筛选。
 *
 * @author 成员C
 * @since 2026-08-04
 */
@Data
public class ApplicationQuery {

    /** 求职者用户ID（C端必传） */
    private Long candidateId;

    /** 企业ID（HR端必传，经 job_post JOIN 匹配） */
    private Long companyId;

    /** 岗位ID（可选筛选） */
    private Long jobId;

    /** 投递状态（可选筛选） */
    private String status;

    /** 最低匹配分筛选（可选） */
    private BigDecimal minMatchScore;

    /** 候选人姓名模糊搜索（可选） */
    private String keyword;

    /** 排序字段（可选，Service 层白名单校验：matchScore / submittedAt） */
    private String sortBy;

    /** 投递ID集合过滤（HR 面试官本人负责范围，可选；为空不生效） */
    private List<Long> applicationIds;
}
