package com.lingxi.chat.domain.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import javax.validation.constraints.Size;

/**
 * 发送消息请求
 *
 * @author 成员A
 * @since 2026-08-03
 */
@Data
public class SendMessageRequest {

    /** 消息类型：TEXT=纯文本 RICH_TEXT=富文本 IMAGE=图片 FILE=文件 */
    @Pattern(regexp = "^(TEXT|RICH_TEXT|IMAGE|FILE)$", message = "消息类型不支持")
    private String msgType;

    /** 业务消息类型：TEXT/IMAGE/FILE/CARD_RESUME/CARD_JOB */
    @NotBlank(message = "消息类型不能为空")
    @Pattern(regexp = "^(TEXT|IMAGE|FILE|CARD_RESUME|CARD_JOB)$", message = "消息类型不支持")
    private String contentType;

    /** 消息内容 */
    @NotBlank(message = "消息内容不能为空")
    @Size(max = 10000, message = "消息内容过长")
    private String content;

    /** 媒体文件URL（图片/文件消息时有值） */
    private String mediaUrl;

    /** 文件名 */
    private String fileName;

    /** 文件大小 */
    private Long fileSize;
}
