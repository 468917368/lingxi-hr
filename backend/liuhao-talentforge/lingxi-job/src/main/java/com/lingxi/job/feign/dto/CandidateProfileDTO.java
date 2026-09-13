package com.lingxi.job.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 用户画像推荐参数 DTO（画像推荐模式）
 * <p>
 * 镜像 lingxi-user {@code GET /internal/users/{id}/profile} 契约（{@code UserProfileVO}）中的
 * 推荐相关字段，本地解耦：仅保留六个推荐维度，避免依赖只读模块的类型。
 * </p>
 *
 * @author lingxi-team
 * @since 2026-08-06
 */
@Data
public class CandidateProfileDTO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 期望岗位（用户输入，可含 % _ = 通配符，SQL 侧需转义） */
    private String desiredJob;

    /** 期望城市（逗号分隔，可能含空格/中文逗号，SQL 前需规范化） */
    private String desiredCity;

    /** 期望最低薪资（元，INT 非负，SQL 前转分） */
    private Integer desiredSalaryMin;

    /** 期望最高薪资（元，INT 非负，SQL 前转分） */
    private Integer desiredSalaryMax;

    /** 工作年限：FRESH/1-3/3-5/5-10/10+ */
    private String workYears;

    /** 最高学历：COLLEGE/BACHELOR/MASTER/PHD */
    private String education;
}
