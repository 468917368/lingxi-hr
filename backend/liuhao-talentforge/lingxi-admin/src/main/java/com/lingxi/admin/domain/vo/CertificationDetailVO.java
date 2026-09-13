package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.util.List;

/**
 * 审核详情VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CertificationDetailVO {

    /** 申请ID */
    private Long id;

    /** 企业名称 */
    private String companyName;

    /** 行业 */
    private String industry;

    /** 规模 */
    private String scale;

    /** 地址 */
    private String address;

    /** 联系人 */
    private String contactPerson;

    /** 联系电话 */
    private String contactPhone;

    /** 营业执照URL */
    private String licenseUrl;

    /** 认证材料URL */
    private String certMaterialUrl;

    /** 认证状态 */
    private String certStatus;

    /** 拒绝原因 */
    private String certRejectReason;

    /** 证明材料列表 */
    private List<String> materials;

    /** 审核历史 */
    private List<AuditHistoryVO> history;
}
