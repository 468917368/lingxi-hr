package com.lingxi.chat.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 通知视图对象
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 通知ID */
    private Long id;

    /** 通知类型 */
    private String type;

    /** 通知类型描述 */
    private String typeDesc;

    /** 通知标题 */
    private String title;

    /** 通知内容 */
    private String content;

    /** 关联目标类型 */
    private String targetType;

    /** 关联目标ID */
    private Long targetId;

    /** 是否已读 */
    private Boolean isRead;

    /** 创建时间 */
    private String createdAt;
}
