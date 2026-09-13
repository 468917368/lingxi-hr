package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 公告列表项VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AnnouncementVO {

    /** 公告ID */
    private Long id;

    /** 标题 */
    private String title;

    /** 内容 */
    private String content;

    /** 目标角色 */
    private String targetRole;

    /** 状态 */
    private String status;

    /** 发布时间 */
    private LocalDateTime createdAt;
}
