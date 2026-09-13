package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 企业信息视图对象
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompanyVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID */
    private Long id;

    /** 企业名称 */
    private String name;

    /** 认证状态 */
    private String certStatus;
}
