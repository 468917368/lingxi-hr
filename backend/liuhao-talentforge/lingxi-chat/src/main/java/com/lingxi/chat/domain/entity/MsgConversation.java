package com.lingxi.chat.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话表实体
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class MsgConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private Long id;

    /** 企业ID */
    private Long companyId;

    /** 求职者用户ID */
    private Long candidateId;

    /** HR用户ID */
    private Long hrId;

    /** 关联投递记录ID（咨询HR入口创建时可能为空） */
    private Long applicationId;

    /** 最后一条消息摘要（前50字） */
    private String lastMessagePreview;

    /** 最后消息时间 */
    private LocalDateTime lastMessageAt;

    /** 求职者未读数 */
    private Integer candidateUnread;

    /** HR未读数 */
    private Integer hrUnread;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
