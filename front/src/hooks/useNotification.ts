import { useState, useCallback, useEffect } from 'react';
import { message } from 'antd';
import type { Notification, NotificationType, UnreadNotificationCount } from '@/constants/apiTypes';
import * as notificationService from '@/services/notification';
import { requestCache } from '@/utils/requestCache';
import { useWebSocketEvent } from './useWebSocket';

/** 未读数刷新事件 */
const UNREAD_REFRESH_EVENT = 'notification-unread-refresh';

/** 触发未读数刷新（供其他组件调用） */
export function triggerUnreadRefresh() {
  window.dispatchEvent(new CustomEvent(UNREAD_REFRESH_EVENT));
}

/**
 * 通知未读数Hook
 */
export function useUnreadCount() {
  const [unreadCount, setUnreadCount] = useState<UnreadNotificationCount>({
    totalCount: 0,
    resumeViewedCount: 0,
    interviewInviteCount: 0,
    offerReceivedCount: 0,
    jobRecommendCount: 0,
    newApplicationCount: 0,
    interviewScheduleCount: 0,
    offerManageCount: 0,
    hcWarningCount: 0,
    companyCertCount: 0,
    systemCount: 0,
  });
  const [loading, setLoading] = useState(false);

  /** 获取未读数 */
  const fetchUnreadCount = useCallback(async () => {
    setLoading(true);
    try {
      const data = await notificationService.getUnreadNotificationCount();
      setUnreadCount(data);
    } catch (error) {
      console.error('Failed to fetch unread count:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  /** 清除缓存并刷新未读数 */
  const refreshUnreadCount = useCallback(async () => {
    requestCache.delete('unread-notification-count');
    await fetchUnreadCount();
  }, [fetchUnreadCount]);

  /** WebSocket推送更新未读数 */
  useWebSocketEvent('NEW_NOTIFICATION', useCallback(() => {
    refreshUnreadCount();
  }, [refreshUnreadCount]));

  /** 监听自定义刷新事件 */
  useEffect(() => {
    const handler = () => refreshUnreadCount();
    window.addEventListener(UNREAD_REFRESH_EVENT, handler);
    return () => window.removeEventListener(UNREAD_REFRESH_EVENT, handler);
  }, [refreshUnreadCount]);

  /** 初始加载 */
  useEffect(() => {
    fetchUnreadCount();
  }, [fetchUnreadCount]);

  return { unreadCount, loading, refresh: refreshUnreadCount };
}

/**
 * 通知列表Hook
 */
export function useNotificationList() {
  const [notifications, setNotifications] = useState<Notification[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [typeFilter, setTypeFilter] = useState<NotificationType | undefined>();

  /** 获取通知列表 */
  const fetchNotifications = useCallback(async (p = 1, type?: NotificationType) => {
    setLoading(true);
    try {
      const data = await notificationService.getNotifications(p, 20, type);
      setNotifications(data.list);
      setTotal(data.total);
      setPage(p);
    } catch (error) {
      console.error('Failed to fetch notifications:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  /** 标记单条已读 */
  const markRead = useCallback(async (id: number) => {
    try {
      await notificationService.markNotificationRead(id);
      setNotifications((prev) =>
        prev.map((n) => (n.id === id ? { ...n, isRead: true } : n)),
      );
      // 清除未读数缓存并触发刷新
      requestCache.delete('unread-notification-count');
      triggerUnreadRefresh();
    } catch (error) {
      message.error('标记已读失败');
    }
  }, []);

  /** 全部标记已读 */
  const markAllRead = useCallback(async () => {
    try {
      await notificationService.markAllNotificationsRead();
      setNotifications((prev) => prev.map((n) => ({ ...n, isRead: true })));
      // 清除未读数缓存并触发刷新
      requestCache.delete('unread-notification-count');
      triggerUnreadRefresh();
      message.success('已全部标记为已读');
    } catch (error) {
      message.error('操作失败');
    }
  }, []);

  /** 切换筛选类型 */
  const changeTypeFilter = useCallback(
    (type: NotificationType | undefined) => {
      console.log('[Notification] Changing filter to:', type);
      setTypeFilter(type);
      fetchNotifications(1, type);
    },
    [fetchNotifications],
  );

  /** 切换页码 */
  const changePage = useCallback(
    (p: number) => {
      fetchNotifications(p, typeFilter);
    },
    [fetchNotifications, typeFilter],
  );

  /** 初始加载 */
  useEffect(() => {
    fetchNotifications();
  }, [fetchNotifications]);

  return {
    notifications,
    loading,
    total,
    page,
    typeFilter,
    markRead,
    markAllRead,
    changeTypeFilter,
    changePage,
    refresh: () => fetchNotifications(page, typeFilter),
  };
}
