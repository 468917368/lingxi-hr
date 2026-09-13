package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知记录表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class MsgNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 接收用户ID */
    private Long userId;

    /** 通知类型：APPLICATION/INTERVIEW/OFFER/HC_WARNING/EVAL_TIMEOUT/JOB_REFRESH/SYSTEM */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联目标类型：application/interview/offer/job */
    private String targetType;

    /** 关联目标ID */
    private Long targetId;

    /** 是否已读：0=未读 1=已读 */
    private Integer isRead;

    /** 通知时间 */
    private LocalDateTime createdAt;
}
