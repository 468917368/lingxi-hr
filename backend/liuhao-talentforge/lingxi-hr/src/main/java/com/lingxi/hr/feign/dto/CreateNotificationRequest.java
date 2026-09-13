package com.lingxi.hr.feign.dto;

import lombok.Data;

import java.io.Serializable;

/**
 * 创建通知请求（对齐 lingxi-chat {@code InternalNotificationController.CreateNotificationRequest}）
 *
 * @author 成员D
 * @since 2026-08-05
 */
@Data
public class CreateNotificationRequest implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 接收用户ID（候选人） */
    private Long userId;

    /** 通知类型（lingxi-chat ChatConstant 枚举，候选人侧 RESUME_VIEWED 等） */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联目标类型：application/interview/offer/job */
    private String targetType;

    /** 关联目标ID */
    private Long targetId;
}
