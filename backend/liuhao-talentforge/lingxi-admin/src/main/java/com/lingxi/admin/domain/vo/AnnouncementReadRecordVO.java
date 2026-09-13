package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 公告已读记录VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AnnouncementReadRecordVO {

    /** 记录ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 用户类型 */
    private String userType;

    /** 阅读时间 */
    private LocalDateTime readAt;
}
