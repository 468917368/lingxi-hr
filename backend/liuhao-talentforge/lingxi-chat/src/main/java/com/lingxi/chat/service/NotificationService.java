package com.lingxi.chat.service;

import com.lingxi.chat.domain.vo.NotificationVO;
import com.lingxi.common.domain.PageResult;

import java.util.Map;

/**
 * 通知服务接口
 *
 * @author 成员A
 * @since 2026-08-03
 */
public interface NotificationService {

    /**
     * 创建通知
     *
     * @param userId     接收用户ID
     * @param type       通知类型
     * @param title      通知标题
     * @param content    通知内容
     * @param targetType 关联目标类型
     * @param targetId   关联目标ID
     * @return 通知ID
     */
    Long createNotification(Long userId, String type, String title, String content,
                            String targetType, Long targetId);

    /**
     * 获取通知列表
     *
     * @param userId 用户ID
     * @param type   通知类型（可选）
     * @param page   页码
     * @param size   每页条数
     * @return 通知列表
     */
    PageResult<NotificationVO> getNotifications(Long userId, String type, int page, int size);

    /**
     * 获取未读通知数
     *
     * @param userId 用户ID
     * @return 未读通知数（按类型分组）
     */
    Map<String, Long> getUnreadCount(Long userId);

    /**
     * 标记通知已读
     *
     * @param userId 用户ID
     * @param id     通知ID
     */
    void markAsRead(Long userId, Long id);

    /**
     * 标记所有通知已读
     *
     * @param userId 用户ID
     */
    void markAllAsRead(Long userId);

    /**
     * 删除用户今日的指定类型通知
     *
     * @param userId 用户ID
     * @param type   通知类型
     */
    void deleteTodayNotifications(Long userId, String type);
}
