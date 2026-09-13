import React, { useState } from 'react';
import { Outlet, useLocation, useNavigate } from 'umi';
import { BellOutlined, UserOutlined, MenuFoldOutlined, MenuUnfoldOutlined, SafetyOutlined } from '@ant-design/icons';
import { Badge, Dropdown, Avatar } from 'antd';
import type { MenuProps } from 'antd';
import { ROUTES } from '@/constants/routes';
import { useAuth } from '@/hooks/useAuth';
import { getAvatarUrl } from '@/utils/fileUrl';
import styles from './HRLayout.less';

const adminMenuItems = [
  { key: ROUTES.ADMIN_DASHBOARD, label: '数据看板', icon: '📊' },
  { key: ROUTES.ADMIN_ENTERPRISE_AUDIT, label: '认证审核', icon: '🏢' },
  { key: ROUTES.ADMIN_ENTERPRISE, label: '企业管理', icon: '🏢' },
  { key: ROUTES.ADMIN_HR, label: 'HR管理', icon: '👤' },
  { key: ROUTES.ADMIN_INTERVIEWER, label: '面试官管理', icon: '👤' },
  { key: ROUTES.ADMIN_JOB, label: '岗位管理', icon: '📋' },
  { key: ROUTES.ADMIN_CANDIDATE, label: '求职者管理', icon: '👤' },
  { key: ROUTES.ADMIN_ANNOUNCEMENT, label: '系统公告', icon: '📢' },
  { key: ROUTES.ADMIN_AUDIT_LOG, label: '操作日志', icon: '📝' },
  { key: ROUTES.ADMIN_CONFIG, label: '系统配置', icon: '⚙️' },
];

const AdminLayout: React.FC = () => {
  const location = useLocation();
  const navigate = useNavigate();
  const { userInfo, logout } = useAuth();
  const [collapsed, setCollapsed] = useState(false);

  const isActive = (path: string) => {
    return location.pathname === path || location.pathname.startsWith(path + '/');
  };

  const userMenuItems: MenuProps['items'] = [
    { key: 'security', label: '账号安全', icon: <SafetyOutlined />, onClick: () => navigate(ROUTES.CANDIDATE_SECURITY) },
    { type: 'divider' },
    { key: 'logout', label: '退出登录', onClick: logout },
  ];

  return (
    <div className={`a-mode ${styles.layout}`}>
      {/* 侧边栏 */}
      <aside className={`${styles.sidebar} ${collapsed ? styles.collapsed : ''}`}>
        <div className={styles.sidebarBrand} onClick={() => navigate(ROUTES.ADMIN_DASHBOARD)}>
          <div className={styles.brandMark}>L</div>
          {!collapsed && <span className={styles.brandText}>管理后台</span>}
        </div>

        <nav className={styles.sidebarNav}>
          {adminMenuItems.map((item) => (
            <button
              key={item.key}
              className={`${styles.sidebarItem} ${isActive(item.key) ? styles.active : ''}`}
              onClick={() => navigate(item.key)}
              title={collapsed ? item.label : undefined}
            >
              <span className={styles.sidebarIcon}>{item.icon}</span>
              {!collapsed && <span>{item.label}</span>}
            </button>
          ))}
        </nav>
      </aside>

      {/* 主区域 */}
      <div className={styles.main}>
        <header className={styles.topbar}>
          <button className={styles.collapseBtn} onClick={() => setCollapsed(!collapsed)}>
            {collapsed ? <MenuUnfoldOutlined /> : <MenuFoldOutlined />}
          </button>

          <div style={{ flex: 1 }} />

          <div className={styles.topbarActions}>
            <Dropdown menu={{ items: userMenuItems }} placement="bottomRight">
              <button className={styles.avatarBtn}>
                <Avatar
                  size={32}
                  src={getAvatarUrl(userInfo?.avatar)}
                  icon={<UserOutlined />}
                />
                <span className={styles.userName}>{userInfo?.name || '管理员'}</span>
              </button>
            </Dropdown>
          </div>
        </header>

        <main className={styles.content}>
          <Outlet />
        </main>
      </div>
    </div>
  );
};

export default AdminLayout;
