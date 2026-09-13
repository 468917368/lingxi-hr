package com.lingxi.admin.domain.entity;

import lombok.Data;
import java.time.LocalDateTime;

/**
 * 操作日志实体
 *
 * @author 成员E
 * @since 2026-08-01
 */
@Data
public class AuditLog {

    /** 日志ID */
    private Long id;

    /** 操作用户ID */
    private Long userId;

    /** 操作用户姓名 */
    private String userName;

    /** 操作类型：LOGIN / REVIEW / DISABLE / ENABLE / OFFLINE / ANNOUNCE / CONFIG / CREATE / UPDATE / DELETE / PUBLISH */
    private String operation;

    /** 操作对象类型：JOB / USER / COMPANY 等 */
    private String targetType;

    /** 操作对象ID */
    private Long targetId;

    /** 操作详情 */
    private String detail;

    /** 操作IP */
    private String ip;

    /** 操作时间 */
    private LocalDateTime createdAt;
}
