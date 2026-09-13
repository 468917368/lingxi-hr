package com.lingxi.job.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * lingxi-user 用户当前企业 DTO（内部接口载荷，UserInfoDTO.company 嵌套）
 * <p>仅读取 id/name，用于 C 端企业归属校验（user.company.id == job.company_id 才回填企业名）。</p>
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserCompanyDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 企业ID */
    private Long id;

    /** 企业名称 */
    private String name;
}
