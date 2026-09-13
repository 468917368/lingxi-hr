package com.lingxi.chat.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 通知表实体
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class SysNotification implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 接收用户ID */
    private Long userId;

    /** 通知类型：APPLICATION/INTERVIEW/OFFER/SYSTEM/JOB_RECOMMEND */
    private String type;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联目标类型：application/interview/offer */
    private String targetType;

    /** 关联目标ID */
    private Long targetId;

    /** 是否已读：0=否 1=是 */
    private Integer isRead;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
