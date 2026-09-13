package com.lingxi.user.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * Agent对话历史表实体
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class SysAgentConversation implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 会话ID（UUID格式） */
    private String sessionId;

    /** 用户ID（关联 sys_user 表） */
    private Long userId;

    /** 消息角色：user=用户 assistant=助手 system=系统 */
    private String role;

    /** 消息内容 */
    private String content;

    /** 工具调用记录：[{name,args,result}] */
    private String toolCalls;

    /** 本次消耗的Token数 */
    private Integer tokensUsed;

    /** 创建时间 */
    private LocalDateTime createdAt;
}
