package com.lingxi.job.domain.dto.response;

import lombok.Data;

import java.io.Serializable;

/**
 * HC 预冻结响应
 *
 * @author lingxi-team
 * @since 2026-08-02
 */
@Data
public class HcReserveResponse implements Serializable {

    private static final long serialVersionUID = 1L;

    private String reservationStatus;
    private Integer totalHc;
    private Integer reservedHc;
    private Integer confirmedHc;
    private Integer availableHc;
    private String jobStatus;
    private Integer jobVersion;
    private Integer reservationVersion;
}
