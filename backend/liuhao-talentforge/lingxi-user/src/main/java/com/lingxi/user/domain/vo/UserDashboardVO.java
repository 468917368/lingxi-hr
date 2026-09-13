package com.lingxi.user.domain.vo;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 用户仪表盘聚合数据
 *
 * @author lingxi-team
 * @since 2026-08-07
 */
@Data
public class UserDashboardVO {

    /** 用户信息 */
    private UserInfoVO userInfo;

    /** 隐私设置 */
    private PrivacyVO privacy;

    /** 最近会话（前5个） */
    private List<Map<String, Object>> recentSessions;

    /** 未读消息数 */
    private int unreadCount;
}
