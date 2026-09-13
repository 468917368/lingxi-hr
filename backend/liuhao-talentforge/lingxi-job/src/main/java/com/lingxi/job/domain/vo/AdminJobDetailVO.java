package com.lingxi.job.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 管理员岗位详情视图对象（成员 E 依赖）
 * <p>返回完整 JD、画像、状态原因、HC；不返回 companyName/applyCount（由 E/C 补齐）</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class AdminJobDetailVO implements Serializable {

    private static final long serialVersionUID = 1L;

    // ==================== 岗位基础字段 ====================
    private Long jobId;
    private Long companyId;
    private String title;
    private String status;
    private String pauseReason;
    private String closeReason;
    private String jdText;
    private String jdSummary;
    private Integer totalHc;
    private Integer reservedHc;
    private Integer confirmedHc;
    private Integer availableHc;
    private Integer version;
    private LocalDateTime publishedAt;
    private LocalDateTime closedAt;
    private LocalDateTime expiresAt;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    // ==================== 岗位画像字段 ====================
    private String jobType;
    private List<String> coreSkills;
    private List<String> softSkills;
    private String industryExperience;
    private List<String> interviewFocus;
    private String hiddenRequirements;
    private Boolean profileConfirmed;
}
