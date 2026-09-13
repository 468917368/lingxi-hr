/**
 * 面试官「消息通知」（/interviewer/notification）
 * 与 HR 端复用同一套通知接口（按登录用户隔离），统计卡仅展示面试安排/系统通知；
 * 点击通知标记已读：面试安排跳转我的面试，系统通知弹详情。
 */
import React, { useMemo, useCallback, useState } from 'react';
import { List, Button, Space, Spin, Pagination, Modal } from 'antd';
import { useNavigate } from 'umi';
import { CheckOutlined, CalendarOutlined, BellOutlined } from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import EmptyState from '@/components/EmptyState';
import { useNotificationList, useUnreadCount } from '@/hooks/useNotification';
import type { Notification, NotificationType } from '@/constants/apiTypes';
import styles from './index.less';

/** 通知类型配置（面试官关注：面试安排 + 系统） */
const NOTIFICATION_TYPE_CONFIG: Record<string, { icon: React.ReactNode; color: string; label: string }> = {
  INTERVIEW_SCHEDULE: { icon: <CalendarOutlined />, color: '#059669', label: '面试安排' },
  INTERVIEW_INVITE: { icon: <CalendarOutlined />, color: '#059669', label: '面试安排' },
  SYSTEM: { icon: <BellOutlined />, color: '#6B7280', label: '系统通知' },
};

/** 格式化相对时间 */
function formatRelativeTime(time: string): string {
  const date = new Date(time);
  const now = new Date();
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;
  if (diffHour < 24) return `${diffHour}小时前`;
  if (diffDay === 1) return '昨天';
  if (diffDay < 7) return `${diffDay}天前`;

  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${month}月${day}日`;
}

const InterviewerNotificationPage: React.FC = () => {
  const navigate = useNavigate();
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedNotification, setSelectedNotification] = useState<Notification | null>(null);

  const {
    notifications,
    loading,
    total,
    page,
    typeFilter,
    markRead,
    markAllRead,
    changeTypeFilter,
    changePage,
  } = useNotificationList();

  const { unreadCount } = useUnreadCount();

  /** 点击通知：标已读 + 跳转 */
  const handleNotificationClick = useCallback(
    async (item: Notification) => {
      await markRead(item.id);
      const t = item.type as string;
      if (t === 'INTERVIEW_SCHEDULE' || t === 'INTERVIEW_INVITE') {
        navigate('/interviewer/interview');
      } else if (t === 'SYSTEM') {
        setSelectedNotification(item);
        setDetailModalVisible(true);
      }
    },
    [markRead, navigate],
  );

  const handleCloseDetail = useCallback(() => {
    setDetailModalVisible(false);
    setSelectedNotification(null);
  }, []);

  /** 统计数据 */
  const stats = useMemo(
    () => ({
      total: unreadCount.totalCount,
      interview: unreadCount.interviewScheduleCount || 0,
      system: unreadCount.systemCount,
    }),
    [unreadCount],
  );

  const statItems = useMemo(
    () => [
      { label: '面试安排', value: stats.interview, type: 'INTERVIEW_SCHEDULE' },
      { label: '系统通知', value: stats.system, type: 'SYSTEM' },
    ],
    [stats],
  );

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="消息通知"
        desc="查看我的面试安排与系统消息"
        extra={
          <Space>
            <Button className={styles.outlineBtn} onClick={() => markAllRead()}>
              <CheckOutlined /> 全部已读
            </Button>
          </Space>
        }
      />

      {/* 统计卡 */}
      <div className={styles.statRow}>
        <div
          className={`${styles.statCard} ${!typeFilter ? styles.statCardActive : ''}`}
          onClick={() => changeTypeFilter(undefined)}
        >
          <div className={styles.statValue}>{stats.total}</div>
          <div className={styles.statLabel}>全部通知</div>
        </div>
        {statItems.map((stat) => (
          <div
            key={stat.type}
            className={`${styles.statCard} ${String(typeFilter) === stat.type ? styles.statCardActive : ''}`}
            onClick={() => changeTypeFilter(stat.type as unknown as NotificationType)}
          >
            <div className={styles.statValue}>{stat.value}</div>
            <div className={styles.statLabel}>{stat.label}</div>
          </div>
        ))}
      </div>

      {/* 通知列表 */}
      <div className={styles.card}>
        <div className={styles.filterBar}>
          <span className={styles.tabLabel}>
            {typeFilter
              ? `筛选: ${NOTIFICATION_TYPE_CONFIG[String(typeFilter)]?.label || typeFilter}`
              : '全部通知'}
          </span>
          {typeFilter && (
            <Button type="link" size="small" onClick={() => changeTypeFilter(undefined)}>
              清除筛选
            </Button>
          )}
        </div>

        {loading ? (
          <div className={styles.loading}><Spin /></div>
        ) : notifications.length === 0 ? (
          <EmptyState description="暂无通知" />
        ) : (
          <>
            <List
              dataSource={notifications}
              locale={{ emptyText: '暂无通知' }}
              renderItem={(item) => {
                const config = NOTIFICATION_TYPE_CONFIG[String(item.type)] || NOTIFICATION_TYPE_CONFIG.SYSTEM;
                return (
                  <List.Item
                    className={`${styles.notifItem} ${!item.isRead ? styles.notifUnread : ''}`}
                    onClick={() => handleNotificationClick(item)}
                    style={{ cursor: 'pointer' }}
                    actions={[
                      !item.isRead && (
                        <Button
                          key="read"
                          type="link"
                          size="small"
                          className={styles.actionBtn}
                          onClick={() => markRead(item.id)}
                        >
                          标记已读
                        </Button>
                      ),
                    ].filter(Boolean)}
                  >
                    <List.Item.Meta
                      avatar={
                        <div
                          className={styles.notifIcon}
                          style={{ backgroundColor: `${config.color}15`, color: config.color }}
                        >
                          {config.icon}
                        </div>
                      }
                      title={
                        <div className={styles.notifTitle}>
                          {!item.isRead && <span className={styles.unreadDot} />}
                          <span className={styles.notifTypeLabel}>{item.title}</span>
                          <span className={styles.notifTime}>
                            {formatRelativeTime(item.createdAt)}
                          </span>
                        </div>
                      }
                      description={
                        <div className={styles.notifContent}>
                          <span>{item.content}</span>
                        </div>
                      }
                    />
                  </List.Item>
                );
              }}
            />

            {total > 20 && (
              <div className={styles.pagination}>
                <Pagination
                  current={page}
                  total={total}
                  pageSize={20}
                  onChange={changePage}
                  showSizeChanger={false}
                  showQuickJumper
                />
              </div>
            )}
          </>
        )}
      </div>

      {/* 系统通知详情弹窗 */}
      <Modal
        title="通知详情"
        open={detailModalVisible}
        onCancel={handleCloseDetail}
        footer={[<Button key="close" onClick={handleCloseDetail}>关闭</Button>]}
      >
        {selectedNotification && (
          <div>
            <div style={{ marginBottom: 16 }}>
              <div style={{ fontWeight: 600, fontSize: 16, marginBottom: 8 }}>
                {selectedNotification.title}
              </div>
              <div style={{ color: '#999', fontSize: 12 }}>
                {formatRelativeTime(selectedNotification.createdAt)}
              </div>
            </div>
            <div style={{ lineHeight: 1.8 }}>{selectedNotification.content}</div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default InterviewerNotificationPage;
