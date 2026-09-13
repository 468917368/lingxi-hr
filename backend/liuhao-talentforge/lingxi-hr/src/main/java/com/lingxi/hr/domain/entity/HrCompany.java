package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 企业信息表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrCompany implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID */
    private Long id;

    /** 企业全称（认证后不可修改） */
    private String name;

    /** 企业简称 */
    private String shortName;

    /** 企业简介 */
    private String description;

    /** 所属行业 */
    private String industry;

    /** 企业规模：0-50/50-100/100-500/500-2000/2000+ */
    private String scale;

    /** 企业Logo URL */
    private String logoUrl;

    /** 详细办公地址 */
    private String address;

    /** 企业官网 */
    private String website;

    /** 企业邀请码（6位） */
    private String inviteCode;

    /** 营业执照URL */
    private String businessLicenseUrl;

    /** 认证状态：PENDING/APPROVED/REJECTED */
    private String certStatus;

    /** 认证拒绝原因 */
    private String certRejectReason;

    /** 企业状态：ACTIVE/DISABLED */
    private String status;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
