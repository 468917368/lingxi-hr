package com.lingxi.job.domain.vo;

import com.lingxi.job.domain.dto.request.JobCreateRequest;
import com.lingxi.job.domain.dto.response.JobRequirementResponse;
import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * HR 岗位详情（完整字段 + 画像段）
 *
 * @author lingxi-team
 * @since 2026-08-03
 */
@Data
public class HrJobDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 岗位基础字段 ====================
    private Long jobId;
    private Long companyId;
    private String title;
    private String status;
    private String industryGroupCode;
    private String industryCode;
    private String cityCode;
    private String cityName;
    private Integer minExperienceYears;
    private String educationRequirement;
    private SalaryVO salary;
    private Integer totalHc;
    private Integer reservedHc;
    private Integer confirmedHc;
    private Integer availableHc;
    private String jdText;
    private String jdSummary;
    private String pauseReason;
    private String closeReason;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** 创建人用户ID（复用 job_post.created_by） */
    private Long createdBy;

    /** 创建人姓名（Service 组装；用户服务不可用/缺失时降级为 "用户"+id） */
    private String createdByName;

    private Integer version;

    // ==================== 岗位画像字段 ====================
    private String jobType;
    private List<JobRequirementResponse.CoreSkill> coreSkills;
    private List<JobRequirementResponse.SoftSkill> softSkills;
    private String industryExperience;
    private List<JobCreateRequest.HiddenRequirement> hiddenRequirements;
    private List<String> interviewFocus;
    private String profileSource;
    private Long confirmedBy;
    private LocalDateTime confirmedAt;
    private Boolean profileConfirmed;
    private Integer profileVersion;

    /**
     * 薪资展示结构
     */
    @Data
    public static class SalaryVO implements Serializable {

        private static final long serialVersionUID = 1L;

        private Long minAmount;
        private Long maxAmount;
        private String currency;
        private String period;
        private Integer months;
        private Boolean negotiable;
        private String rawText;
    }
}
