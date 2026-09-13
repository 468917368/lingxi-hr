package com.lingxi.user.domain.dto;

import lombok.Data;

import javax.validation.constraints.Pattern;

/**
 * 更新隐私设置请求
 * <p>
 * 对齐前端 services/user.ts PrivacySettings
 * 对齐PRD 5.7.5 隐私设置
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
public class UpdatePrivacyRequest {

    /** 简历是否公开 */
    private Boolean resumePublic;

    /** 匹配通知 */
    private Boolean matchNotify;

    /** 当前状态：JOB_SEEKING/EMPLOYED_LOOKING/NOT_LOOKING */
    @Pattern(regexp = "^(JOB_SEEKING|EMPLOYED_LOOKING|NOT_LOOKING)$", message = "求职状态值不合法")
    private String jobStatus;

    /** 盲选模式 */
    private Boolean blindMode;
}
