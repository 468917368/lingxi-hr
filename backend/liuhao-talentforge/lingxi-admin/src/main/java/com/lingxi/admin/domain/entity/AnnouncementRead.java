package com.lingxi.admin.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 公告已读记录实体
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Data
public class AnnouncementRead {

    /** 主键ID */
    private Long id;

    /** 公告ID */
    private Long announcementId;

    /** 用户ID */
    private Long userId;

    /** 用户类型：CANDIDATE / HR / INTERVIEWER */
    private String userType;

    /** 阅读时间 */
    private LocalDateTime readAt;
}
