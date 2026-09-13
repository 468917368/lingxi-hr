import React, { useState, useEffect, useCallback } from 'react';
import { Select, Input, Table, Button, Space, Modal, Drawer, Spin, message } from 'antd';
import { SearchOutlined, EyeOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import { getInterviewerList, enableInterviewer, disableInterviewer } from '@/services/admin';
import styles from './index.less';

interface InterviewerRecord {
  id: number;
  name: string;
  position: string;
  phone: string;
  email: string;
  status: string;
  companyId: number;
  companyName: string;
  interviewCount: number;
}

type CompanyGroup = { company: string; industry: string; members: InterviewerRecord[] };

const InterviewerPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [searchText, setSearchText] = useState('');
  const [companyFilter, setCompanyFilter] = useState<number | undefined>(undefined);
  const [companyData, setCompanyData] = useState<Record<number, CompanyGroup>>({});
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentInterviewer, setCurrentInterviewer] = useState<InterviewerRecord | null>(null);

  const fetchInterviewers = useCallback(async () => {
    setLoading(true);
    try {
      const data = (await getInterviewerList()) as unknown as Array<{
        companyId: number;
        companyName: string;
        users: Array<{ id: number; name: string; department: string; status: string }>;
      }>;
      // Backend returns [{companyId, companyName, users: [...]}]
      const grouped: Record<number, CompanyGroup> = {};
      (data || []).forEach((group) => {
        grouped[group.companyId] = {
          company: group.companyName,
          industry: '',
          members: group.users.map((u) => ({
            id: u.id,
            name: u.name,
            position: u.department || '-',
            phone: u.phone || '-',
            email: u.email || '-',
            status: u.status,
            interviewCount: u.interviewCount ?? 0,
            companyId: group.companyId,
            companyName: group.companyName,
          })),
        };
      });
      setCompanyData(grouped);
    } catch (error) {
      message.error('获取面试官列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchInterviewers();
  }, [fetchInterviewers]);

  const allInterviewers = Object.values(companyData).flatMap((c) => c.members);

  const filteredData = allInterviewers.filter((ir) => {
    if (companyFilter && ir.companyId !== companyFilter) return false;
    if (searchText) {
      return (
        ir.name.includes(searchText) ||
        ir.companyName.includes(searchText) ||
        ir.phone.includes(searchText)
      );
    }
    return true;
  });

  const groupedByCompany = filteredData.reduce<Record<string, InterviewerRecord[]>>((acc, ir) => {
    if (!acc[ir.companyName]) acc[ir.companyName] = [];
    acc[ir.companyName].push(ir);
    return acc;
  }, {});

  const handleDisable = (record: InterviewerRecord) => {
    Modal.confirm({
      title: '确认禁用',
      content: `确认禁用「${record.companyName}」的面试官「${record.name}」？禁用后该面试官将无法接收新的面试任务。`,
      okText: '确认禁用',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await disableInterviewer(record.id);
          message.success(`已禁用面试官「${record.name}」`);
          fetchInterviewers();
        } catch (error) {
          message.error('禁用面试官失败');
        }
      },
    });
  };

  const handleEnable = (record: InterviewerRecord) => {
    Modal.confirm({
      title: '确认启用',
      content: `确认启用「${record.companyName}」的面试官「${record.name}」？`,
      okText: '确认启用',
      cancelText: '取消',
      onOk: async () => {
        try {
          await enableInterviewer(record.id);
          message.success(`已启用面试官「${record.name}」`);
          fetchInterviewers();
        } catch (error) {
          message.error('启用面试官失败');
        }
      },
    });
  };

  const columns: ColumnsType<InterviewerRecord> = [
    { title: '姓名', dataIndex: 'name', key: 'name', width: 100 },
    { title: '职位', dataIndex: 'position', key: 'position', width: 140 },
    { title: '手机', dataIndex: 'phone', key: 'phone', width: 140 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
    { title: '面试场次', dataIndex: 'interviewCount', key: 'interviewCount', width: 90 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <StatusTag type={status === 'ACTIVE' ? 'success' : 'danger'}>{status === 'ACTIVE' ? '正常' : '禁用'}</StatusTag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={8}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => { setCurrentInterviewer(record); setDrawerOpen(true); }}>
            详情
          </Button>
          {record.status === 'ACTIVE' ? (
            <Button
              type="link"
              size="small"
              danger
              icon={<StopOutlined />}
              onClick={() => handleDisable(record)}
            >
              禁用
            </Button>
          ) : (
            <Button
              type="link"
              size="small"
              icon={<CheckCircleOutlined />}
              style={{ color: '#059669' }}
              onClick={() => handleEnable(record)}
            >
              启用
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const companyOptions = Object.entries(companyData).map(([id, data]) => ({
    label: data.company,
    value: Number(id),
  }));

  return (
    <div className={styles.page}>
      <PageHeader title="面试官管理" description="管理各企业面试官成员账号" />

      <div className={styles.filterBar}>
        <div className={styles.filterLeft}>
          <Select
            placeholder="选择企业"
            allowClear
            value={companyFilter}
            onChange={setCompanyFilter}
            style={{ width: 180 }}
            options={companyOptions}
          />
          <Input
            placeholder="搜索面试官姓名或企业名称"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 280 }}
            allowClear
          />
        </div>
      </div>

      <Spin spinning={loading}>
        {Object.entries(groupedByCompany).map(([companyName, members]) => {
          const companyInfo = Object.values(companyData).find((c) => c.company === companyName);
          return (
            <div key={companyName} className={styles.companySection}>
              <div className={styles.companyHeader}>
                <div className={styles.companyAvatar}>
                  {companyName.charAt(0)}
                </div>
                <div>
                  <div className={styles.companyName}>{companyName}</div>
                  <div className={styles.companyMeta}>
                    {companyInfo?.industry} | {members.length} 位面试官
                  </div>
                </div>
              </div>
              <div className={styles.table}>
                <Table
                  columns={columns}
                  dataSource={members}
                  rowKey="id"
                  pagination={false}
                />
              </div>
            </div>
          );
        })}
      </Spin>

      <Drawer
        title="面试官详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
      >
        {currentInterviewer && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>姓名</div>
              <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4 }}>{currentInterviewer.name}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>所属企业</div>
              <div style={{ marginTop: 4 }}>{currentInterviewer.companyName}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>部门/职位</div>
              <div style={{ marginTop: 4 }}>{currentInterviewer.position || '-'}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>手机</div>
              <div style={{ marginTop: 4 }}>{currentInterviewer.phone || '-'}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>邮箱</div>
              <div style={{ marginTop: 4 }}>{currentInterviewer.email || '-'}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>面试场次</div>
              <div style={{ marginTop: 4 }}>{currentInterviewer.interviewCount} 场</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>状态</div>
              <div style={{ marginTop: 4 }}>
                <StatusTag type={currentInterviewer.status === 'ACTIVE' ? 'success' : 'danger'}>
                  {currentInterviewer.status === 'ACTIVE' ? '正常' : '禁用'}
                </StatusTag>
              </div>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default InterviewerPage;
