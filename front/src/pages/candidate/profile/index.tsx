import React, { useState, useEffect, useCallback, useRef, useMemo } from 'react';
import { useNavigate } from 'umi';
import { Button, Switch, Select, message, Spin, Modal } from 'antd';
import {
  EditOutlined,
  RightOutlined,
  EnvironmentOutlined,
  ClockCircleOutlined,
  FileTextOutlined,
  SendOutlined,
  BellOutlined,
  LogoutOutlined,
  SafetyOutlined,
} from '@ant-design/icons';
import { ROUTES } from '@/constants/routes';
import { useAuth } from '@/hooks/useAuth';
import { useUnreadCount } from '@/hooks/useNotification';
import { getUserInfo, getPrivacySettings, updatePrivacySettings } from '@/services/user';
import type { UserInfo, PrivacySettings } from '@/constants/apiTypes';
import { cachedRequest } from '@/utils/requestCache';
import EditProfileModal from './components/EditProfileModal';
import styles from './index.less';

/** 求职状态映射 */
const JOB_STATUS_TEXT: Record<string, string> = {
  JOB_SEEKING: '求职中',
  EMPLOYED_LOOKING: '在职看机会',
  NOT_LOOKING: '暂不考虑',
};

const ProfilePage = () => {
  const navigate = useNavigate();
  const { logout } = useAuth();
  const isMountedRef = useRef(true);
  const { unreadCount } = useUnreadCount();

  const [user, setUser] = useState<UserInfo | null>(null);
  const [privacy, setPrivacy] = useState<PrivacySettings | null>(null);
  const [loading, setLoading] = useState(true);
  const [editModalVisible, setEditModalVisible] = useState(false);

  // 功能入口配置（使用 useMemo 缓存）
  const functionItems = useMemo(
    () => [
      {
        icon: <FileTextOutlined />,
        label: '简历管理',
        onClick: () => navigate(ROUTES.CANDIDATE_RESUME_UPLOAD),
        badge: 0,
      },
      {
        icon: <SendOutlined />,
        label: '投递记录',
        onClick: () => navigate(ROUTES.CANDIDATE_APPLICATION),
        badge: 0,
      },
      {
        icon: <BellOutlined />,
        label: '消息通知',
        onClick: () => navigate(ROUTES.CANDIDATE_NOTIFICATION),
        badge: unreadCount.totalCount,
      },
      {
        icon: <SafetyOutlined />,
        label: '账号安全',
        onClick: () => navigate(ROUTES.CANDIDATE_SECURITY),
        badge: 0,
      },
    ],
    [navigate, unreadCount.totalCount],
  );

  /** 获取用户信息（带缓存，30秒内不重复请求） */
  const fetchUserInfo = useCallback(async () => {
    try {
      const userInfo = await cachedRequest(
        'user-info',
        () => getUserInfo(),
        30 * 1000, // 30秒缓存
      );
      if (isMountedRef.current) {
        setUser(userInfo);
      }
    } catch (error) {
      console.error('获取用户信息失败:', error);
    }
  }, []);

  /** 获取隐私设置（带缓存，30秒内不重复请求） */
  const fetchPrivacy = useCallback(async () => {
    try {
      const privacySettings = await cachedRequest(
        'privacy-settings',
        () => getPrivacySettings(),
        30 * 1000, // 30秒缓存
      );
      if (isMountedRef.current) {
        setPrivacy(privacySettings);
      }
    } catch (error) {
      console.error('获取隐私设置失败:', error);
    }
  }, []);

  // 初始化数据
  useEffect(() => {
    const init = async () => {
      setLoading(true);
      await Promise.all([fetchUserInfo(), fetchPrivacy()]);
      if (isMountedRef.current) {
        setLoading(false);
      }
    };
    init();

    return () => {
      isMountedRef.current = false;
    };
  }, [fetchUserInfo, fetchPrivacy]);

  /** 更新隐私设置（使用函数式更新避免过期状态） */
  const handlePrivacyChange = useCallback(
    async (key: keyof PrivacySettings, value: boolean | string) => {
      let oldValue: boolean | string | undefined;

      // 使用函数式更新获取最新状态
      setPrivacy((prev) => {
        if (!prev) return prev;
        oldValue = prev[key];
        return { ...prev, [key]: value };
      });

      try {
        await updatePrivacySettings({ [key]: value });
        message.success('设置已更新');
      } catch {
        // 回滚到旧值（函数式更新），错误已在拦截器中提示
        if (isMountedRef.current) {
          setPrivacy((prev) => {
            if (!prev) return prev;
            return { ...prev, [key]: oldValue! };
          });
        }
      }
    },
    [],
  );

  /** 盲选模式切换（需二次确认） */
  const handleBlindModeChange = useCallback(
    (checked: boolean) => {
      if (checked) {
        Modal.confirm({
          title: '确认开启盲选模式？',
          content: '开启后，企业将无法看到您的姓名和照片，仅展示技能画像。',
          okText: '确认开启',
          cancelText: '取消',
          onOk: () => handlePrivacyChange('blindMode', true),
        });
      } else {
        handlePrivacyChange('blindMode', false);
      }
    },
    [handlePrivacyChange],
  );

  /** 退出登录 */
  const handleLogout = useCallback(() => {
    Modal.confirm({
      title: '确认退出登录？',
      content: '退出后需要重新登录',
      okText: '确认退出',
      cancelText: '取消',
      onOk: () => {
        logout();
        message.success('已退出登录');
      },
    });
  }, [logout]);

  // 加载中
  if (loading) {
    return (
      <div className={styles.loading}>
        <Spin size="large" />
      </div>
    );
  }

  // 加载失败
  if (!user) {
    return <div className={styles.loading}>加载失败</div>;
  }

  return (
    <div className={styles.page}>
      {/* 个人信息卡片 */}
      <div className={styles.profileHeader}>
        <div className={styles.profileAvatar}>
          {user.avatar ? (
            <img src={user.avatar} alt="avatar" />
          ) : (
            user.name.charAt(0)
          )}
        </div>
        <div className={styles.profileInfo}>
          <div className={styles.profileName}>{user.name}</div>
          <div className={styles.profilePosition}>
            {user.profile?.desiredJob || '未设置求职意向'}
          </div>
          <div className={styles.profileMeta}>
            <span className={styles.profileMetaItem}>
              <EnvironmentOutlined /> {user.profile?.city || '未设置城市'}
            </span>
            <span className={styles.profileMetaItem}>
              <ClockCircleOutlined />{' '}
              {JOB_STATUS_TEXT[privacy?.jobStatus || ''] || '未设置'}
            </span>
          </div>
          {/* 求职意向详情 */}
          <div className={styles.profileDesired}>
            {user.profile?.desiredCity && (
              <span className={styles.desiredItem}>
                📍 期望城市：{user.profile.desiredCity}
              </span>
            )}
            {user.profile?.desiredSalaryMin && user.profile?.desiredSalaryMax && (
              <span className={styles.desiredItem}>
                💰 期望薪资：{user.profile.desiredSalaryMin / 1000}K-{user.profile.desiredSalaryMax / 1000}K
              </span>
            )}
            {user.profile?.availableFrom && (
              <span className={styles.desiredItem}>
                📅 到岗时间：{user.profile.availableFrom}
              </span>
            )}
          </div>
        </div>
        <Button
          icon={<EditOutlined />}
          size="large"
          onClick={() => setEditModalVisible(true)}
        >
          编辑资料
        </Button>
      </div>

      {/* 功能入口 */}
      <div className={styles.funcSection}>
        <div className={styles.funcTitle}>功能入口</div>
        {functionItems.map((item) => (
          <div
            key={item.label}
            className={styles.funcItem}
            onClick={item.onClick}
          >
            <div className={styles.funcItemLeft}>
              <div className={styles.funcItemIcon}>
                {React.cloneElement(item.icon, {
                  style: { color: 'var(--accent)', fontSize: 18 },
                })}
              </div>
              <span className={styles.funcItemLabel}>
                {item.label}
                {item.badge > 0 && (
                  <span className={styles.badge}>{item.badge}</span>
                )}
              </span>
            </div>
            <RightOutlined className={styles.funcItemArrow} />
          </div>
        ))}
      </div>

      {/* 隐私设置 */}
      <div className={styles.settingsSection}>
        <div className={styles.settingsTitle}>隐私设置</div>

        <div className={styles.settingItem}>
          <div>
            <div className={styles.settingLabel}>简历公开</div>
            <div className={styles.settingDesc}>
              允许HR搜索到你的简历，提高被找到的机会
            </div>
          </div>
          <Switch
            checked={privacy?.resumePublic ?? true}
            onChange={(checked) => handlePrivacyChange('resumePublic', checked)}
          />
        </div>

        <div className={styles.settingItem}>
          <div>
            <div className={styles.settingLabel}>匹配通知</div>
            <div className={styles.settingDesc}>
              当有高匹配岗位时，通过通知提醒你
            </div>
          </div>
          <Switch
            checked={privacy?.matchNotify ?? true}
            onChange={(checked) => handlePrivacyChange('matchNotify', checked)}
          />
        </div>

        <div className={styles.settingItem}>
          <div>
            <div className={styles.settingLabel}>盲选模式</div>
            <div className={styles.settingDesc}>
              隐藏个人信息，仅展示技能画像
            </div>
          </div>
          <Switch
            checked={privacy?.blindMode ?? false}
            onChange={handleBlindModeChange}
          />
        </div>

        <div className={styles.statusSection}>
          <div className={styles.statusLabel}>求职状态</div>
          <Select
            value={privacy?.jobStatus || 'JOB_SEEKING'}
            onChange={(value) => handlePrivacyChange('jobStatus', value)}
            style={{ width: 240 }}
            options={[
              { value: 'JOB_SEEKING', label: '求职中' },
              { value: 'EMPLOYED_LOOKING', label: '在职看机会' },
              { value: 'NOT_LOOKING', label: '暂不考虑' },
            ]}
          />
        </div>
      </div>

      {/* 退出登录 */}
      <div className={styles.logoutArea}>
        <Button
          danger
          icon={<LogoutOutlined />}
          size="large"
          onClick={handleLogout}
        >
          退出登录
        </Button>
      </div>

      {/* 编辑个人信息Modal */}
      {user && (
        <EditProfileModal
          visible={editModalVisible}
          user={user}
          onClose={() => setEditModalVisible(false)}
          onSuccess={() => {
            setEditModalVisible(false);
            fetchUserInfo(); // 刷新用户信息
          }}
        />
      )}
    </div>
  );
};

export default ProfilePage;
