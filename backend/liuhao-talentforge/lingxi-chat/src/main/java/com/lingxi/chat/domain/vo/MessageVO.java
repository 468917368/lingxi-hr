package com.lingxi.chat.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * 消息视图对象
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageVO implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 消息ID */
    private Long id;

    /** 发送者ID */
    private Long senderId;

    /** 发送者姓名 */
    private String senderName;

    /** 发送者头像 */
    private String senderAvatar;

    /** 消息类型：TEXT=纯文本 RICH_TEXT=富文本 IMAGE=图片 FILE=文件 */
    private String msgType;

    /** 业务消息类型：TEXT/IMAGE/FILE/SYSTEM */
    private String contentType;

    /** 消息内容 */
    private String content;

    /** 媒体文件URL */
    private String mediaUrl;

    /** 文件名 */
    private String fileName;

    /** 文件大小 */
    private Long fileSize;

    /** 是否已读 */
    private Boolean isRead;

    /** 消息状态：SENT=已送达 DELIVERED=已送达 READ=已读 */
    private String status;

    /** 发送时间 */
    private String createdAt;
}
