package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审核历史VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AuditHistoryVO {

    /** 审核时间 */
    private LocalDateTime createdAt;

    /** 审核人 */
    private String reviewerName;

    /** 审核结果：APPROVED/REJECTED */
    private String result;

    /** 审核原因 */
    private String reason;
}
