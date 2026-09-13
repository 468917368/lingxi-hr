package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业信息 VO
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrCompanyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    private Long id;
    private String name;
    private String shortName;
    private String description;
    private String industry;
    private String scale;
    private String logoUrl;
    private String address;
    private String website;
    private String inviteCode;
    private String certStatus;
    private String certRejectReason;
    private String status;
    private LocalDateTime createdAt;
}
