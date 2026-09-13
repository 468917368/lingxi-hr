/**
 * HR 公司管理（/hr/company）
 * Tab1 公司信息：编辑/保存、邀请码复制与刷新（刷新后旧码失效）；
 * Tab2 成员管理：成员列表、添加面试官（手机号+验证码）、移除成员（仅面试官可移除）。
 */
import React, { useState, useEffect, useCallback, useRef } from 'react';
import {
  Tabs,
  Form,
  Input,
  Select,
  Button,
  Table,
  Card,
  message,
  Modal,
  Typography,
  Spin,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  DeleteOutlined,
  CopyOutlined,
  ReloadOutlined,
  UserOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import {
  getCompanyInfo,
  updateCompanyInfo,
  listMembers,
  createMember,
  removeMember,
  refreshInviteCode,
  type HrCompanyInfo,
  type HrMemberItem,
} from '@/services/company';
import { sendCode } from '@/services/auth';
import type { CertStatus } from '@/constants/apiTypes';
import styles from './index.less';

const { Text } = Typography;

const certStatusLabel: Record<CertStatus, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
};

const certStatusIcon: Record<CertStatus, React.ReactNode> = {
  PENDING: <ClockCircleOutlined style={{ color: '#FAAD14' }} />,
  APPROVED: <CheckCircleOutlined style={{ color: '#059669' }} />,
  REJECTED: <CloseCircleOutlined style={{ color: '#FF4D4F' }} />,
};

const roleLabel: Record<string, string> = {
  HR_ADMIN: 'HR负责人',
  INTERVIEWER: '面试官',
};

const isHrAdmin = (role: string) => role === 'HR_ADMIN';

const CompanyPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('info');
  const [form] = Form.useForm();
  const [memberForm] = Form.useForm();

  const [company, setCompany] = useState<HrCompanyInfo | null>(null);
  const [memberList, setMemberList] = useState<HrMemberItem[]>([]);
  const [memberLoading, setMemberLoading] = useState(false);
  const [memberModal, setMemberModal] = useState(false);
  const [savingMember, setSavingMember] = useState(false);
  const [savingCompany, setSavingCompany] = useState(false);

  const [codeCountdown, setCodeCountdown] = useState(0);
  const [sendingCode, setSendingCode] = useState(false);
  const countdownRef = useRef<ReturnType<typeof setInterval>>();

  const loadCompany = useCallback(async () => {
    try {
      const data = await getCompanyInfo();
      setCompany(data);
      form.setFieldsValue({
        name: data.name,
        industry: data.industry,
        scale: data.scale,
        address: data.address,
      });
    } catch {
      // 错误已在拦截器提示
    }
  }, [form]);

  const loadMembers = useCallback(async () => {
    setMemberLoading(true);
    try {
      const data = await listMembers({ status: 'ACTIVE' });
      setMemberList(data);
    } catch {
      // 错误已在拦截器提示
    } finally {
      setMemberLoading(false);
    }
  }, []);

  useEffect(() => {
    loadCompany();
    loadMembers();
  }, [loadCompany, loadMembers]);

  useEffect(() => {
    if (codeCountdown <= 0) return;
    const timer = setInterval(() => {
      setCodeCountdown((prev) => (prev <= 1 ? 0 : prev - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [codeCountdown]);

  useEffect(() => {
    return () => {
      if (countdownRef.current) clearInterval(countdownRef.current);
    };
  }, []);

  const handleSaveCompany = () => {
    form.validateFields().then(async (values) => {
      setSavingCompany(true);
      try {
        await updateCompanyInfo({
          name: values.name,
          industry: values.industry,
          scale: values.scale,
          address: values.address,
        });
        message.success('公司信息已保存');
        await loadCompany();
      } catch {
        // 错误已在拦截器提示
      } finally {
        setSavingCompany(false);
      }
    });
  };

  const handleCopyCode = () => {
    if (!company?.inviteCode) return;
    navigator.clipboard.writeText(company.inviteCode).then(() => {
      message.success('邀请码已复制到剪贴板');
    });
  };

  const handleRefreshCode = () => {
    Modal.confirm({
      title: '刷新邀请码',
      content: '刷新后旧邀请码将立即失效，已加入成员不受影响。确定刷新吗？',
      okText: '确认刷新',
      cancelText: '取消',
      onOk: async () => {
        try {
          const newCode = await refreshInviteCode();
          setCompany((prev) => (prev ? { ...prev, inviteCode: newCode } : prev));
          message.success('邀请码已刷新');
        } catch {
          // 错误已在拦截器提示
        }
      },
    });
  };

  const handleAddMember = () => {
    memberForm.resetFields();
    setMemberModal(true);
  };

  const handleDeleteMember = (member: HrMemberItem) => {
    Modal.confirm({
      title: '确认移除成员',
      content: `确定要移除面试官"${member.name}"吗？`,
      okText: '确认移除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await removeMember(member.id);
          message.success('成员已移除');
          await loadMembers();
        } catch {
          // 错误已在拦截器提示
        }
      },
    });
  };

  const handleSendCode = async () => {
    const phone = memberForm.getFieldValue('phone');
    if (!phone || !/^1[3-9]\d{9}$/.test(phone)) {
      message.warning('请输入正确的手机号');
      return;
    }
    setSendingCode(true);
    try {
      await sendCode({ phone });
      message.success('验证码已发送');
      setCodeCountdown(60);
    } catch {
      // 错误已在拦截器提示
    } finally {
      setSendingCode(false);
    }
  };

  const handleSaveMember = () => {
    memberForm.validateFields().then(async (values) => {
      setSavingMember(true);
      try {
        await createMember({
          phone: values.phone,
          code: values.code,
          password: values.password,
          name: values.name,
          department: values.department,
          techDirection: values.techDirection,
          email: values.email,
        });
        message.success('成员已添加');
        setMemberModal(false);
        memberForm.resetFields();
        await loadMembers();
      } catch {
        // 错误已在拦截器提示
      } finally {
        setSavingMember(false);
      }
    });
  };

  const memberColumns: ColumnsType<HrMemberItem> = [
    {
      title: '成员',
      dataIndex: 'name',
      key: 'name',
      width: 160,
      render: (name: string, record) => (
        <div className={styles.memberCell}>
          <div className={styles.memberAvatar}>
            {record.avatar ? (
              <img src={record.avatar} alt={name} className={styles.memberAvatarImg} />
            ) : (
              <UserOutlined />
            )}
          </div>
          <span className={styles.memberName}>{name}</span>
        </div>
      ),
    },
    {
      title: '角色',
      dataIndex: 'role',
      key: 'role',
      width: 130,
      render: (role: string) => (
        <span className={`${styles.roleTag} ${isHrAdmin(role) ? styles.roleHR : styles.roleInterviewer}`}>
          {roleLabel[role] || role}
        </span>
      ),
    },
    {
      title: '手机号',
      dataIndex: 'phone',
      key: 'phone',
      width: 150,
    },
    {
      title: '加入时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 130,
      render: (time: string) => (time ? time.slice(0, 10) : '-'),
    },
    {
      title: '操作',
      key: 'actions',
      width: 100,
      render: (_: unknown, record: HrMemberItem) =>
        !isHrAdmin(record.role) ? (
          <Button
            type="link"
            size="small"
            danger
            onClick={() => handleDeleteMember(record)}
          >
            <DeleteOutlined /> 移除
          </Button>
        ) : null,
    },
  ];

  const tabItems = [
    { key: 'info', label: '公司信息' },
    { key: 'members', label: '成员管理' },
  ];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero title="公司管理" desc="管理公司基本信息和团队成员" />

      <Card className={styles.card} bordered={false}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={tabItems}
          className={styles.tabs}
        />

        {activeTab === 'info' && (
          <div className={styles.tabContent}>
            {!company ? (
              <Spin style={{ display: 'block', margin: '40px auto' }} />
            ) : (
              <Form form={form} layout="vertical" className={styles.companyForm}>
                <Form.Item
                  name="name"
                  label="公司名称"
                  rules={[{ required: true, message: '请输入公司名称' }]}
                >
                  <Input placeholder="请输入公司名称" disabled={company.certStatus === 'APPROVED'} />
                </Form.Item>

                <Form.Item
                  name="industry"
                  label="所属行业"
                  rules={[{ required: true, message: '请选择行业' }]}
                >
                  <Select
                    options={[
                      { value: '互联网/电商', label: '互联网/电商' },
                      { value: '企业服务/SaaS', label: '企业服务/SaaS' },
                      { value: '大数据/AI', label: '大数据/AI' },
                      { value: '金融科技', label: '金融科技' },
                      { value: '教育科技', label: '教育科技' },
                      { value: '医疗健康', label: '医疗健康' },
                    ]}
                  />
                </Form.Item>

                <Form.Item
                  name="scale"
                  label="公司规模"
                  rules={[{ required: true, message: '请选择规模' }]}
                >
                  <Select
                    options={[
                      { value: '1-49人', label: '1-49人' },
                      { value: '50-99人', label: '50-99人' },
                      { value: '100-499人', label: '100-499人' },
                      { value: '500-999人', label: '500-999人' },
                      { value: '1000人以上', label: '1000人以上' },
                    ]}
                  />
                </Form.Item>

                <Form.Item
                  name="address"
                  label="公司地址"
                  rules={[{ required: true, message: '请输入地址' }]}
                >
                  <Input placeholder="请输入公司详细地址" />
                </Form.Item>

                <Form.Item label="认证状态">
                  <div className={styles.certStatus}>
                    {certStatusIcon[company.certStatus]}
                    <span>{certStatusLabel[company.certStatus]}</span>
                  </div>
                </Form.Item>

                <div className={styles.inviteSection}>
                  <div className={styles.inviteLabel}>邀请码</div>
                  <div className={styles.inviteRow}>
                    <Input value={company.inviteCode} readOnly className={styles.inviteInput} />
                    <Button icon={<CopyOutlined />} className={styles.inviteBtn} onClick={handleCopyCode}>
                      复制
                    </Button>
                    <Button icon={<ReloadOutlined />} className={styles.inviteBtnOutline} onClick={handleRefreshCode}>
                      刷新
                    </Button>
                  </div>
                  <Text type="secondary" className={styles.inviteHint}>
                    将邀请码分享给团队成员，他们可以通过邀请码加入公司
                  </Text>
                </div>

                <Form.Item>
                  <Button type="primary" className={styles.saveBtn} onClick={handleSaveCompany} loading={savingCompany}>
                    保存修改
                  </Button>
                </Form.Item>
              </Form>
            )}
          </div>
        )}

        {activeTab === 'members' && (
          <div className={styles.tabContent}>
            <div className={styles.memberHeader}>
              <span className={styles.memberCount}>共 {memberList.length} 位成员</span>
              <Button type="primary" className={styles.addBtn} icon={<PlusOutlined />} onClick={handleAddMember}>
                添加成员
              </Button>
            </div>

            <Table
              columns={memberColumns}
              dataSource={memberList}
              rowKey="id"
              className={styles.table}
              pagination={false}
              loading={memberLoading}
              locale={{ emptyText: '暂无成员' }}
            />
          </div>
        )}
      </Card>

      {/* 添加成员 Modal（创建面试官） */}
      <Modal
        title="添加成员"
        open={memberModal}
        onCancel={() => setMemberModal(false)}
        onOk={handleSaveMember}
        okText="保存"
        cancelText="取消"
        confirmLoading={savingMember}
        className={styles.memberModal}
      >
        <Form form={memberForm} layout="vertical" className={styles.memberForm}>
          <Form.Item
            name="phone"
            label="手机号"
            rules={[
              { required: true, message: '请输入手机号' },
              { pattern: /^1[3-9]\d{9}$/, message: '手机号格式不正确' },
            ]}
          >
            <Input placeholder="请输入面试官手机号" maxLength={11} />
          </Form.Item>
          <Form.Item
            name="code"
            label="短信验证码"
            rules={[
              { required: true, message: '请输入验证码' },
              { pattern: /^\d{6}$/, message: '验证码为6位数字' },
            ]}
          >
            <Input
              placeholder="请输入验证码"
              maxLength={6}
              suffix={
                <Button
                  size="small"
                  type="link"
                  disabled={codeCountdown > 0 || sendingCode}
                  onClick={handleSendCode}
                >
                  {sendingCode ? '发送中...' : codeCountdown > 0 ? `${codeCountdown}s` : '获取验证码'}
                </Button>
              }
            />
          </Form.Item>
          <Form.Item
            name="password"
            label="初始密码"
            rules={[
              { required: true, message: '请输入初始密码' },
              { min: 8, max: 20, message: '密码长度须在8-20位之间' },
            ]}
          >
            <Input.Password placeholder="8-20位" />
          </Form.Item>
          <Form.Item
            name="name"
            label="姓名"
            rules={[{ required: true, message: '请输入姓名' }]}
          >
            <Input placeholder="请输入成员姓名" />
          </Form.Item>
          <Form.Item
            name="department"
            label="所属部门"
            rules={[{ required: true, message: '请输入所属部门' }]}
          >
            <Input placeholder="如：技术部" />
          </Form.Item>
          <Form.Item name="techDirection" label="技术方向">
            <Input placeholder="如：前端/后端/算法（选填）" />
          </Form.Item>
          <Form.Item
            name="email"
            label="邮箱"
            rules={[{ type: 'email', message: '邮箱格式不正确' }]}
          >
            <Input placeholder="选填" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CompanyPage;
