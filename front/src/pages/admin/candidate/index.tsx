import React, { useState, useEffect, useCallback } from 'react';
import { Input, Table, Button, Space, Drawer, Modal, Spin, message } from 'antd';
import { SearchOutlined, EyeOutlined, StopOutlined, CheckCircleOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import { getAdminCandidateList, getCandidateApplications, enableCandidate, disableCandidate } from '@/services/admin';
import styles from './index.less';

interface CandidateRecord {
  id: number;
  name: string;
  phone: string;
  email: string;
  city: string;
  jobStatus: string;
  expectedPosition: string;
  resumeCount: number;
  applyCount: number;
  registerTime: string;
  status: string;
}

interface ApplicationRecord {
  id: number;
  jobTitle: string;
  companyName: string;
  status: string;
  appliedAt: string;
  lastUpdatedAt: string;
}

const CandidatePage: React.FC = () => {
  const [searchText, setSearchText] = useState('');
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentCandidate, setCurrentCandidate] = useState<CandidateRecord | null>(null);
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<CandidateRecord[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  const fetchCandidates = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };
      if (searchText) {
        params.keyword = searchText;
      }
      const data = await getAdminCandidateList(params);
      const responseData = data as unknown as { list: CandidateRecord[]; total: number };
      setDataSource(responseData.list || []);
      setPagination((prev) => ({ ...prev, total: responseData.total || 0 }));
    } catch (error) {
      message.error('获取求职者列表失败，请稍后重试');
    } finally {
      setLoading(false);
    }
  }, [searchText, pagination.current, pagination.pageSize]);

  useEffect(() => {
    fetchCandidates();
  }, [fetchCandidates]);

  const [applications, setApplications] = useState<ApplicationRecord[]>([]);
  const [appLoading, setAppLoading] = useState(false);

  // 打开详情，加载投递历史
  const handleDetail = async (record: CandidateRecord) => {
    setCurrentCandidate(record);
    setDrawerOpen(true);
    setAppLoading(true);
    try {
      const data = await getCandidateApplications(record.id, { page: 1, size: 20 });
      // 兼容两种返回格式：{ records } 或 { list } 或直接是数组
      const arr = (data as any)?.records || (data as any)?.list || data;
      setApplications(Array.isArray(arr) ? arr : []);
    } catch (e) {
      console.error('获取投递历史失败', e);
      setApplications([]);
    } finally {
      setAppLoading(false);
    }
  };

  const handleDisable = (record: CandidateRecord) => {
    Modal.confirm({
      title: '确认禁用',
      content: `确认禁用求职者「${record.name}」？`,
      okText: '确认禁用',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await disableCandidate(record.id);
          message.success(`已禁用求职者「${record.name}」`);
          fetchCandidates();
        } catch (error) {
          message.error('操作失败，请稍后重试');
        }
      },
    });
  };

  const handleEnable = (record: CandidateRecord) => {
    Modal.confirm({
      title: '确认启用',
      content: `确认启用求职者「${record.name}」？`,
      okText: '确认启用',
      cancelText: '取消',
      onOk: async () => {
        try {
          await enableCandidate(record.id);
          message.success(`已启用求职者「${record.name}」`);
          fetchCandidates();
        } catch (error) {
          message.error('操作失败，请稍后重试');
        }
      },
    });
  };

  const columns: ColumnsType<CandidateRecord> = [
    { title: '姓名', dataIndex: 'name', key: 'name', width: 80 },
    { title: '手机', dataIndex: 'phone', key: 'phone', width: 130 },
    { title: '城市', dataIndex: 'city', key: 'city', width: 70 },
    { title: '城市', dataIndex: 'city', key: 'city', width: 70 },
    {
      title: '求职状态',
      dataIndex: 'jobStatus',
      key: 'jobStatus',
      width: 100,
      render: (status: string) => {
        const statusMap: Record<string, { text: string; type: 'success' | 'warning' | 'neutral' }> = {
          ACTIVELY_LOOKING: { text: '主动求职', type: 'success' },
          EMPLOYED_LOOKING: { text: '在职看机会', type: 'warning' },
          NOT_LOOKING: { text: '暂不考虑', type: 'neutral' },
          JOB_SEEKING: { text: '求职中', type: 'success' },
        };
        const item = statusMap[status] || { text: status, type: 'neutral' as const };
        return <StatusTag type={item.type}>{item.text}</StatusTag>;
      },
    },
    { title: '简历数', dataIndex: 'resumeCount', key: 'resumeCount', width: 70 },
    { title: '投递数', dataIndex: 'applyCount', key: 'applyCount', width: 70 },
    {
      title: '注册时间',
      dataIndex: 'registerTime',
      key: 'registerTime',
      width: 110,
      render: (t: string) => (t ? t.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 80,
      render: (status: string) => {
        const type = status === 'ACTIVE' ? 'success' as const : 'danger' as const;
        const text = status === 'ACTIVE' ? '正常' : '禁用';
        return <StatusTag type={type}>{text}</StatusTag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 200,
      render: (_, record) => (
        <Space size={8}>
          <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleDetail(record)}>
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

  return (
    <div className={styles.page}>
      <PageHeader title="求职者管理" description="管理平台注册求职者信息及求职状态" />

      <div className={styles.filterBar}>
        <div className={styles.filterLeft}>
          <Input
            placeholder="搜索姓名或手机号"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => {
              setSearchText(e.target.value);
              setPagination((prev) => ({ ...prev, current: 1 }));
            }}
            style={{ width: 280 }}
            allowClear
          />
        </div>
      </div>

      <div className={styles.table}>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={dataSource}
            rowKey="id"
            pagination={{
              ...pagination,
              showSizeChanger: false,
              onChange: (page, pageSize) => {
                setPagination((prev) => ({ ...prev, current: page, pageSize }));
              },
            }}
          />
        </Spin>
      </div>

      <Drawer
        title={currentCandidate?.name || '求职者详情'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={600}
      >
        {currentCandidate && (
          <div className={styles.drawerContent}>
            <div className={styles.drawerSection}>
              <div className={styles.drawerTitle}>基本信息</div>
              <div className={styles.drawerInfo}>
                <div className={styles.drawerItem}>
                  <div className={styles.drawerLabel}>姓名</div>
                  <div className={styles.drawerValue}>{currentCandidate.name}</div>
                </div>
                <div className={styles.drawerItem}>
                  <div className={styles.drawerLabel}>手机</div>
                  <div className={styles.drawerValue}>{currentCandidate.phone}</div>
                </div>
                <div className={styles.drawerItem}>
                  <div className={styles.drawerLabel}>邮箱</div>
                  <div className={styles.drawerValue}>{currentCandidate.email}</div>
                </div>
                <div className={styles.drawerItem}>
                  <div className={styles.drawerLabel}>城市</div>
                  <div className={styles.drawerValue}>{currentCandidate.city || '-'}</div>
                </div>
                <div className={styles.drawerItem}>
                  <div className={styles.drawerLabel}>求职状态</div>
                  <div className={styles.drawerValue}>{currentCandidate.jobStatus || '-'}</div>
                </div>
                <div className={styles.drawerItem}>
                  <div className={styles.drawerLabel}>注册时间</div>
                  <div className={styles.drawerValue}>{currentCandidate.registerTime}</div>
                </div>
              </div>
            </div>

            <div className={styles.drawerSection}>
              <div className={styles.drawerTitle}>投递历史</div>
              <Spin spinning={appLoading}>
                {applications.length > 0 ? (
                  <div className={styles.appList}>
                    {applications.map((app) => (
                      <div key={app.id} className={styles.appItem}>
                        <div>
                          <div className={styles.appJobTitle}>{app.jobTitle}</div>
                          <div className={styles.appCompany}>{app.companyName}</div>
                        </div>
                        <div className={styles.appMeta}>
                          <StatusTag type={
                            app.status === 'VIEWED' ? 'primary' :
                            app.status === 'INTERVIEWING' ? 'warning' :
                            app.status === 'REJECTED' ? 'danger' : 'success'
                          }>
                            {({ SUBMITTED: '已投递', VIEWED: 'HR已查看', SCREENED: '筛选通过', INTERVIEWING: '面试中', OFFERABLE: '可录用', OFFERED: '已录用', REJECTED: '已淘汰', WITHDRAWN: '已撤回' } as Record<string, string>)[app.status] || app.status}
                          </StatusTag>
                          <span style={{ color: 'var(--text-tertiary)', fontSize: 12 }}>
                            {app.appliedAt}
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                ) : (
                  <div style={{ color: 'var(--text-tertiary)', fontSize: 14 }}>暂无投递记录</div>
                )}
              </Spin>
            </div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default CandidatePage;
