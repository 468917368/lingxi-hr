package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 企业详情VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class CompanyDetailVO {

    /** 企业ID */
    private Long id;

    /** 企业名称 */
    private String name;

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
    private String businessLicenseUrl;

    /** 认证材料URL */
    private String certMaterialUrl;

    /** 认证状态 */
    private String certStatus;

    /** 拒绝原因 */
    private String certRejectReason;

    /** 认证时间 */
    private LocalDateTime certTime;

    /** 成员列表 */
    private List<CompanyMemberVO> members;

    /** 岗位数 */
    private Integer jobCount;
}
