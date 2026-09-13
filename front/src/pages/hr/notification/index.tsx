/**
 * HR 消息通知（/hr/notification）
 * 未读统计卡 + 类型筛选 + 通知列表；点击标记已读并按 targetType 跳转，
 * 人才推荐通知弹窗展示简历并发起沟通。
 */
import React, { useMemo, useCallback, useState, useEffect } from 'react';
import {
  List,
  Button,
  Space,
  Select,
  Tabs,
  message,
  Card,
  Spin,
  Pagination,
  Modal,
  Descriptions,
  Tag,
  Divider,
  Input,
} from 'antd';
import { useNavigate } from 'umi';
import {
  CheckOutlined,
  DeleteOutlined,
  FileTextOutlined,
  CalendarOutlined,
  BellOutlined,
  TrophyOutlined,
  WarningOutlined,
  TeamOutlined,
  MessageOutlined,
  PhoneOutlined,
  MailOutlined,
  WechatOutlined,
  SendOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import EmptyState from '@/components/EmptyState';
import { useNotificationList, useUnreadCount } from '@/hooks/useNotification';
import { getCandidateResumeByUserId } from '@/services/hr';
import { createConversation, sendMessage } from '@/services/message';
import useUserStore from '@/stores/userStore';
import type { Notification, NotificationType } from '@/constants/apiTypes';
import type { ResumeDetail } from '@/services/resume';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

/** 通知类型配置（与后端对齐） */
const NOTIFICATION_TYPE_CONFIG: Record<string, { icon: React.ReactNode; color: string; label: string }> = {
  NEW_APPLICATION: { icon: <FileTextOutlined />, color: '#2563EB', label: '投递通知' },
  INTERVIEW_SCHEDULE: { icon: <CalendarOutlined />, color: '#059669', label: '面试安排' },
  OFFER_MANAGE: { icon: <TrophyOutlined />, color: '#D97706', label: 'Offer管理' },
  HC_WARNING: { icon: <WarningOutlined />, color: '#F59E0B', label: 'HC预警' },
  TALENT_RECOMMEND: { icon: <TeamOutlined />, color: '#8B5CF6', label: '人才推荐' },
  SYSTEM: { icon: <BellOutlined />, color: '#6B7280', label: '系统通知' },
  // 兼容旧类型
  APPLICATION: { icon: <FileTextOutlined />, color: '#2563EB', label: '投递通知' },
  INTERVIEW: { icon: <CalendarOutlined />, color: '#059669', label: '面试通知' },
  OFFER: { icon: <TrophyOutlined />, color: '#D97706', label: 'Offer通知' },
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

const HRNotificationPage: React.FC = () => {
  const navigate = useNavigate();
  const { userInfo } = useUserStore();
  const [detailModalVisible, setDetailModalVisible] = useState(false);
  const [selectedNotification, setSelectedNotification] = useState<Notification | null>(null);

  // 人才推荐相关状态
  const [talentModalVisible, setTalentModalVisible] = useState(false);
  const [talentResume, setTalentResume] = useState<ResumeDetail | null>(null);
  const [talentLoading, setTalentLoading] = useState(false);
  const [talentUserId, setTalentUserId] = useState<number | null>(null);
  const [contactMessage, setContactMessage] = useState('您好，我看到您的简历非常符合我们的岗位需求，希望有机会和您聊聊！');
  const [sending, setSending] = useState(false);

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

  /** 点击通知 */
  const handleNotificationClick = useCallback(
    async (item: Notification) => {
      // 标记已读
      await markRead(item.id);

      const { targetType, targetId } = item;

      // 人才推荐通知 -> 弹窗显示简历
      if (targetType === 'CANDIDATE' && targetId) {
        setTalentUserId(targetId);
        setTalentModalVisible(true);
        setTalentLoading(true);
        try {
          const resume = await getCandidateResumeByUserId(String(targetId));
          setTalentResume(resume);
        } catch (error: any) {
          message.error(error.message || '获取简历失败');
          setTalentModalVisible(false);
        } finally {
          setTalentLoading(false);
        }
        return;
      }

      // 其他通知类型跳转
      if (targetType === 'APPLICATION' && targetId) {
        navigate(`/hr/candidate/${targetId}`);
        return;
      }

      if (targetType === 'INTERVIEW' && targetId) {
        navigate(ROUTES.HR_INTERVIEW);
        return;
      }

      if (targetType === 'OFFER' && targetId) {
        navigate(ROUTES.HR_OFFER);
        return;
      }

      if (targetType === 'JOB' && targetId) {
        navigate(`/hr/job/${targetId}`);
        return;
      }

      // 根据通知类型跳转（备用）
      switch (item.type) {
        case 'NEW_APPLICATION':
        case 'APPLICATION':
          navigate(ROUTES.HR_CANDIDATE);
          break;
        case 'INTERVIEW_SCHEDULE':
        case 'INTERVIEW':
          navigate(ROUTES.HR_INTERVIEW);
          break;
        case 'OFFER_MANAGE':
        case 'OFFER':
          navigate(ROUTES.HR_OFFER);
          break;
        case 'HC_WARNING':
          navigate(ROUTES.HR_JOB);
          break;
        case 'SYSTEM':
          setSelectedNotification(item);
          setDetailModalVisible(true);
          break;
        default:
          setSelectedNotification(item);
          setDetailModalVisible(true);
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

  /** 关闭人才弹窗 */
  const handleCloseTalent = useCallback(() => {
    setTalentModalVisible(false);
    setTalentResume(null);
    setTalentUserId(null);
    setContactMessage('您好，我看到您的简历非常符合我们的岗位需求，希望有机会和您聊聊！');
  }, []);

  /** 发起沟通 */
  const handleContact = useCallback(async () => {
    if (!talentUserId || !contactMessage.trim()) {
      message.warning('请输入沟通内容');
      return;
    }
    setSending(true);
    try {
      const conversationId = await createConversation({
        companyId: userInfo?.companyId || 0,
        candidateId: talentUserId,
        hrId: userInfo?.id || 0,
      });

      await sendMessage(conversationId, {
        content: contactMessage,
        msgType: 'TEXT',
        contentType: 'TEXT',
      });

      message.success('消息已发送');
      handleCloseTalent();
      navigate(`/hr/message?conversationId=${conversationId}`);
    } catch (error: any) {
      message.error(error.message || '发送失败');
    } finally {
      setSending(false);
    }
  }, [talentUserId, contactMessage, navigate, userInfo, handleCloseTalent]);

  /** 渲染简历卡片 */
  const renderResumeCard = () => {
    if (talentLoading) {
      return (
        <div style={{ textAlign: 'center', padding: '40px 0' }}>
          <Spin />
        </div>
      );
    }

    if (!talentResume) {
      return <div style={{ textAlign: 'center', padding: '40px 0', color: '#999' }}>简历不存在</div>;
    }

    const isBlindMode = talentResume.blindMode === true;

    return (
      <div>
        {/* 盲选模式提示 */}
        {isBlindMode && (
          <div style={{ padding: '12px', background: '#fff7e6', border: '1px solid #ffd591', borderRadius: '4px', marginBottom: 16 }}>
            📋 该候选人已开启盲选模式，个人信息已隐藏
          </div>
        )}

        {/* 简历内容 - 直接展示所有 sections */}
        {talentResume.cardStructure?.sections?.map((section, index) => (
          <div key={index} style={{ marginBottom: 16 }}>
            <h4 style={{ color: '#1890ff', marginBottom: 8 }}>{section.title}</h4>
            <ul style={{ paddingLeft: 20, margin: 0 }}>
              {section.points.map((point) => (
                <li key={point.id} style={{ padding: '4px 0', color: '#333' }}>
                  {point.text}
                </li>
              ))}
            </ul>
          </div>
        ))}
      </div>
    );
  };

  /** 统计数据 */
  const stats = useMemo(() => ({
    unread: unreadCount.totalCount,
    newApplication: unreadCount.newApplicationCount || 0,
    interviewSchedule: unreadCount.interviewScheduleCount || 0,
    offerManage: unreadCount.offerManageCount || 0,
    hcWarning: unreadCount.hcWarningCount || 0,
    talentRecommend: unreadCount.talentRecommendCount || 0,
    system: unreadCount.systemCount,
  }), [unreadCount]);

  /** 所有统计项 */
  const allStats = useMemo(() => [
    { label: '投递通知', value: stats.newApplication, type: 'NEW_APPLICATION' },
    { label: '面试安排', value: stats.interviewSchedule, type: 'INTERVIEW_SCHEDULE' },
    { label: 'Offer管理', value: stats.offerManage, type: 'OFFER_MANAGE' },
    { label: 'HC预警', value: stats.hcWarning, type: 'HC_WARNING' },
    { label: '人才推荐', value: stats.talentRecommend, type: 'TALENT_RECOMMEND' },
    { label: '系统通知', value: stats.system, type: 'SYSTEM' },
  ], [stats]);

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="消息通知"
        desc="查看和管理所有招聘相关通知"
        extra={
          <Space>
            <Button className={styles.outlineBtn} onClick={markAllRead}>
              <CheckOutlined /> 全部已读
            </Button>
          </Space>
        }
      />

      {/* Stat Cards */}
      <div className={styles.statRow}>
        <div
          className={`${styles.statCard} ${!typeFilter ? styles.statCardActive : ''}`}
          onClick={() => changeTypeFilter(undefined)}
        >
          <div className={styles.statValue}>{stats.unread}</div>
          <div className={styles.statLabel}>全部通知</div>
        </div>
        {allStats.map((stat) => (
          <div
            key={stat.type}
            className={`${styles.statCard} ${typeFilter === stat.type ? styles.statCardActive : ''}`}
            onClick={() => changeTypeFilter(stat.type as NotificationType)}
          >
            <div className={styles.statValue}>{stat.value}</div>
            <div className={styles.statLabel}>{stat.label}</div>
          </div>
        ))}
      </div>

      {/* Notification List */}
      <Card className={styles.card} bordered={false}>
        <div className={styles.filterBar}>
          <div className={styles.tabsWrapper}>
            <span className={styles.tabLabel}>
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
        </div>

        {loading ? (
          <div className={styles.loading}>
            <Spin />
          </div>
        ) : notifications.length === 0 ? (
          <EmptyState description="暂无通知" />
        ) : (
          <>
            <List
              dataSource={notifications}
              locale={{ emptyText: '暂无通知' }}
              renderItem={(item) => {
                const config = NOTIFICATION_TYPE_CONFIG[item.type] || NOTIFICATION_TYPE_CONFIG.SYSTEM;
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
      </Card>

      {/* 通知详情弹窗 */}
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

      {/* 人才推荐弹窗 - 显示简历 + 沟通按钮 */}
      <Modal
        title={
          <Space>
            <TeamOutlined />
            <span>候选人简历</span>
          </Space>
        }
        open={talentModalVisible}
        onCancel={handleCloseTalent}
        width={700}
        footer={[
          <Button key="cancel" onClick={handleCloseTalent}>
            关闭
          </Button>,
          <Button
            key="contact"
            type="primary"
            icon={<MessageOutlined />}
            onClick={handleContact}
            loading={sending}
          >
            发起沟通
          </Button>,
        ]}
      >
        {renderResumeCard()}

        {/* 沟通消息输入 */}
        {!talentLoading && talentResume && (
          <div style={{ marginTop: 16 }}>
            <Divider />
            <div style={{ marginBottom: 8, fontWeight: 500 }}>发送消息：</div>
            <Input.TextArea
              value={contactMessage}
              onChange={(e) => setContactMessage(e.target.value)}
              placeholder="请输入沟通内容..."
              rows={3}
            />
          </div>
        )}
      </Modal>
    </div>
  );
};

export default HRNotificationPage;
