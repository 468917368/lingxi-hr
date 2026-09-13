package com.lingxi.admin.domain.vo;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志VO
 *
 * @author 成员E
 * @since 2026-08-02
 */
@Data
public class AuditLogVO {

    /** 日志ID */
    private Long id;

    /** 操作人ID */
    private Long userId;

    /** 操作人姓名 */
    private String userName;

    /** 操作时间 */
    private LocalDateTime createTime;

    /** 操作类型 */
    private String operationType;

    /** 操作详情 */
    private String detail;

    /** IP地址 */
    private String ip;
}
