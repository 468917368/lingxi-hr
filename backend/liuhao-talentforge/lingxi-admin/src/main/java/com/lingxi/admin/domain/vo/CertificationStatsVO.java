package com.lingxi.admin.domain.vo;

import lombok.Data;

/**
 * 审核统计VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CertificationStatsVO {

    /** 待审核数 */
    private Integer pendingCount;

    /** 已通过数 */
    private Integer approvedCount;

    /** 已拒绝数 */
    private Integer rejectedCount;

    /** 本周新增数 */
    private Integer weekNewCount;
}
