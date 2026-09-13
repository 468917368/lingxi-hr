import React, { useState, useEffect } from 'react';
import { Select, Input, Table, Button, Space, Modal, Drawer, message, Spin } from 'antd';
import { SearchOutlined, EyeOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import { getHRList, enableHR, disableHR } from '@/services/admin';
import styles from './index.less';

interface HRRecord {
  id: number;
  name: string;
  position: string;
  phone: string;
  email: string;
  status: string;
  companyId: number;
  companyName: string;
  industry?: string;
}

interface CompanyGroup {
  company: string;
  industry: string;
  members: HRRecord[];
}

const HRPage: React.FC = () => {
  const [searchText, setSearchText] = useState('');
  const [companyFilter, setCompanyFilter] = useState<number | undefined>(undefined);
  const [loading, setLoading] = useState(false);
  const [hrDataByCompany, setHrDataByCompany] = useState<Record<number, CompanyGroup>>({});
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentHR, setCurrentHR] = useState<HRRecord | null>(null);

  const fetchHRList = async () => {
    setLoading(true);
    try {
      const data = (await getHRList()) as unknown as Array<{
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
            companyId: group.companyId,
            companyName: group.companyName,
          })),
        };
      });
      setHrDataByCompany(grouped);
    } catch (error: any) {
      message.error(error?.message || '获取HR列表失败');
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchHRList();
  }, []);

  const allHRs = Object.values(hrDataByCompany).flatMap((c) => c.members);

  const filteredData = allHRs.filter((hr) => {
    if (companyFilter && hr.companyId !== companyFilter) return false;
    if (searchText) {
      return (
        hr.name.includes(searchText) ||
        hr.companyName.includes(searchText) ||
        hr.phone.includes(searchText)
      );
    }
    return true;
  });

  const groupedByCompany = filteredData.reduce<Record<string, HRRecord[]>>((acc, hr) => {
    if (!acc[hr.companyName]) acc[hr.companyName] = [];
    acc[hr.companyName].push(hr);
    return acc;
  }, {});

  const handleDisable = (record: HRRecord) => {
    Modal.confirm({
      title: '确认禁用',
      content: `确认禁用「${record.companyName}」的HR成员「${record.name}」？`,
      okText: '确认禁用',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await disableHR(record.id);
          message.success(`已禁用HR「${record.name}」`);
          fetchHRList();
        } catch (error: any) {
          message.error(error?.message || '禁用失败');
        }
      },
    });
  };

  const handleEnable = (record: HRRecord) => {
    Modal.confirm({
      title: '确认启用',
      content: `确认启用「${record.companyName}」的HR成员「${record.name}」？`,
      okText: '确认启用',
      cancelText: '取消',
      onOk: async () => {
        try {
          await enableHR(record.id);
          message.success(`已启用HR「${record.name}」`);
          fetchHRList();
        } catch (error: any) {
          message.error(error?.message || '启用失败');
        }
      },
    });
  };

  const columns: ColumnsType<HRRecord> = [
    { title: '姓名', dataIndex: 'name', key: 'name', width: 100 },
    { title: '职位', dataIndex: 'position', key: 'position', width: 120 },
    { title: '手机', dataIndex: 'phone', key: 'phone', width: 140 },
    { title: '邮箱', dataIndex: 'email', key: 'email', width: 200 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => (
        <StatusTag type={status === 'ACTIVE' ? 'success' : 'danger'}>{
          status === 'ACTIVE' ? '正常' : '禁用'
        }</StatusTag>
      ),
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={8}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => { setCurrentHR(record); setDrawerOpen(true); }}>
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

  const companyOptions = Object.entries(hrDataByCompany).map(([id, data]) => ({
    label: data.company,
    value: Number(id),
  }));

  return (
    <div className={styles.page}>
      <PageHeader title="HR管理" description="管理各企业HR成员账号" />

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
            placeholder="搜索HR姓名或企业名称"
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
          const companyInfo = Object.values(hrDataByCompany).find((c) => c.company === companyName);
          return (
            <div key={companyName} className={styles.companySection}>
              <div className={styles.companyHeader}>
                <div className={styles.companyAvatar}>
                  {companyName.charAt(0)}
                </div>
                <div>
                  <div className={styles.companyName}>{companyName}</div>
                  <div className={styles.companyMeta}>
                    {companyInfo?.industry} | {members.length} 位HR
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
        title="HR详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={480}
      >
        {currentHR && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>姓名</div>
              <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4 }}>{currentHR.name}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>所属企业</div>
              <div style={{ marginTop: 4 }}>{currentHR.companyName}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>部门/职位</div>
              <div style={{ marginTop: 4 }}>{currentHR.position || '-'}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>手机</div>
              <div style={{ marginTop: 4 }}>{currentHR.phone || '-'}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>邮箱</div>
              <div style={{ marginTop: 4 }}>{currentHR.email || '-'}</div>
            </div>
            <div>
              <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>状态</div>
              <div style={{ marginTop: 4 }}>
                <StatusTag type={currentHR.status === 'ACTIVE' ? 'success' : 'danger'}>
                  {currentHR.status === 'ACTIVE' ? '正常' : '禁用'}
                </StatusTag>
              </div>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default HRPage;
