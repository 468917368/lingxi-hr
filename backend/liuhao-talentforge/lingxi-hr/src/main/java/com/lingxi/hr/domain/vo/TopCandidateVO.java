package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.List;

/**
 * Top5 高潜推荐项（系分文档 5.5.2）
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
public class TopCandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 排名（从 1 起） */
    private Integer rank;

    /** 候选人用户ID */
    private Long candidateId;

    /** 投递记录ID（前端「查看简历」需用此调 GET /candidates/{applicationId}/resume） */
    private Long applicationId;

    /** 候选人姓名 */
    private String candidateName;

    /** 投递匹配度(0-100) */
    private BigDecimal matchScore;

    /** AI匹配分（C 侧预留，暂为 null） */
    private BigDecimal aiScore;

    /** 优势（C/Job Agent 生成，未就绪可空） */
    private List<String> advantages;

    /** 风险（同上，可空） */
    private List<String> risks;
}
