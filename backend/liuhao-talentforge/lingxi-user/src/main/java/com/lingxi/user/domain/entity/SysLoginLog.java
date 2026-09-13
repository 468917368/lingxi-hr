package com.lingxi.user.domain.entity;

import lombok.Data;

import java.io.Serializable;
import java.time.LocalDateTime;

/**
 * 登录日志表实体
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Data
public class SysLoginLog implements Serializable {

    private static final long serialVersionUID = 1L;

    /** 主键ID */
    private Long id;

    /** 用户ID */
    private Long userId;

    /** 登录IP */
    private String loginIp;

    /** 登录设备/浏览器UA */
    private String loginDevice;

    /** 登录地点 */
    private String loginLocation;

    /** 登录时间 */
    private LocalDateTime loginTime;

    /** 登录状态：1=成功 0=失败 */
    private Integer loginStatus;

    /** 失败原因 */
    private String failReason;
}
