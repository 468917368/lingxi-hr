package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业认证状态 VO（pending 审核页使用）
 *
 * @author 成员D
 * @since 2026-08-03
 */
@Data
public class HrCertificationStatusVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 认证状态：PENDING=待审核 APPROVED=已通过 REJECTED=已拒绝 */
    private String certStatus;

    /** 认证拒绝原因（未拒绝为 null） */
    private String certRejectReason;

    /** 认证申请提交时间 */
    private LocalDateTime submittedAt;
}
