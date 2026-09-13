import React, { useState, useEffect } from 'react';
import { Outlet, useLocation, useNavigate } from 'umi';
import { BellOutlined, UserOutlined, MenuFoldOutlined, MenuUnfoldOutlined } from '@ant-design/icons';
import { Badge, Dropdown, Avatar } from 'antd';
import type { MenuProps } from 'antd';
import { ROUTES } from '@/constants/routes';
import { useAuth } from '@/hooks/useAuth';
import useUserStore from '@/stores/userStore';
import { useUnreadCount } from '@/hooks/useNotification';
import { useWebSocketConnect } from '@/hooks/useWebSocket';
import { getAvatarUrl } from '@/utils/fileUrl';
import { getHrAccountProfile } from '@/services/hr';
import useMessageStore from '@/stores/messageStore';
import * as messageService from '@/services/message';
import styles from './HRLayout.less';

const hrMenuItems = [
  { key: ROUTES.HR_DASHBOARD, label: '工作台', icon: '📊' },
  { key: ROUTES.HR_JOB, label: '岗位管理', icon: '📋' },
  { key: ROUTES.HR_QUESTION_BANK, label: '题库管理', icon: '📚' },
  { key: ROUTES.HR_CANDIDATE, label: '人才库', icon: '👥' },
  { key: ROUTES.HR_INTERVIEW, label: '面试协同', icon: '🎯' },
  { key: ROUTES.HR_OFFER, label: 'Offer管理', icon: '📄' },
  { key: ROUTES.HR_MESSAGE, label: '消息沟通', icon: '💬' },
  { key: ROUTES.HR_NOTIFICATION, label: '消息通知', icon: '🔔' },
  { key: ROUTES.HR_COMPANY, label: '公司管理', icon: '🏢' },
  { key: ROUTES.HR_PROFILE, label: '个人中心', icon: '👤' },
];

const interviewerMenuItems = [
  { key: '/interviewer/interview', label: '我的面试', icon: '🎯' },
  { key: '/interviewer/candidate', label: '候选人', icon: '👥' },
  { key: '/interviewer/notification', label: '消息通知', icon: '🔔' },
  { key: ROUTES.INTERVIEWER_PROFILE, label: '个人中心', icon: '👤' },
];

const HRLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { userInfo, logout } = useAuth();
  const { isLogin, setUserInfo } = useUserStore();
  const [collapsed, setCollapsed] = useState(false);
  const { unreadCount } = useUnreadCount();
  const conversations = useMessageStore((s) => s.conversations);
  const setConversations = useMessageStore((s) => s.setConversations);

  // 消息未读总数
  const messageUnreadCount = conversations.reduce((sum, c) => sum + (c.unreadCount || 0), 0);

  // 连接WebSocket获取实时通知
  useWebSocketConnect();

  // 拉取真实账号信息（头像/姓名等），同步右上角全局展示（HR_ADMIN 与面试官均可用）
  useEffect(() => {
    if (!isLogin) return;
    getHrAccountProfile()
      .then((profile) => {
        const current = useUserStore.getState().userInfo;
        if (!current) return;
        setUserInfo({
          ...current,
          name: profile.name,
          phone: profile.phone,
          email: profile.email || undefined,
          avatar: profile.avatar || undefined,
          companyName: profile.companyName || undefined,
        });
      })
      .catch(() => {});
  }, [isLogin, setUserInfo]);

  // 加载会话列表（获取未读数）
  useEffect(() => {
    if (isLogin) {
      messageService.getConversations().then((data) => {
        setConversations(Array.isArray(data) ? data : []);
      }).catch(() => {});
    }
  }, [isLogin, setConversations]);

  const isInterviewer = location.pathname.startsWith('/interviewer');
  const modeClass = isInterviewer ? 'b-mode interviewer-role' : 'b-mode';
  const menuItems = isInterviewer ? interviewerMenuItems : hrMenuItems;

  const isActive = (path: string) => {
    return location.pathname.startsWith(path);
  };

  const userMenuItems: MenuProps['items'] = [
    { key: 'profile', label: '个人中心', icon: <UserOutlined />, onClick: () => navigate(isInterviewer ? '/interviewer/profile' : ROUTES.HR_PROFILE) },
    { type: 'divider' },
    { key: 'logout', label: '退出登录', onClick: logout },
  ];

  return (
    <div className={`${modeClass} ${styles.layout}`}>
      {/* 侧边栏 */}
      <aside className={`${styles.sidebar} ${collapsed ? styles.collapsed : ''}`}>
        <div className={styles.sidebarBrand} onClick={() => navigate(isInterviewer ? '/interviewer/interview' : ROUTES.HR_DASHBOARD)}>
          <div className={styles.brandMark}>L</div>
          {!collapsed && <span className={styles.brandText}>灵犀互聘</span>}
        </div>

        <nav className={styles.sidebarNav}>
          {menuItems.map((item) => (
            <button
              key={item.key}
              className={`${styles.sidebarItem} ${isActive(item.key) ? styles.active : ''}`}
              onClick={() => navigate(item.key)}
              title={collapsed ? item.label : undefined}
            >
              {item.key === ROUTES.HR_MESSAGE && messageUnreadCount > 0 ? (
                <Badge count={messageUnreadCount} size="small" offset={[4, -2]}>
                  <span className={styles.sidebarIcon}>{item.icon}</span>
                </Badge>
              ) : (
                <span className={styles.sidebarIcon}>{item.icon}</span>
              )}
              {!collapsed && <span>{item.label}</span>}
            </button>
          ))}
        </nav>
      </aside>

      {/* 主区域 */}
      <div className={styles.main}>
        {/* 顶部栏 */}
        <header className={styles.topbar}>
          <button className={styles.collapseBtn} onClick={() => setCollapsed(!collapsed)}>
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </button>

          <div style={{ flex: 1 }} />

          <div className={styles.topbarActions}>
            <Badge count={unreadCount.totalCount} size="small">
              <button
                className={styles.iconBtn}
                onClick={() => navigate(isInterviewer ? '/interviewer/notification' : ROUTES.HR_NOTIFICATION)}
              >
                <BellOutlined />
              </button>
            </Badge>
            {isLogin ? (
              <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
                <button className={styles.avatarBtn}>
                  <Avatar
                    key={userInfo?.avatar || 'default'}
                    size={32}
                    src={getAvatarUrl(userInfo?.avatar)}
                    icon={<UserOutlined />}
                  />
                  <span className={styles.userName}>{userInfo?.name || '用户'}</span>
                </button>
              </Dropdown>
            ) : (
              <span className={styles.userName}>未登录</span>
            )}
          </div>
        </header>

        {/* 内容区 */}
        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default HRLayout;
