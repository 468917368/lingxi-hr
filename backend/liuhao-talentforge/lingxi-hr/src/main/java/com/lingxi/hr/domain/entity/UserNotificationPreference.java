package com.lingxi.hr.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 用户通知偏好表
 *
 * @author 成员D
 * @since 2026-08-02
 */
@Data
public class UserNotificationPreference implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 邮件通知：0=关闭 1=开启 */
    private Integer emailEnabled;

    /** 短信通知：0=关闭 1=开启 */
    private Integer smsEnabled;

    /** 浏览器推送：0=关闭 1=开启 */
    private Integer browserPushEnabled;

    /** 高匹配岗位推送（C端专用）：0=关闭 1=开启 */
    private Integer matchNotification;

    /** 创建时间 */
    private LocalDateTime createdAt;

    /** 更新时间 */
    private LocalDateTime updatedAt;
}
