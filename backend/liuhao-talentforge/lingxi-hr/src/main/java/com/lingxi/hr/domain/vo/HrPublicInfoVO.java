package com.lingxi.hr.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * HR公开信息视图对象（不含隐私数据）
 *
 * @author lingxi-team
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HrPublicInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** HR用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /** 角色 */
    private String role;

    /** 部门 */
    private String department;

    /** 职位 */
    private String position;

    /** 所属公司信息 */
    private CompanyPublicInfoVO company;

    /**
     * 公司公开信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CompanyPublicInfoVO implements Serializable {

        private static final long serialVersionUID = 1L;

        /** 公司ID */
        private Long id;

        /** 公司名称 */
        private String name;

        /** 所属行业 */
        private String industry;

        /** 公司规模 */
        private String scale;

        /** 融资阶段 */
        private String stage;

        /** 所在城市 */
        private String city;

        /** 公司简介 */
        private String description;

        /** 公司Logo */
        private String logo;
    }
}
