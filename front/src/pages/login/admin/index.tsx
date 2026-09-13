import React, { useState, useCallback } from 'react';
import { Input, Typography, message, Modal, Form } from 'antd';
import { UserOutlined, LockOutlined } from '@ant-design/icons';
import { useNavigate } from 'umi';
import { useAuth } from '@/hooks/useAuth';
import { adminLogin, adminChangePassword } from '@/services/auth';
import styles from './index.less';

const { Text } = Typography;

const AdminLoginPage: React.FC = () => {
  const navigate = useNavigate();
  const { login } = useAuth();

  const [username, setUsername] = useState('');
  const [password, setPassword] = useState('');
  const [loading, setLoading] = useState(false);
  const [firstLogin, setFirstLogin] = useState(false);
  const [changePasswordVisible, setChangePasswordVisible] = useState(false);
  const [passwordForm] = Form.useForm();

  const handleLogin = useCallback(async () => {
    if (!username) {
      message.warning('请输入管理员账号');
      return;
    }
    if (!password) {
      message.warning('请输入密码');
      return;
    }
    setLoading(true);
    try {
      const response = await adminLogin({ username, password });
      message.success('管理员登录成功');
      login(response);
    } catch (error) {
      const apiError = error as { code?: number; message?: string };
      if (apiError.code === 5004) {
        setFirstLogin(true);
        setChangePasswordVisible(true);
      }
    } finally {
      setLoading(false);
    }
  }, [username, password, login]);

  const handleChangePassword = useCallback(async () => {
    try {
      const values = await passwordForm.validateFields();
      if (values.newPassword !== values.confirmPassword) {
        message.error('两次输入的新密码不一致');
        return;
      }
      await adminChangePassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
      });
      message.success('密码修改成功，请使用新密码重新登录');
      setChangePasswordVisible(false);
      setFirstLogin(false);
      passwordForm.resetFields();
      setPassword('');
    } catch {
      // form validation errors handled by Form component
    }
  }, [passwordForm]);

  return (
    <div className={`a-mode ${styles.wrapper}`}>
      {/* Left: Brand Panel */}
      <div className={styles.brandPanel}>
        <div className={styles.brandPhoto} />
        <div className={styles.brandOverlay} />
        <div className={styles.brandGrain} />
        {/* Floating particles */}
        <div className={styles.particles}>
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
          <div className={styles.particle} />
        </div>

        <div className={`${styles.brandShape} ${styles.shape1}`} />
        <div className={`${styles.brandShape} ${styles.shape2}`} />
        <div className={styles.panelEdge} />

        <div className={styles.brandTop}>
          <div className={styles.brandIcon}>灵</div>
          <span className={styles.brandName}>灵犀互聘</span>
        </div>

        <div className={styles.brandContent}>
          <h1 className={styles.brandHeadline}>
            管理后台<br />高效运营
          </h1>
          <p className={styles.brandDesc}>
            一站式招聘管理平台，企业管理、岗位审核、
            数据统计，让招聘运营更高效。
          </p>

          <div className={styles.brandFeatures}>
            <div className={styles.brandFeature}>
              <span className={styles.featureDot} />
              <span>企业管理与认证审核</span>
            </div>
            <div className={styles.brandFeature}>
              <span className={styles.featureDot} />
              <span>岗位发布与内容审核</span>
            </div>
            <div className={styles.brandFeature}>
              <span className={styles.featureDot} />
              <span>平台数据统计与分析</span>
            </div>
          </div>
        </div>

        <div className={styles.brandStats}>
          <div className={styles.brandStat}>
            <span className={styles.statNumber}>1200+</span>
            <span className={styles.statLabel}>入驻企业</span>
          </div>
          <div className={styles.brandStat}>
            <span className={styles.statNumber}>5000+</span>
            <span className={styles.statLabel}>在招岗位</span>
          </div>
          <div className={styles.brandStat}>
            <span className={styles.statNumber}>99.9%</span>
            <span className={styles.statLabel}>系统可用率</span>
          </div>
        </div>
      </div>

      {/* Right: Form Panel */}
      <div className={styles.formPanel}>
        <div className={styles.formPanelBg} />
        <div className={styles.panelEdge} />

        <div className={styles.formWrapper}>
          <div className={styles.formBrandMobile}>
            <div className={styles.formBrandIcon}>管</div>
            <span style={{ fontFamily: 'var(--font-title)', fontWeight: 700, fontSize: 18, color: 'var(--text)' }}>
              管理后台
            </span>
          </div>

          <div className={styles.formHeader}>
            <h1 className={styles.formTitle}>管理员登录</h1>
            <p className={styles.formSubtitle}>灵犀互聘管理后台</p>
          </div>

          <div className={styles.form}>
            <div className={styles.field}>
              <Input
                size="large"
                placeholder="请输入管理员账号"
                prefix={<UserOutlined style={{ color: 'var(--text-tertiary)' }} />}
                value={username}
                onChange={(e) => setUsername(e.target.value)}
                className={styles.input}
              />
            </div>
            <div className={styles.field}>
              <Input.Password
                size="large"
                placeholder="请输入密码"
                prefix={<LockOutlined style={{ color: 'var(--text-tertiary)' }} />}
                value={password}
                onChange={(e) => setPassword(e.target.value)}
                className={styles.input}
              />
            </div>

            <button
              className={styles.loginBtn}
              disabled={loading}
              onClick={handleLogin}
            >
              {loading ? '登录中...' : '登录'}
            </button>
          </div>

          <div className={styles.footer}>
            <Text className={styles.footerLink} onClick={() => navigate('/login')}>
              返回求职者登录
            </Text>
          </div>
        </div>
      </div>

      {/* 首次改密Modal */}
      <Modal
        title="修改初始密码"
        open={changePasswordVisible}
        onCancel={() => {
          if (!firstLogin) {
            setChangePasswordVisible(false);
            setFirstLogin(false);
          }
        }}
        footer={null}
        closable={!firstLogin}
        maskClosable={!firstLogin}
      >
        <Form form={passwordForm} onFinish={handleChangePassword} layout="vertical">
          <Form.Item
            name="oldPassword"
            label="初始密码"
            rules={[{ required: true, message: '请输入初始密码' }]}
          >
            <Input.Password placeholder="请输入初始密码" />
          </Form.Item>
          <Form.Item
            name="newPassword"
            label="新密码"
            rules={[
              { required: true, message: '请输入新密码' },
              {
                pattern: /^(?=.*[a-z])(?=.*[A-Z])(?=.*\d)(?=.*[^a-zA-Z0-9]).{8,20}$/,
                message: '密码须包含大小写字母、数字和特殊字符',
              },
            ]}
          >
            <Input.Password placeholder="请设置新密码（8-20位）" />
          </Form.Item>
          <Form.Item
            name="confirmPassword"
            label="确认新密码"
            dependencies={['newPassword']}
            rules={[
              { required: true, message: '请确认新密码' },
              ({ getFieldValue }) => ({
                validator(_, value) {
                  if (!value || getFieldValue('newPassword') === value) {
                    return Promise.resolve();
                  }
                  return Promise.reject(new Error('两次输入的密码不一致'));
                },
              }),
            ]}
          >
            <Input.Password placeholder="请确认新密码" />
          </Form.Item>
          <button className={styles.loginBtn} type="submit">
            确认修改
          </button>
        </Form>
      </Modal>
    </div>
  );
};

export default AdminLoginPage;
