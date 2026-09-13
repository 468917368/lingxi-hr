package com.lingxi.hr.domain.vo;

import lombok.Data;

import java.io.Serializable;

/**
 * 邀请码加入企业结果 VO
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrJoinCompanyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID */
    private Long companyId;

    /** 企业名称 */
    private String companyName;

    /** 加入后的成员角色：HR_ADMIN */
    private String role;

    /** 成员状态：ACTIVE */
    private String status;
}
