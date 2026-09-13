import request from '@/utils/request';
import { cachedRequest } from '@/utils/requestCache';
import type {
  Notification,
  NotificationType,
  UnreadNotificationCount,
  PageResult,
} from '@/constants/apiTypes';

/** 获取通知列表 */
export async function getNotifications(
  page = 1,
  size = 20,
  type?: NotificationType,
): Promise<PageResult<Notification>> {
  return request.get('/v1/notifications', {
    params: { page, size, type },
  });
}

/** 获取未读通知数（缓存10秒，避免频繁请求） */
export async function getUnreadNotificationCount(): Promise<UnreadNotificationCount> {
  return cachedRequest(
    'unread-notification-count',
    () => request.get('/v1/notifications/unread-count'),
    10 * 1000, // 10秒缓存
  );
}

/** 标记通知已读 */
export async function markNotificationRead(id: number): Promise<void> {
  return request.put(`/v1/notifications/${id}/read`);
}

/** 全部标记已读 */
export async function markAllNotificationsRead(): Promise<void> {
  return request.put('/v1/notifications/read-all');
}
