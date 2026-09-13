package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * AI会话视图对象
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiSessionVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 会话ID */
    private String sessionId;

    /** 会话标题（取自第一条用户消息） */
    private String title;

    /** 最后一条消息内容（前50字） */
    private String lastMessage;

    /** 最后消息时间 */
    private String lastMessageTime;

    /** 消息数量 */
    private Long messageCount;

    /** Token消耗总量 */
    private Long totalTokens;
}
