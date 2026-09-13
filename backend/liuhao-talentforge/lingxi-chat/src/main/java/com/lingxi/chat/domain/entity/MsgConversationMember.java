package com.lingxi.chat.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 会话成员表实体
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class MsgConversationMember implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 用户ID */
    private Long userId;

    /** 成员角色：CANDIDATE/HR */
    private String memberRole;

    /** 未读消息数 */
    private Integer unreadCount;

    /** 是否置顶：0=否 1=是 */
    private Integer isTop;

    /** 是否免打扰：0=否 1=是 */
    private Integer isMuted;

    /** 是否删除：0=否 1=是（仅对自己隐藏） */
    private Integer isDeleted;

    /** 最后已读时间 */
    private LocalDateTime lastReadAt;

    /** 加入时间 */
    private LocalDateTime joinedAt;
}
