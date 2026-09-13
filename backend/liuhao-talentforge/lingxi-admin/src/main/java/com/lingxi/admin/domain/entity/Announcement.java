package com.lingxi.admin.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 平台公告实体
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Data
public class Announcement {

    /** 公告ID */
    private Long id;

    /** 公告标题 */
    private String title;

    /** 公告类型：SYSTEM/UPDATE/NOTICE/MAINTENANCE */
    private String type;

    /** 公告内容 */
    private String content;

    /** 目标角色：ALL=全部 / CANDIDATE=求职者 / HR=HR / INTERVIEWER=面试官 */
    private String targetRole;

    /** 状态：DRAFT=草稿 / PUBLISHED=已发布 / ARCHIVED=已归档 */
    private String status;

    /** 发布人管理员ID */
    private Long createdBy;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
