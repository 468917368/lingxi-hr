package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 审核列表项VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CertificationVO {

    /** 申请ID */
    private Long id;

    /** 申请人姓名 */
    private String applicantName;

    /** 联系电话 */
    private String contactPhone;

    /** 企业名称 */
    private String companyName;

    /** 行业 */
    private String industry;

    /** 规模 */
    private String scale;

    /** 地址 */
    private String address;

    /** 营业执照URL */
    private String licenseUrl;

    /** 认证材料URL */
    private String certMaterialUrl;

    /** 申请时间 */
    private LocalDateTime applyTime;

    /** 状态：PENDING/APPROVED/REJECTED */
    private String status;
}
