import React, { useState, useEffect, useCallback } from 'react';
import { Select, Input, Table, Button, Space, Tabs, Modal, Drawer, message, Spin } from 'antd';
import { SearchOutlined, EyeOutlined, StopOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import { getAdminJobList, offlineJob } from '@/services/admin';
import styles from './index.less';

interface JobRecord {
  id: number;
  title: string;
  companyName: string;
  companyId: number;
  city: string;
  salary: string;
  status: string;
  headcount: string;
  applyCount: number;
  publishTime: string;
  content?: string;
  targetRole?: string;
}

/** 解析 headcount 字符串 "总名额/已确认" → { total, confirmed } */
const parseHeadcount = (hc: string) => {
  const parts = hc.split('/');
  return {
    total: parseInt(parts[0]) || 0,
    confirmed: parseInt(parts[1]) || 0,
  };
};

/** API 返回的英文状态 -> 中文展示 */
const STATUS_TO_CN: Record<string, string> = {
  PUBLISHED: '已发布',
  DRAFT: '草稿',
  CLOSED: '已关闭',
  PAUSED: '已暂停',
  ARCHIVED: '已归档',
};

/** 中文 -> 英文状态，用于筛选时反查 */
const CN_TO_STATUS: Record<string, string> = {
  '已发布': 'PUBLISHED',
  '草稿': 'DRAFT',
  '已关闭': 'CLOSED',
  '已暂停': 'PAUSED',
  '已归档': 'ARCHIVED',
};

const getStatusType = (status: string) => {
  // status 此时已经是中文
  const eng = CN_TO_STATUS[status];
  if (eng === 'PUBLISHED') return 'success' as const;
  if (eng === 'DRAFT') return 'neutral' as const;
  if (eng === 'CLOSED') return 'danger' as const;
  if (eng === 'PAUSED') return 'warning' as const;
  if (eng === 'ARCHIVED') return 'neutral' as const;
  return 'neutral' as const;
};

interface CompanyInfo {
  id: number;
  name: string;
  industry?: string;
}

const JobPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [jobs, setJobs] = useState<JobRecord[]>([]);
  const [companies, setCompanies] = useState<CompanyInfo[]>([]);
  const [activeTab, setActiveTab] = useState('全部');
  const [companyFilter, setCompanyFilter] = useState<number | undefined>(undefined);
  const [searchText, setSearchText] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentJob, setCurrentJob] = useState<JobRecord | null>(null);

  const fetchJobs = useCallback(async () => {
    setLoading(true);
    try {
      const res = await getAdminJobList() as unknown as {
        groups?: Array<{
          companyId: number; companyName: string; industry?: string;
          jobs: Array<{ id: number; title: string; status: string; city?: string; salary?: string; headcount?: string; applyCount?: number; publishTime?: string }>;
        }>;
        total?: number;
      };
      const groups = res?.groups ?? [];
      const list: JobRecord[] = [];
      const companyMap = new Map<number, CompanyInfo>();
      groups.forEach((group) => {
        companyMap.set(group.companyId, { id: group.companyId, name: group.companyName, industry: group.industry });
        (group.jobs || group.users || []).forEach((j) => {
          list.push({
            id: j.id,
            title: j.title,
            companyName: group.companyName,
            companyId: group.companyId,
            city: j.city || '-',
            salary: j.salary || '-',
            status: STATUS_TO_CN[j.status] || j.status,
            headcount: j.headcount || '0/0',
            applyCount: j.applyCount || 0,
            publishTime: j.publishTime || '-',
          });
        });
      });
      setJobs(list);
      setCompanies(Array.from(companyMap.values()));
    } catch (err: any) {
      message.error(err?.message || '获取岗位列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchJobs();
  }, [fetchJobs]);

  const filterData = () => {
    let filtered = [...jobs];
    if (activeTab !== '全部') {
      const engStatus = CN_TO_STATUS[activeTab];
      filtered = filtered.filter((d) => d.status === activeTab || (engStatus && d.status === engStatus));
    }
    if (companyFilter) {
      filtered = filtered.filter((d) => d.companyId === companyFilter);
    }
    if (searchText) {
      filtered = filtered.filter(
        (d) => d.title.includes(searchText) || d.companyName.includes(searchText)
      );
    }
    return filtered;
  };

  const handleTakeDown = (record: JobRecord) => {
    Modal.confirm({
      title: '确认下架',
      content: `确认下架「${record.companyName}」的岗位「${record.title}」？下架后该岗位将不再对外展示。`,
      okText: '确认下架',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await offlineJob(record.id);
          message.success(`已下架岗位「${record.title}」`);
          fetchJobs();
        } catch (err: any) {
          message.error(err?.message || '下架失败，请重试');
        }
      },
    });
  };

  const columns: ColumnsType<JobRecord> = [
    { title: '岗位名称', dataIndex: 'title', key: 'title', width: 160 },
    { title: '城市', dataIndex: 'city', key: 'city', width: 80 },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: string) => <StatusTag type={getStatusType(status)}>{status}</StatusTag>,
    },
    {
      title: 'HC（名额/已确认）',
      dataIndex: 'headcount',
      key: 'headcount',
      width: 130,
      render: (_: string, record: JobRecord) => {
        const { total, confirmed } = parseHeadcount(record.headcount);
        const remaining = Math.max(0, total - confirmed);
        return (
          <span>
            <span style={{ fontWeight: 600 }}>{record.headcount}</span>
            <span style={{ color: 'var(--text-tertiary)', marginLeft: 6 }}>
              剩余{remaining}
            </span>
          </span>
        );
      },
    },
    {
      title: '发布时间',
      dataIndex: 'publishTime',
      key: 'publishTime',
      width: 120,
      render: (t: string) => (t ? t.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 140,
      render: (_, record) => (
        <Space size={8}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => { setCurrentJob(record); setDrawerOpen(true); }}>
            详情
          </Button>
          {record.status === '已发布' && (
            <Button
              type="link"
              size="small"
              danger
              icon={<StopOutlined />}
              onClick={() => handleTakeDown(record)}
            >
              下架
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const filteredData = filterData();
  const groupedByCompany = filteredData.reduce<Record<string, JobRecord[]>>((acc, job) => {
    if (!acc[job.companyName]) acc[job.companyName] = [];
    acc[job.companyName].push(job);
    return acc;
  }, {});

  const companyOptions = companies.map((c) => ({
    label: c.name,
    value: c.id,
  }));

  return (
    <div className={styles.page}>
      <PageHeader title="岗位管理" description="管理平台所有企业发布的招聘岗位" />

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
            placeholder="搜索岗位名称"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 220 }}
            allowClear
          />
        </div>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          items={[
            { key: '全部', label: '全部' },
            { key: '已发布', label: '已发布' },
            { key: '草稿', label: '草稿' },
            { key: '已关闭', label: '已关闭' },
          ]}
        />
      </div>

      <Spin spinning={loading}>
        {Object.entries(groupedByCompany).map(([companyName, groupedJobs]) => {
          const company = companies.find((c) => c.name === companyName);
          return (
            <div key={companyName} className={styles.companySection}>
              <div className={styles.companyHeader}>
                <div className={styles.companyAvatar}>
                  {companyName.charAt(0)}
                </div>
                <div>
                  <div className={styles.companyName}>{companyName}</div>
                  <div className={styles.companyMeta}>
                    {company?.industry ? `${company.industry} | ` : ''}{groupedJobs.length} 个岗位
                  </div>
                </div>
              </div>
              <div className={styles.table}>
                <Table
                  columns={columns}
                  dataSource={groupedJobs}
                  rowKey="id"
                  pagination={false}
                />
              </div>
            </div>
          );
        })}
      </Spin>

      <Drawer
        title="岗位详情"
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={560}
      >
        {currentJob && (
          <div>
            <div className={styles.companySection}>
              <div className={styles.companyHeader}>
                <div className={styles.companyAvatar}>{(currentJob.companyName || '?').charAt(0)}</div>
                <div>
                  <div className={styles.companyName}>{currentJob.companyName || '-'}</div>
                </div>
              </div>
            </div>
            <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>岗位名称</div>
                <div style={{ fontSize: 16, fontWeight: 700, marginTop: 4 }}>{currentJob.title}</div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>城市</div>
                <div style={{ marginTop: 4 }}>{currentJob.city || '-'}</div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>状态</div>
                <div style={{ marginTop: 4 }}>{currentJob.status}</div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>HC</div>
                <div style={{ marginTop: 4 }}>
                  总名额 <strong>{currentJob.headcount}</strong>
                  <span style={{ color: 'var(--success)', fontWeight: 600, marginLeft: 8 }}>
                    （剩余可用 {Math.max(0, parseHeadcount(currentJob.headcount).total - parseHeadcount(currentJob.headcount).confirmed)}）
                  </span>
                </div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>投递数</div>
                <div style={{ marginTop: 4 }}>{currentJob.applyCount}</div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>薪资</div>
                <div style={{ marginTop: 4 }}>{currentJob.salary || '-'}</div>
              </div>
              <div>
                <div style={{ fontSize: 14, fontWeight: 600, color: 'var(--text-secondary)' }}>发布时间</div>
                <div style={{ marginTop: 4 }}>{currentJob.publishTime || '-'}</div>
              </div>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default JobPage;
