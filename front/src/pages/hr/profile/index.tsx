/**
 * HR 个人中心（/hr/profile，面试官端复用）
 * Tab1 账号信息：头像上传、姓名/部门编辑、手机号/邮箱改绑（验证码）；
 * Tab2 修改密码：前端校验，成功即 token 失效重新登录。
 */
import React, { useState, useEffect, useCallback } from 'react';
import {
  Tabs,
  Form,
  Input,
  Button,
  Card,
  message,
  Avatar,
  Spin,
  Upload,
} from 'antd';
import {
  UserOutlined,
  LockOutlined,
  MailOutlined,
  PhoneOutlined,
  BankOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import { getHrAccountProfile, updateHrAccountProfile, changeHrPassword } from '@/services/hr';
import { uploadAvatar } from '@/services/user';
import { useAuth } from '@/hooks/useAuth';
import useUserStore from '@/stores/userStore';
import { validatePassword } from '@/utils/validators';
import type { HrAccountProfile, UpdateHrAccountProfileRequest } from '@/constants/apiTypes';
import PhoneModal from './components/PhoneModal';
import EmailModal from './components/EmailModal';
import styles from './index.less';

const ProfilePage: React.FC = () => {
  const { logout } = useAuth();
  const setUserInfo = useUserStore((s) => s.setUserInfo);
  const [activeKey, setActiveKey] = useState('account');
  const [accountForm] = Form.useForm();
  const [passwordForm] = Form.useForm();

  const [profile, setProfile] = useState<HrAccountProfile | null>(null);
  const [avatarUrl, setAvatarUrl] = useState<string>('');
  const [loading, setLoading] = useState(true);
  const [savingAccount, setSavingAccount] = useState(false);
  const [uploadingAvatar, setUploadingAvatar] = useState(false);
  const [phoneModalVisible, setPhoneModalVisible] = useState(false);
  const [emailModalVisible, setEmailModalVisible] = useState(false);

  /** 拉取账号信息 */
  const fetchProfile = useCallback(async () => {
    try {
      const data = await getHrAccountProfile();
      setProfile(data);
      setAvatarUrl(data.avatar || '');
    } catch (error) {
      console.error('获取账号信息失败:', error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchProfile();
  }, [fetchProfile]);

  /** 头像上传（上传接口即已保存，无需再走「保存修改」） */
  const handleAvatarUpload = async (file: File) => {
    setUploadingAvatar(true);
    try {
      const url = await uploadAvatar(file);
      // 强制浏览器重新加载：静态 URL 追加时间戳；预签名 URL（含 ?，每次不同）原样避免破坏签名
      const displayUrl = url.includes('?') ? url : `${url}?t=${Date.now()}`;
      setAvatarUrl(displayUrl);
      // 同步本地与全局 store（右上角头像即时更新）
      setProfile((prev) => (prev ? { ...prev, avatar: displayUrl } : prev));
      const current = useUserStore.getState().userInfo;
      if (current) setUserInfo({ ...current, avatar: displayUrl });
      message.success('头像已上传');
    } catch (error) {
      // 上传失败错误已由拦截器提示
    } finally {
      setUploadingAvatar(false);
    }
    return false;
  };

  /** 保存账号信息（name/department 至少一个字段） */
  const handleSaveAccount = async () => {
    try {
      const values = await accountForm.validateFields();
      const payload: UpdateHrAccountProfileRequest = {};
      if (values.name && values.name !== profile?.name) payload.name = values.name;
      if (values.department && values.department !== profile?.department) payload.department = values.department;

      if (Object.keys(payload).length === 0) {
        message.info('没有需要保存的修改');
        return;
      }

      setSavingAccount(true);
      await updateHrAccountProfile(payload);
      message.success('账号信息已更新');
      // 同步本地状态与全局 store（右上角姓名）
      setProfile((prev) => (prev ? { ...prev, ...payload } : prev));
      const current = useUserStore.getState().userInfo;
      if (current) {
        setUserInfo({
          ...current,
          name: payload.name ?? current.name,
        });
      }
    } catch (error: any) {
      if (error?.code === 1114) {
        message.error('姓名修改过于频繁');
      } else {
        message.error(error?.message || '保存失败');
      }
    } finally {
      setSavingAccount(false);
    }
  };

  /** 修改密码（成功即 token 失效，跳转重新登录） */
  const handleChangePassword = async () => {
    try {
      const values = await passwordForm.validateFields();
      await changeHrPassword({
        oldPassword: values.oldPassword,
        newPassword: values.newPassword,
        confirmPassword: values.confirmPassword,
      });
      message.success('密码修改成功，请重新登录');
      logout();
    } catch (error) {
      // 旧密码错误等已由拦截器提示
    }
  };

  /** 邮箱修改成功：刷新账号信息显示 */
  const handleEmailSuccess = async () => {
    try {
      const data = await getHrAccountProfile();
      setProfile(data);
      setAvatarUrl(data.avatar || '');
      accountForm.setFieldsValue({ email: data.email });
    } catch (error) {
      console.error('刷新账号信息失败:', error);
    }
  };

  if (loading) {
    return (
      <div className={styles.page} style={{ display: 'flex', justifyContent: 'center', padding: '80px 0' }}>
        <Spin size="large" />
      </div>
    );
  }

  const tabItems = [
    {
      key: 'account',
      label: '账号信息',
      children: (
        <Card className={styles.card} bordered={false}>
          <Form
            form={accountForm}
            layout="vertical"
            initialValues={{
              name: profile?.name,
              phone: profile?.phone,
              email: profile?.email,
              department: profile?.department,
              companyName: profile?.companyName,
            }}
            className={styles.formSection}
          >
            <Form.Item label="头像">
              <Upload
                showUploadList={false}
                accept="image/png,image/jpeg"
                beforeUpload={handleAvatarUpload}
              >
                <Avatar
                  size={64}
                  src={avatarUrl || undefined}
                  icon={<UserOutlined />}
                  className={styles.avatarUploader}
                />
              </Upload>
              {uploadingAvatar && <div className={styles.uploadHint}>上传中...</div>}
            </Form.Item>

            <Form.Item
              name="name"
              label="姓名"
              rules={[
                { required: true, message: '请输入姓名' },
                { max: 32, message: '姓名长度须在1-32字符之间' },
              ]}
            >
              <Input prefix={<UserOutlined />} placeholder="请输入姓名" />
            </Form.Item>

            <Form.Item
              name="phone"
              label="手机号"
            >
              <Input
                prefix={<PhoneOutlined />}
                readOnly
                suffix={
                  <Button type="link" size="small" onClick={() => setPhoneModalVisible(true)}>
                    修改
                  </Button>
                }
              />
            </Form.Item>

            <Form.Item
              name="email"
              label="邮箱"
            >
              <Input
                prefix={<MailOutlined />}
                readOnly
                suffix={
                  <Button type="link" size="small" onClick={() => setEmailModalVisible(true)}>
                    修改
                  </Button>
                }
              />
            </Form.Item>

            <Form.Item
              name="department"
              label="部门"
              rules={[{ required: true, message: '请输入部门' }]}
            >
              <Input prefix={<BankOutlined />} placeholder="请输入部门" />
            </Form.Item>

            <Form.Item
              name="companyName"
              label="公司"
            >
              <Input prefix={<BankOutlined />} disabled />
            </Form.Item>

            <Form.Item>
              <Button
                type="primary"
                className={styles.saveBtn}
                loading={savingAccount}
                onClick={handleSaveAccount}
              >
                保存修改
              </Button>
            </Form.Item>
          </Form>
        </Card>
      ),
    },
    {
      key: 'password',
      label: '修改密码',
      children: (
        <Card className={styles.card} bordered={false}>
          <Form
            form={passwordForm}
            layout="vertical"
            className={styles.formSection}
          >
            <Form.Item
              name="oldPassword"
              label="当前密码"
              rules={[{ required: true, message: '请输入当前密码' }]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="请输入当前密码" />
            </Form.Item>

            <Form.Item
              name="newPassword"
              label="新密码"
              rules={[
                { required: true, validator: validatePassword },
              ]}
            >
              <Input.Password prefix={<LockOutlined />} placeholder="8-20位，含大小写字母、数字和特殊字符" />
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
              <Input.Password prefix={<LockOutlined />} placeholder="请再次输入新密码" />
            </Form.Item>

            <Form.Item>
              <Button type="primary" className={styles.saveBtn} onClick={handleChangePassword}>
                更新密码
              </Button>
            </Form.Item>
          </Form>
        </Card>
      ),
    },
  ];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero title="个人中心" desc="管理您的账号信息和安全设置" />

      {/* 账号信息 / 修改密码 */}
      <Tabs
        activeKey={activeKey}
        onChange={setActiveKey}
        items={tabItems}
        className={styles.profileTabs}
        style={{ marginTop: 24 }}
      />

      {/* 修改手机号（成功即重新登录） */}
      <PhoneModal
        visible={phoneModalVisible}
        currentPhone={profile?.phone || ''}
        onClose={() => setPhoneModalVisible(false)}
        onSuccess={logout}
      />

      {/* 修改邮箱 */}
      <EmailModal
        visible={emailModalVisible}
        currentEmail={profile?.email}
        onClose={() => setEmailModalVisible(false)}
        onSuccess={handleEmailSuccess}
      />
    </div>
  );
};

export default ProfilePage;
