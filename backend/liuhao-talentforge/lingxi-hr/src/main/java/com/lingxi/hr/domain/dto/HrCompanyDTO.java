package com.lingxi.hr.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import java.io.Serializable;

/**
 * 企业信息 DTO
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class HrCompanyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业全称 */
    @NotBlank(message = "企业名称不能为空")
    private String name;

    /** 企业简称 */
    private String shortName;

    /** 企业简介 */
    private String description;

    /** 所属行业 */
    @NotBlank(message = "所属行业不能为空")
    private String industry;

    /** 企业规模 */
    @NotBlank(message = "企业规模不能为空")
    private String scale;

    /** 企业Logo URL */
    private String logoUrl;

    /** 详细办公地址 */
    private String address;

    /** 企业官网 */
    private String website;
}
