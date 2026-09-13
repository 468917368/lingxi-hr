package com.lingxi.job.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HC预冻结流水表实体
 * <p>每个 Offer 对应一条 HC 流水</p>
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class JobHcReservation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** HC流水ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 岗位ID */
    private Long jobId;

    /** Offer业务ID */
    private Long offerId;

    /** 候选人用户ID */
    private Long candidateId;

    /** 流水状态：RESERVED/CONFIRMED/RELEASED */
    private String status;

    /** 预冻结时间 */
    private LocalDateTime reservedAt;

    /** 确认占用时间 */
    private LocalDateTime confirmedAt;

    /** 释放时间 */
    private LocalDateTime releasedAt;

    /** 释放原因编码 */
    private String releaseReason;

    /** 乐观锁版本号 */
    private Integer version;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
