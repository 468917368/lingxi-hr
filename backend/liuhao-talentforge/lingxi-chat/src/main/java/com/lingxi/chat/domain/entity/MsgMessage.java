package com.lingxi.chat.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 消息表实体
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class MsgMessage implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long id;

    /** 会话ID */
    private Long conversationId;

    /** 发送者ID（0=系统消息） */
    private Long senderId;

    /** 发送者角色：CANDIDATE=求职者 HR=HR */
    private String senderRole;

    /** 消息类型：TEXT=纯文本 RICH_TEXT=富文本 IMAGE=图片 FILE=文件 */
    private String msgType;

    /** 业务消息类型：TEXT/IMAGE/FILE/SYSTEM/CARD_RESUME/CARD_JOB */
    private String contentType;

    /** 消息内容 */
    private String content;

    /** 媒体文件URL（图片/文件消息时有值） */
    private String mediaUrl;

    /** 原始文件名 */
    private String fileName;

    /** 文件大小（字节） */
    private Long fileSize;

    /** 是否已读：0=否 1=是 */
    private Integer isRead;

    /** 发送时间 */
    private LocalDateTime createdAt;
}
