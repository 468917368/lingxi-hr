import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate } from 'umi';
import { Button, Divider, Spin } from 'antd';
import {
  LeftOutlined,
  MobileOutlined,
  LockOutlined,
  MailOutlined,
  FileTextOutlined,
} from '@ant-design/icons';
import { getUserInfo } from '@/services/user';
import { maskPhone } from '@/utils/validators';
import type { UserInfo } from '@/constants/apiTypes';
import PhoneModal from './components/PhoneModal';
import PasswordModal from './components/PasswordModal';
import EmailModal from './components/EmailModal';
import LoginLogModal from './components/LoginLogModal';
import styles from './index.less';

const AccountSecurityPage = () => {
  const navigate = useNavigate();
  const isMountedRef = useRef(true);

  const [user, setUser] = useState<UserInfo | null>(null);
  const [loading, setLoading] = useState(true);

  // Modal可见性
  const [phoneModalVisible, setPhoneModalVisible] = useState(false);
  const [passwordModalVisible, setPasswordModalVisible] = useState(false);
  const [emailModalVisible, setEmailModalVisible] = useState(false);
  const [loginLogModalVisible, setLoginLogModalVisible] = useState(false);

  /** 获取用户信息 */
  const fetchUserInfo = useCallback(async () => {
    try {
      const userInfo = await getUserInfo();
      if (isMountedRef.current) {
        setUser(userInfo);
      }
    } catch (error) {
      console.error('获取用户信息失败:', error);
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    fetchUserInfo();
    return () => {
      isMountedRef.current = false;
    };
  }, [fetchUserInfo]);

  /** 操作成功回调 */
  const handleSuccess = useCallback(() => {
    fetchUserInfo();
  }, [fetchUserInfo]);

  if (loading) {
    return (
      <div className={styles.loading}>
        <Spin size="large" />
      </div>
    );
  }

  return (
    <div className={styles.page}>
      {/* 页面头部 */}
      <div className={styles.header}>
        <Button
          type="text"
          icon={<LeftOutlined />}
          onClick={() => navigate(-1)}
        >
          返回
        </Button>
        <h1 className={styles.title}>账号安全</h1>
      </div>

      {/* 安全设置列表 */}
      <div className={styles.securityList}>
        {/* 手机号 */}
        <div className={styles.securityItem}>
          <div className={styles.itemLeft}>
            <MobileOutlined className={styles.itemIcon} />
            <div>
              <div className={styles.itemLabel}>手机号</div>
              <div className={styles.itemValue}>
                {user?.phone ? maskPhone(user.phone) : '未绑定'}
              </div>
            </div>
          </div>
          <Button type="link" onClick={() => setPhoneModalVisible(true)}>
            修改
          </Button>
        </div>

        <Divider className={styles.divider} />

        {/* 密码 */}
        <div className={styles.securityItem}>
          <div className={styles.itemLeft}>
            <LockOutlined className={styles.itemIcon} />
            <div>
              <div className={styles.itemLabel}>登录密码</div>
              <div className={styles.itemValue}>已设置</div>
            </div>
          </div>
          <Button type="link" onClick={() => setPasswordModalVisible(true)}>
            修改
          </Button>
        </div>

        <Divider className={styles.divider} />

        {/* 邮箱 */}
        <div className={styles.securityItem}>
          <div className={styles.itemLeft}>
            <MailOutlined className={styles.itemIcon} />
            <div>
              <div className={styles.itemLabel}>邮箱</div>
              <div className={styles.itemValue}>
                {user?.email || '未绑定'}
              </div>
            </div>
          </div>
          <Button type="link" onClick={() => setEmailModalVisible(true)}>
            {user?.email ? '修改' : '绑定'}
          </Button>
        </div>

        <Divider className={styles.divider} />

        {/* 登录日志 */}
        <div className={styles.securityItem}>
          <div className={styles.itemLeft}>
            <FileTextOutlined className={styles.itemIcon} />
            <div>
              <div className={styles.itemLabel}>登录日志</div>
              <div className={styles.itemValue}>查看最近登录记录</div>
            </div>
          </div>
          <Button type="link" onClick={() => setLoginLogModalVisible(true)}>
            查看
          </Button>
        </div>
      </div>

      {/* 修改手机号 Modal */}
      <PhoneModal
        visible={phoneModalVisible}
        currentPhone={user?.phone || ''}
        onClose={() => setPhoneModalVisible(false)}
        onSuccess={handleSuccess}
      />

      {/* 修改密码 Modal */}
      <PasswordModal
        visible={passwordModalVisible}
        onClose={() => setPasswordModalVisible(false)}
        onSuccess={handleSuccess}
      />

      {/* 修改/绑定邮箱 Modal */}
      <EmailModal
        visible={emailModalVisible}
        currentEmail={user?.email}
        onClose={() => setEmailModalVisible(false)}
        onSuccess={handleSuccess}
      />

      {/* 登录日志 Modal */}
      <LoginLogModal
        visible={loginLogModalVisible}
        onClose={() => setLoginLogModalVisible(false)}
      />
    </div>
  );
};

export default AccountSecurityPage;
