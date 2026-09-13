package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 候选人列表项（系分文档 5.5.2）
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
public class CandidateVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 投递记录ID */
    private Long id;

    /** 候选人用户ID */
    private Long candidateId;

    /** 候选人姓名（来自 lingxi-user，降级为"用户"+id） */
    private String candidateName;

    /** 候选人手机号（脱敏，Feign 降级为 ***） */
    private String phone;

    /** 候选人头像 */
    private String avatar;

    /** 岗位ID */
    private Long jobId;

    /** 岗位名称 */
    private String jobTitle;

    /** 投递状态：SUBMITTED/VIEWED/SCREENED/INTERVIEWING/OFFERABLE/OFFERED/REJECTED/WITHDRAWN */
    private String status;

    /** 投递匹配度(0-100)，投递时 Job Agent 计算快照 */
    private BigDecimal matchScore;

    /** AI匹配分（C 侧预留，暂为 null） */
    private BigDecimal aiScore;

    /** 投递时间 */
    private LocalDateTime appliedAt;

    /** 该投递是否已存在 Offer 记录（含 WITHDRAWN/EXPIRED/REJECTED 等终态；前端据此禁用「发起 Offer」按钮，2026-08-07 前端对齐） */
    private Boolean hasOfferRecord;

    /** 该投递最新一条 Offer 的状态：SENT/ACCEPTED/REJECTED/EXPIRED/WITHDRAWN，无 Offer 记录则 null（2026-08-07 前端清单） */
    private String lastOfferStatus;
}
