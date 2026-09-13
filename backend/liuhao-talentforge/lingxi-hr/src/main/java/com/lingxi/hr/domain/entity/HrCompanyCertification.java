package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业认证申请表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrCompanyCertification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 申请ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 申请人用户ID */
    private Long applicantId;

    /** 营业执照URL */
    private String businessLicenseUrl;

    /** 其他证明材料URL */
    private String certMaterialUrl;

    /** 审核状态：PENDING/APPROVED/REJECTED */
    private String status;

    /** 拒绝原因 */
    private String rejectReason;

    /** 审核人管理员ID */
    private Long reviewerId;

    /** 审核时间 */
    private LocalDateTime reviewedAt;

    /** 申请时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
