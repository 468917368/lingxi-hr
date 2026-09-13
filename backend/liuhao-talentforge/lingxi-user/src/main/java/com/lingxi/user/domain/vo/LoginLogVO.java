package com.lingxi.user.domain.vo;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 登录日志视图对象
 *
 * @author 成员A
 * @since 2026-08-02
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLogVO {

    /** 主键ID */
    private Long id;

    /** 登录IP */
    private String loginIp;

    /** 登录设备/浏览器UA */
    private String loginDevice;

    /** 登录地点 */
    private String loginLocation;

    /** 登录时间（格式化字符串） */
    private String loginTime;

    /** 登录状态：1=成功 0=失败 */
    private Integer loginStatus;

    /** 失败原因 */
    private String failReason;
}
