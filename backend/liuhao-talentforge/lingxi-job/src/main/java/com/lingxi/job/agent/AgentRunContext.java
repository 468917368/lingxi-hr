package com.lingxi.job.agent;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Interview Agent 运行上下文（runToken 绑定）
 * <p>
 * 一次出题运行一个上下文，由百宝箱 Tool 回调（{@code X-Run-Token}）读取，
 * 用于跨请求传递岗位/简历/企业信息，并做跨企业数据隔离校验。
 * </p>
 * <p>
 * TTL 300s：超过有效期后 Tool 回调拒绝（401），由 {@code AgentContextStore} 定时清理。
 * </p>
 *
 * @author 成员B
 * @since 2026-08-04
 */
@Data
public class AgentRunContext implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 上下文有效期（秒） */
    public static final long TTL_SECONDS = 300L;

    /** 运行令牌（32 位 hex，Tool 回调携带） */
    private String runToken;

    /** 企业ID（跨企业数据隔离） */
    private Long companyId;

    /** 岗位ID */
    private Long jobId;

    /** 标准化岗位类型（供题库搜索 Tool 读取） */
    private String jobType;

    /** 岗位画像解析出的标准化技能标签（供企业私有题匹配使用） */
    private List<String> skillTags;

    /** 投递记录ID */
    private Long applicationId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 脱敏后的简历亮点 */
    private List<String> resumeHighlights;

    /** 简历完整度（0~1，BigDecimal 原始值；SSE 发送处转 SUFFICIENT/INSUFFICIENT 枚举） */
    private BigDecimal dataCompleteness;

    /** 是否个性化出题（dataCompleteness ≥ 0.5 且校验通过）；false 时跳过简历上下文、通用题 */
    private Boolean personalized;

    /** 过期时间（创建时间 + TTL） */
    private LocalDateTime expiresAt;
}
