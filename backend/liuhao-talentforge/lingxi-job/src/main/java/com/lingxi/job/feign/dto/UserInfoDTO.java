package com.lingxi.job.feign.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.io.Serializable;

/**
 * lingxi-user 用户基础信息 DTO（内部接口载荷）
 * <p>仅声明本模块消费字段（id/name），其余字段忽略。</p>
 *
 * @author lingxi-team
 * @since 2026-08-08
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class UserInfoDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 用户姓名 */
    private String name;

    /** 用户当前企业（C 端企业归属校验用；未入企/已离职时为 null） */
    private UserCompanyDTO company;
}
