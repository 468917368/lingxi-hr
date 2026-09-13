import React, { useState, useEffect, useCallback } from 'react';
import { Select, Input, Table, Button, Drawer, Spin, message } from 'antd';
import { SearchOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import { getCompanyList, getCompanyDetail } from '@/services/admin';
import styles from './index.less';

interface EnterpriseRecord {
  id: number;
  name: string;
  industry: string;
  scale: string;
  address: string;
  certStatus: string;
  jobCount: number;
  hrCount: number;
}

interface MemberRecord {
  name: string;
  department: string;
  phone: string;
  email: string;
  status: string;
}

interface CompanyDetail {
  id: number;
  name: string;
  industry: string;
  scale: string;
  address: string;
  certStatus: string;
  contactPerson: string;
  contactPhone: string;
  businessLicenseUrl: string;
  certMaterialUrl: string;
  certRejectReason: string;
  jobCount: number;
  hrCount: number;
  members: MemberRecord[];
}

const certStatusLabels: Record<string, string> = {
  PENDING: '待审核',
  APPROVED: '已通过',
  REJECTED: '已拒绝',
  DISABLED: '已禁用',
};

const getCertStatusType = (status: string) => {
  if (status === 'APPROVED') return 'success' as const;
  if (status === 'PENDING') return 'warning' as const;
  if (status === 'REJECTED') return 'danger' as const;
  if (status === 'DISABLED') return 'neutral' as const;
  return 'neutral' as const;
};

const EnterprisePage: React.FC = () => {
  const [certFilter, setCertFilter] = useState<string>('全部');
  const [searchText, setSearchText] = useState('');
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<EnterpriseRecord[]>([]);
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [currentCompany, setCurrentCompany] = useState<CompanyDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);

  const fetchCompanyList = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };
      if (certFilter !== '全部') {
        params.certStatus = certFilter;
      }
      if (searchText) {
        params.keyword = searchText;
      }
      const data = await getCompanyList(params);
      const responseData = data as unknown as { list: Array<Record<string, unknown>>; total: number };
      const mapped = (responseData.list || []).map((item: Record<string, unknown>) => ({
        ...item,
        companyName: item.name || item.companyName, // backend uses 'name'
      })) as EnterpriseRecord[];
      setDataSource(mapped);
      setPagination((prev) => ({ ...prev, total: responseData.total || 0 }));
    } catch {
      message.error('获取企业列表失败');
    } finally {
      setLoading(false);
    }
  }, [certFilter, searchText, pagination.current, pagination.pageSize]);

  useEffect(() => {
    fetchCompanyList();
  }, [fetchCompanyList]);

  const handleDetail = async (record: EnterpriseRecord) => {
    setDrawerOpen(true);
    setDetailLoading(true);
    try {
      const data = await getCompanyDetail(record.id);
      const detailData = data as unknown as CompanyDetail;
      setCurrentCompany(detailData);
    } catch {
      message.error('获取企业详情失败');
      setDrawerOpen(false);
    } finally {
      setDetailLoading(false);
    }
  };

  const columns: ColumnsType<EnterpriseRecord> = [
    { title: '企业名称', dataIndex: 'name', key: 'name', width: 180 },
    { title: '行业', dataIndex: 'industry', key: 'industry', width: 140 },
    { title: '规模', dataIndex: 'scale', key: 'scale', width: 100 },
    { title: '地址', dataIndex: 'address', key: 'address', width: 140 },
    {
      title: '认证状态',
      dataIndex: 'certStatus',
      key: 'certStatus',
      width: 100,
      render: (status: string) => <StatusTag type={getCertStatusType(status)}>{certStatusLabels[status] || status}</StatusTag>,
    },
    { title: '在招岗位', dataIndex: 'jobCount', key: 'jobCount', width: 90 },
    { title: 'HR数', dataIndex: 'hrCount', key: 'hrCount', width: 80 },
    {
      title: '操作',
      key: 'action',
      width: 100,
      render: (_, record) => (
        <Button type="link" size="small" icon={<EyeOutlined />} onClick={() => handleDetail(record)}>
          详情
        </Button>
      ),
    },
  ];

  const memberColumns: ColumnsType<MemberRecord> = [
    { title: '姓名', dataIndex: 'name', key: 'name' },
    { title: '职位', dataIndex: 'department', key: 'department' },
    { title: '手机', dataIndex: 'phone', key: 'phone' },
    { title: '邮箱', dataIndex: 'email', key: 'email' },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      render: (status: string) => (
        <StatusTag type={status === 'ACTIVE' ? 'success' : 'danger'}>{status === 'ACTIVE' ? '正常' : '禁用'}</StatusTag>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <PageHeader title="企业管理" description="管理平台注册企业信息及成员" />

      <div className={styles.filterBar}>
        <div className={styles.filterLeft}>
          <Select
            value={certFilter}
            onChange={(value) => {
              setCertFilter(value);
              setPagination((prev) => ({ ...prev, current: 1 }));
            }}
            style={{ width: 140 }}
            options={[
              { label: '全部状态', value: '全部' },
              { label: '已认证', value: 'APPROVED' },
              { label: '待审核', value: 'PENDING' },
              { label: '已拒绝', value: 'REJECTED' },
              { label: '已禁用', value: 'DISABLED' },
            ]}
          />
          <Input
            placeholder="搜索企业名称"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => {
              setSearchText(e.target.value);
              setPagination((prev) => ({ ...prev, current: 1 }));
            }}
            style={{ width: 240 }}
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
        title={currentCompany?.name || '企业详情'}
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        width={640}
      >
        <Spin spinning={detailLoading}>
          {currentCompany && (
            <div className={styles.drawerContent}>
              <div className={styles.drawerSection}>
                <div className={styles.drawerTitle}>企业信息</div>
                <div className={styles.drawerInfo}>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>企业名称</div>
                    <div className={styles.drawerValue}>{currentCompany.name}</div>
                  </div>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>行业</div>
                    <div className={styles.drawerValue}>{currentCompany.industry}</div>
                  </div>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>规模</div>
                    <div className={styles.drawerValue}>{currentCompany.scale}</div>
                  </div>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>地址</div>
                    <div className={styles.drawerValue}>{currentCompany.address || '-'}</div>
                  </div>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>联系人</div>
                    <div className={styles.drawerValue}>{currentCompany.contactPerson || '-'}</div>
                  </div>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>联系电话</div>
                    <div className={styles.drawerValue}>{currentCompany.contactPhone || '-'}</div>
                  </div>
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>认证状态</div>
                    <div className={styles.drawerValue}>
                      <StatusTag type={getCertStatusType(currentCompany.certStatus)}>
                        {certStatusLabels[currentCompany.certStatus] || currentCompany.certStatus}
                      </StatusTag>
                    </div>
                  </div>
                  {currentCompany.certRejectReason && (
                    <div className={styles.drawerItem}>
                      <div className={styles.drawerLabel}>拒绝原因</div>
                      <div className={styles.drawerValue}>{currentCompany.certRejectReason}</div>
                    </div>
                  )}
                  <div className={styles.drawerItem}>
                    <div className={styles.drawerLabel}>在招岗位</div>
                    <div className={styles.drawerValue}>{currentCompany.jobCount} 个</div>
                  </div>
                </div>
              </div>

              <div className={styles.drawerSection}>
                <div className={styles.drawerTitle}>成员列表</div>
                <Table
                  className={styles.memberTable}
                  columns={memberColumns}
                  dataSource={currentCompany.members || []}
                  rowKey="name"
                  pagination={false}
                  size="small"
                />
              </div>
            </div>
          )}
        </Spin>
      </Drawer>
    </div>
  );
};

export default EnterprisePage;
