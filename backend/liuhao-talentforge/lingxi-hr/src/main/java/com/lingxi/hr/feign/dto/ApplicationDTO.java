package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 投递记录 DTO（来自 lingxi-resume）
 *
 * <p>字段对齐成员C {@code InternalApplicationVO} 契约（2026-08-03 确认）：
 * companyId 用于跨企业隔离校验；advantages/risks 供 Top5 展示（C/Job Agent 生成，未就绪可空）。
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class ApplicationDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private Long jobId;
    private String jobTitle;
    private Long candidateId;
    private Long resumeId;
    private String status;
    private BigDecimal matchScore;
    private BigDecimal aiScore;
    private LocalDateTime appliedAt;
    /** 投递归属企业（跨企业隔离校验） */
    private Long companyId;
    /** Top5 优势（C/Job Agent 生成，可空） */
    private List<String> advantages;
    /** Top5 风险（同上，可空） */
    private List<String> risks;
}
