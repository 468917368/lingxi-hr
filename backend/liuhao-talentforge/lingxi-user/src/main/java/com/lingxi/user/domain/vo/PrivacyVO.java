package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 隐私设置视图对象
 * <p>
 * 对齐PRD 5.7.5 隐私设置
 * </p>
 *
 * @author 成员A
 * @since 2026-08-01
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PrivacyVO {

    /** 简历是否公开 */
    private Boolean resumePublic;

    /** 匹配通知 */
    private Boolean matchNotify;

    /** 当前状态：JOB_SEEKING/EMPLOYED_LOOKING/NOT_LOOKING */
    private String jobStatus;

    /** 盲选模式 */
    private Boolean blindMode;
}
