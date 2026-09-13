package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 用户公开信息视图对象（不含隐私数据）
 *
 * @author lingxi-team
 * @since 2026-08-10
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserPublicInfoVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 用户ID */
    private Long id;

    /** 姓名 */
    private String name;

    /** 头像 */
    private String avatar;

    /** 角色 */
    private String role;

    /** 工作年限 */
    private String workYears;

    /** 学历 */
    private String education;

    /** 所在城市 */
    private String city;

    /** 求职状态：JOB_SEEKING=求职中 EMPLOYED_LOOKING=在职看机会 NOT_LOOKING=暂不考虑 */
    private String jobStatus;

    /** 期望岗位 */
    private String desiredJob;

    /** 期望城市 */
    private String desiredCity;

    /** 到岗时间 */
    private String availableFrom;

    /** 技能标签（从简历提取） */
    private String skills;
}
