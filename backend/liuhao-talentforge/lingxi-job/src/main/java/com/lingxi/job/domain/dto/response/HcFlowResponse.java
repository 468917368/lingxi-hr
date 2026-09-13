package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * HC 流水查询响应（成员D HC 补偿对账用）
 * <p>返回 Offer 对应流水的当前状态与关键时间点，系分 §21 对账任务据此判断是否调用 release。</p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class HcFlowResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    /** Offer业务ID */
    private Long offerId;

    /** 岗位ID */
    private Long jobId;

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
}
