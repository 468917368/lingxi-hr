package com.lingxi.chat.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 会话视图对象
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private Long id;

    /** 对方用户信息 */
    private TargetUserVO targetUser;

    /** 最后一条消息 */
    private String lastMessage;

    /** 最后消息时间 */
    private String lastMessageTime;

    /** 未读消息数 */
    private Integer unreadCount;

    /** 是否置顶 */
    private Boolean isTop;

    /** 是否免打扰 */
    private Boolean isMuted;

    /**
     * 对方用户信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class TargetUserVO implements Serializable {
        private static final long serialVersionUID = 1L;

        /** 用户ID */
        private Long id;

        /** 姓名 */
        private String name;

        /** 头像 */
        private String avatar;

        /** 角色：HR/CANDIDATE */
        private String role;

        /** 企业名称 */
        private String company;

        /** 是否在线 */
        private Boolean isOnline;
    }
}
