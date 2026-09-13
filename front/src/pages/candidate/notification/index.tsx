/**
 * 求职者消息通知页面
 *
 * 功能：
 * 1. 统计卡片展示各类通知数量
 * 2. 通知列表（支持按类型筛选）
 * 3. 标记已读/全部已读
 * 4. 点击通知跳转到对应页面
 * 5. 系统通知弹窗查看详情
 *
 * 通知类型：
 * - RESUME_VIEWED: 投递通知（简历被查看、筛选通过/未通过等）
 * - INTERVIEW_INVITE: 面试邀请
 * - INTERVIEW_REMIND: 面试提醒
 * - OFFER_RECEIVED: Offer通知（收到Offer、待确认等）
 * - SYSTEM: 系统通知（平台公告、账号安全等）
 * - JOB_RECOMMEND: 岗位推荐（AI推荐的匹配岗位）
 */
import React, { useMemo, useCallback, useState } from 'react';
import { Button, Spin, Pagination, Modal } from 'antd';
import { useNavigate } from 'umi';
import {
  CheckOutlined,          // 已读图标
  FileTextOutlined,       // 投递通知图标
  CalendarOutlined,       // 面试通知图标
  ClockCircleOutlined,    // 面试提醒图标
  GiftOutlined,           // Offer通知图标
  BellOutlined,           // 系统通知图标
  RocketOutlined,         // 岗位推荐图标
} from '@ant-design/icons';
import PageHeader from '@/components/PageHeader';  // 页面头部组件
import StatCard from '@/components/StatCard';      // 统计卡片组件
import EmptyState from '@/components/EmptyState';  // 空状态组件
import { useNotificationList, useUnreadCount } from '@/hooks/useNotification';  // 通知相关Hook
import type { Notification, NotificationType } from '@/constants/apiTypes';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

/**
 * 通知类型配置
 * 定义每种通知类型的图标、颜色、标签
 */
const NOTIFICATION_TYPE_CONFIG: Record<NotificationType, { icon: React.ReactNode; color: string; label: string }> = {
  RESUME_VIEWED: { icon: <FileTextOutlined />, color: '#1890ff', label: '投递通知' },
  INTERVIEW_INVITE: { icon: <CalendarOutlined />, color: '#52c41a', label: '面试邀请' },
  INTERVIEW_REMIND: { icon: <ClockCircleOutlined />, color: '#fa8c16', label: '面试提醒' },
  OFFER_RECEIVED: { icon: <GiftOutlined />, color: '#faad14', label: 'Offer通知' },
  SYSTEM: { icon: <BellOutlined />, color: '#722ed1', label: '系统通知' },
  JOB_RECOMMEND: { icon: <RocketOutlined />, color: '#13c2c2', label: '岗位推荐' },
};

/**
 * 格式化相对时间
 * 将时间戳转换为"刚刚"、"X分钟前"、"X小时前"等
 */
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

const NotificationPage: React.FC = () => {
  // ==================== 状态定义 ====================

  const navigate = useNavigate();

  /** 通知详情弹窗是否可见 */
  const [detailModalVisible, setDetailModalVisible] = useState(false);

  /** 当前选中的通知（用于详情弹窗） */
  const [selectedNotification, setSelectedNotification] = useState<Notification | null>(null);

  // ==================== Hooks ====================

  /**
   * useNotificationList Hook
   * 提供通知列表、筛选、分页、标记已读等功能
   */
  const {
    notifications,    // 通知列表
    loading,          // 加载状态
    total,            // 总数
    page,             // 当前页
    typeFilter,       // 当前筛选类型
    markRead,         // 标记单条已读
    markAllRead,      // 全部标记已读
    changeTypeFilter, // 切换筛选类型
    changePage,       // 切换页码
  } = useNotificationList();

  /**
   * useUnreadCount Hook
   * 获取各类通知的未读数量
   */
  const { unreadCount } = useUnreadCount();

  // ==================== 事件处理 ====================

  /**
   * 点击通知
   * 1. 标记已读
   * 2. 根据通知类型跳转到对应页面
   *
   * 跳转逻辑：
   * - 优先根据 targetType + targetId 跳转（精确跳转）
   * - 其次根据通知类型跳转（通用跳转）
   * - 系统通知弹窗显示详情
   */
  const handleNotificationClick = useCallback(
    async (item: Notification) => {
      // 标记已读
      await markRead(item.id);

      // 优先根据 targetType 和 targetId 跳转（精确跳转）
      // 注意：后端 targetType 为小写（application/offer/interview/job）
      const { targetType, targetId } = item;

      if (targetType === 'job' && targetId) {
        // 岗位推荐 -> 岗位详情页
        navigate(`${ROUTES.CANDIDATE_JOB_DETAIL}/${targetId}`);
        return;
      }

      if (targetType === 'application' && targetId) {
        // 投递通知 -> 投递进度页
        navigate(ROUTES.CANDIDATE_APPLICATION);
        return;
      }

      if (targetType === 'interview' && targetId) {
        // 面试通知 -> 投递进度页
        navigate(ROUTES.CANDIDATE_APPLICATION);
        return;
      }

      if (targetType === 'offer' && targetId) {
        // Offer通知 -> 投递进度页
        navigate(ROUTES.CANDIDATE_APPLICATION);
        return;
      }

      // 其次根据通知类型跳转（通用跳转）
      switch (item.type) {
        case 'RESUME_VIEWED':
        case 'INTERVIEW_INVITE':
        case 'INTERVIEW_REMIND':
        case 'OFFER_RECEIVED':
          navigate(ROUTES.CANDIDATE_APPLICATION);
          break;
        case 'JOB_RECOMMEND':
          navigate(ROUTES.CANDIDATE_JOB);
          break;
        case 'SYSTEM':
          // 系统通知弹窗显示详情
          setSelectedNotification(item);
          setDetailModalVisible(true);
          break;
        default:
          break;
      }
    },
    [markRead, navigate],
  );

  /** 关闭详情弹窗 */
  const handleCloseDetail = useCallback(() => {
    setDetailModalVisible(false);
    setSelectedNotification(null);
  }, []);

  // ==================== 计算属性 ====================

  /**
   * 统计数据
   * 字段名与后端 UnreadNotificationCount 接口对齐
   */
  const stats = useMemo(() => ({
    total: unreadCount.totalCount,
    application: unreadCount.resumeViewedCount || 0,     // 简历被查看
    interview: unreadCount.interviewInviteCount || 0,     // 面试邀请
    offer: unreadCount.offerReceivedCount || 0,           // 收到Offer
    jobRecommend: unreadCount.jobRecommendCount || 0,     // 岗位推荐
    system: unreadCount.systemCount,                      // 系统通知
  }), [unreadCount]);

  // ==================== 渲染 ====================

  return (
    <div className={styles.page}>
      {/* 页面头部 */}
      <PageHeader
        title="消息通知"
        description="查看投递、面试等各类通知"
      />

      {/* ========== 统计卡片行 ========== */}
      <div className={styles.statsRow}>
        <StatCard
          icon="ALL"
          label="全部通知"
          value={stats.total}
          onClick={() => changeTypeFilter(undefined)}
        />
        <StatCard
          icon="DOC"
          label="投递通知"
          value={stats.application}
          onClick={() => changeTypeFilter('RESUME_VIEWED')}
        />
        <StatCard
          icon="INTERVIEW"
          label="面试通知"
          value={stats.interview}
          onClick={() => changeTypeFilter('INTERVIEW_INVITE')}
        />
        <StatCard
          icon="OFFER"
          label="Offer通知"
          value={stats.offer}
          onClick={() => changeTypeFilter('OFFER_RECEIVED')}
        />
        <StatCard
          icon="JOB"
          label="岗位推荐"
          value={stats.jobRecommend}
          onClick={() => changeTypeFilter('JOB_RECOMMEND')}
        />
        <StatCard
          icon="BELL"
          label="系统通知"
          value={stats.system}
          onClick={() => changeTypeFilter('SYSTEM')}
        />
      </div>

      {/* ========== 控制栏：筛选信息 + 全部已读按钮 ========== */}
      <div className={styles.controlBar}>
        <div className={styles.filterInfo}>
          <span>
            {typeFilter ? `筛选: ${NOTIFICATION_TYPE_CONFIG[typeFilter]?.label || typeFilter}` : '全部通知'}
          </span>
          {typeFilter && (
            <Button
              type="link"
              size="small"
              onClick={() => changeTypeFilter(undefined)}
            >
              清除筛选
            </Button>
          )}
        </div>
        <Button
          icon={<CheckOutlined />}
          onClick={markAllRead}
          style={{
            borderRadius: 'var(--radius-md)',
            fontWeight: 600,
            fontFamily: 'var(--font-title)',
          }}
        >
          全部标为已读
        </Button>
      </div>

      {/* ========== 通知列表 ========== */}
      {loading ? (
        // 加载中
        <div className={styles.loading}>
          <Spin />
        </div>
      ) : notifications.length === 0 ? (
        // 空状态
        <EmptyState description="暂无新通知" />
      ) : (
        <>
          {/* 通知列表 */}
          <div className={styles.notifList}>
            {notifications.map((item) => {
              // 获取通知类型配置（图标、颜色、标签）；后端类型不在配置时回退系统通知
              const config = NOTIFICATION_TYPE_CONFIG[item.type as NotificationType] || NOTIFICATION_TYPE_CONFIG.SYSTEM;
              return (
                <div
                  key={item.id}
                  className={`${styles.notifItem} ${!item.isRead ? styles.notifItemUnread : ''}`}
                  onClick={() => handleNotificationClick(item)}
                >
                  {/* 左侧：类型图标 */}
                  <div
                    className={styles.notifIcon}
                    style={{ background: `${config.color}15`, color: config.color }}
                  >
                    {config.icon}
                  </div>

                  {/* 中间：标题 + 内容 */}
                  <div className={styles.notifContent}>
                    <div className={styles.notifTitle}>{item.title}</div>
                    <div className={styles.notifBody}>{item.content}</div>
                  </div>

                  {/* 右侧：时间 + 未读标记 */}
                  <div className={styles.notifMeta}>
                    <span className={styles.notifTime}>
                      {formatRelativeTime(item.createdAt)}
                    </span>
                    {!item.isRead && <div className={styles.unreadDot} />}
                  </div>
                </div>
              );
            })}
          </div>

          {/* 分页（超过20条时显示） */}
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

      {/* ========== 通知详情弹窗（系统通知用） ========== */}
      <Modal
        title="通知详情"
        open={detailModalVisible}
        onCancel={handleCloseDetail}
        footer={[
          <Button key="close" onClick={handleCloseDetail}>
            关闭
          </Button>,
        ]}
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
            <div style={{ lineHeight: 1.8 }}>
              {selectedNotification.content}
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
};

export default NotificationPage;
