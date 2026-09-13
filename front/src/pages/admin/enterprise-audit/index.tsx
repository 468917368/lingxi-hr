import React, { useState, useEffect, useCallback } from 'react';
import { Tabs, Input, Table, Button, Modal, Space, Spin, message } from 'antd';
import { SearchOutlined, CheckOutlined, CloseOutlined, EyeOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import {
  getCertificationList,
  getCertificationDetail,
  approveCertification,
  rejectCertification,
} from '@/services/admin';
import styles from './index.less';

// 类型定义
interface CertificationRecord {
  id: number;
  applicantName: string;
  companyName: string;
  industry: string;
  scale: string;
  applyTime: string;
  status: string;
  // 列表基础字段
  address?: string;
  contactPhone?: string;
  // 详情接口返回的完整字段
  contactPerson?: string;
  licenseUrl?: string;
  certMaterialUrl?: string;
  certRejectReason?: string;
  materials?: CertificationMaterial[];
  history?: CertificationHistory[];
}

interface CertificationMaterial {
  name: string;
  url: string;
}

interface CertificationHistory {
  operator: string;
  action: string;
  remark: string;
  operateTime: string;
}

interface CertificationStats {
  total: number;
  pending: number;
  approved: number;
  rejected: number;
}

const EnterpriseAuditPage: React.FC = () => {
  const [activeTab, setActiveTab] = useState('全部');
  const [searchText, setSearchText] = useState('');
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<CertificationRecord[]>([]);
  const [stats, setStats] = useState<CertificationStats>({ total: 0, pending: 0, approved: 0, rejected: 0 });
  const [rejectModalOpen, setRejectModalOpen] = useState(false);
  const [rejectReason, setRejectReason] = useState('');
  const [currentRecord, setCurrentRecord] = useState<CertificationRecord | null>(null);
  const [expandedRows, setExpandedRows] = useState<number[]>([]);
  const [detailCache, setDetailCache] = useState<Record<number, CertificationRecord>>({});
  const [detailLoading, setDetailLoading] = useState<Set<number>>(new Set());
  const [pagination, setPagination] = useState({ current: 1, pageSize: 10, total: 0 });

  // 获取全局统计（不受筛选影响）
  const fetchStats = useCallback(async () => {
    try {
      const data = await getCertificationList({ page: 1, size: 50 });
      const responseData = data as unknown as { list: CertificationRecord[] };
      const allData = responseData.list || [];
      setStats({
        pending: allData.filter((d: CertificationRecord) => d.status === 'PENDING').length,
        approved: allData.filter((d: CertificationRecord) => d.status === 'APPROVED').length,
        rejected: allData.filter((d: CertificationRecord) => d.status === 'REJECTED').length,
        total: allData.length,
      });
    } catch { /* 静默失败 */ }
  }, []);

  // 获取审核列表
  const fetchCertifications = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: pagination.current,
        size: pagination.pageSize,
      };

      // 状态筛选
      if (activeTab !== '全部') {
        const statusMap: Record<string, string> = {
          '待审核': 'PENDING',
          '已通过': 'APPROVED',
          '已拒绝': 'REJECTED',
        };
        params.status = statusMap[activeTab];
      }

      // 搜索关键词
      if (searchText) {
        params.keyword = searchText;
      }

      const data = await getCertificationList(params);
      const responseData = data as unknown as { list: CertificationRecord[]; total: number };

      setDataSource(responseData.list || []);
      setPagination((prev) => ({ ...prev, total: responseData.total || 0 }));
    } catch (error) {
      message.error('获取审核列表失败');
    } finally {
      setLoading(false);
    }
  }, [activeTab, searchText, pagination.current, pagination.pageSize]);

  // 初始化全局统计
  useEffect(() => {
    fetchStats();
  }, [fetchStats]);

  // 初始化列表数据
  useEffect(() => {
    fetchCertifications();
  }, [fetchCertifications]);

  // 通过审核
  const handleApprove = (record: CertificationRecord) => {
    Modal.confirm({
      title: '确认通过',
      content: `确认通过「${record.companyName}」的企业认证审核？`,
      okText: '确认通过',
      cancelText: '取消',
      onOk: async () => {
        try {
          await approveCertification(record.id);
          message.success(`已通过「${record.companyName}」的认证审核`);
          fetchStats();
          fetchCertifications();
        } catch (error) {
          message.error('审核操作失败，请重试');
        }
      },
    });
  };

  // 打开拒绝弹窗
  const handleReject = (record: CertificationRecord) => {
    setCurrentRecord(record);
    setRejectReason('');
    setRejectModalOpen(true);
  };

  // 确认拒绝
  const confirmReject = async () => {
    if (!rejectReason.trim()) {
      message.warning('请填写拒绝原因');
      return;
    }
    if (currentRecord) {
      try {
        await rejectCertification(currentRecord.id, rejectReason);
        message.success(`已拒绝「${currentRecord.companyName}」的认证申请`);
        setRejectModalOpen(false);
        setRejectReason('');
        setCurrentRecord(null);
        fetchStats();
        fetchCertifications();
      } catch (error) {
        message.error('审核操作失败，请重试');
      }
    }
  };

  // 展开/收起详情（按需加载详情数据）
  const handleToggleExpand = useCallback(
    (record: CertificationRecord) => {
      const isExpanded = expandedRows.includes(record.id);
      if (isExpanded) {
        setExpandedRows((prev) => prev.filter((id) => id !== record.id));
      } else {
        setExpandedRows((prev) => [...prev, record.id]);
        // 按需加载完整详情
        if (!detailCache[record.id]) {
          setDetailLoading((prev) => new Set(prev).add(record.id));
          getCertificationDetail(record.id)
            .then((data) => {
              setDetailCache((prev) => ({ ...prev, [record.id]: data as CertificationRecord }));
            })
            .catch(() => {
              // 加载失败，展开行使用列表数据
            })
            .finally(() => {
              setDetailLoading((prev) => {
                const next = new Set(prev);
                next.delete(record.id);
                return next;
              });
            });
        }
      }
    },
    [expandedRows, detailCache],
  );

  // 状态映射
  const getStatusDisplay = (status: string) => {
    const statusMap: Record<string, { text: string; type: 'warning' | 'success' | 'danger' | 'neutral' }> = {
      PENDING: { text: '待审核', type: 'warning' },
      APPROVED: { text: '已通过', type: 'success' },
      REJECTED: { text: '已拒绝', type: 'danger' },
    };
    return statusMap[status] || { text: status, type: 'neutral' };
  };

  // 表格列定义
  const columns: ColumnsType<CertificationRecord> = [
    { title: '企业名称', dataIndex: 'companyName', key: 'companyName', width: 200 },
    { title: '行业', dataIndex: 'industry', key: 'industry', width: 140 },
    { title: '规模', dataIndex: 'scale', key: 'scale', width: 100 },
    {
      title: '提交时间',
      dataIndex: 'applyTime',
      key: 'applyTime',
      width: 160,
      render: (t: string) => (t ? t.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: string) => {
        const { text, type } = getStatusDisplay(status);
        return <StatusTag type={type}>{text}</StatusTag>;
      },
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={8}>
          <Button
            type="link"
            size="small"
            icon={<EyeOutlined />}
            onClick={() => handleToggleExpand(record)}
          >
            详情
          </Button>
          {record.status === 'PENDING' && (
            <>
              <Button
                type="link"
                size="small"
                icon={<CheckOutlined />}
                style={{ color: '#059669' }}
                onClick={() => handleApprove(record)}
              >
                通过
              </Button>
              <Button
                type="link"
                size="small"
                danger
                icon={<CloseOutlined />}
                onClick={() => handleReject(record)}
              >
                拒绝
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <PageHeader title="企业认证审核" description="审核企业提交的认证资料，保障平台企业真实性" />

      {/* 统计卡片 */}
      <div className={styles.statRow}>
        <div className={styles.statCard}>
          <div className={styles.statValue}>{stats.pending}</div>
          <div className={styles.statLabel}>待审核</div>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statValue}>{stats.approved}</div>
          <div className={styles.statLabel}>已通过</div>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statValue}>{stats.rejected}</div>
          <div className={styles.statLabel}>已拒绝</div>
        </div>
        <div className={styles.statCard}>
          <div className={styles.statValue}>{stats.total}</div>
          <div className={styles.statLabel}>总计</div>
        </div>
      </div>

      {/* 筛选栏 */}
      <div className={styles.filterBar}>
        <div className={styles.filterLeft}>
          <Tabs
            activeKey={activeTab}
            onChange={(key) => {
              setActiveTab(key);
              setPagination((prev) => ({ ...prev, current: 1 }));
            }}
            items={[
              { key: '全部', label: `全部 (${stats.total})` },
              { key: '待审核', label: `待审核 (${stats.pending})` },
              { key: '已通过', label: `已通过 (${stats.approved})` },
              { key: '已拒绝', label: `已拒绝 (${stats.rejected})` },
            ]}
          />
        </div>
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

      {/* 表格 */}
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
            expandable={{
              expandedRowRender: (record) => {
                const detail = detailCache[record.id] || record;
                const isLoading = detailLoading.has(record.id);
                return (
                  <Spin spinning={isLoading}>
                    <div className={styles.expandRow}>
                      <div className={styles.expandSection}>
                        <div className={styles.expandTitle}>企业信息</div>
                        <div className={styles.expandContent}>
                          <div className={styles.expandItem}>
                            <span className={styles.expandLabel}>企业名称</span>
                            <span className={styles.expandValue}>{detail.companyName}</span>
                          </div>
                          <div className={styles.expandItem}>
                            <span className={styles.expandLabel}>行业</span>
                            <span className={styles.expandValue}>{detail.industry}</span>
                          </div>
                          <div className={styles.expandItem}>
                            <span className={styles.expandLabel}>规模</span>
                            <span className={styles.expandValue}>{detail.scale}</span>
                          </div>
                          <div className={styles.expandItem}>
                            <span className={styles.expandLabel}>地址</span>
                            <span className={styles.expandValue}>{detail.address || '-'}</span>
                          </div>
                          <div className={styles.expandItem}>
                            <span className={styles.expandLabel}>联系人</span>
                            <span className={styles.expandValue}>{detail.contactPerson || detail.applicantName || '-'}</span>
                          </div>
                          <div className={styles.expandItem}>
                            <span className={styles.expandLabel}>联系电话</span>
                            <span className={styles.expandValue}>{detail.contactPhone || '-'}</span>
                          </div>
                          {detail.certRejectReason && (
                            <div className={styles.expandItem}>
                              <span className={styles.expandLabel}>拒绝原因</span>
                              <span className={styles.expandValue} style={{ color: 'var(--danger)' }}>
                                {detail.certRejectReason}
                              </span>
                            </div>
                          )}
                        </div>
                      </div>
                      {detail.materials && detail.materials.length > 0 && (
                        <div className={styles.expandSection}>
                          <div className={styles.expandTitle}>认证材料</div>
                          <div className={styles.materialPreview}>
                            {detail.materials.map((m, i) => (
                              <img key={i} src={m.url} alt={m.name} className={styles.materialImage} />
                            ))}
                          </div>
                        </div>
                      )}
                      {detail.licenseUrl && (
                        <div className={styles.expandSection}>
                          <div className={styles.expandTitle}>营业执照</div>
                          <div className={styles.materialPreview}>
                            <img src={detail.licenseUrl} alt="营业执照" className={styles.materialImage} />
                          </div>
                        </div>
                      )}
                      {detail.history && detail.history.length > 0 && (
                        <div className={styles.expandSection}>
                          <div className={styles.expandTitle}>审核记录</div>
                          <div className={styles.historyList}>
                            {detail.history.map((h, i) => (
                              <div key={i} className={styles.historyItem}>
                                <span className={styles.historyTime}>{h.operateTime ? h.operateTime.replace('T', ' ').slice(0, 16) : '-'}</span>
                                <span className={styles.historyAction}>{h.action}</span>
                                <span className={styles.historyOperator}>{h.operator}</span>
                                {h.remark && <span className={styles.historyRemark}>{h.remark}</span>}
                              </div>
                            ))}
                          </div>
                        </div>
                      )}
                    </div>
                  </Spin>
                );
              },
              expandedRowKeys: expandedRows,
              onExpand: (expanded, record) => {
                if (expanded) {
                  handleToggleExpand(record);
                } else {
                  setExpandedRows((prev) => prev.filter((id) => id !== record.id));
                }
              },
            }}
          />
        </Spin>
      </div>

      {/* 拒绝原因弹窗 */}
      <Modal
        title="拒绝原因"
        open={rejectModalOpen}
        onOk={confirmReject}
        onCancel={() => {
          setRejectModalOpen(false);
          setRejectReason('');
          setCurrentRecord(null);
        }}
        okText="确认拒绝"
        cancelText="取消"
        okButtonProps={{ danger: true }}
        className={styles.reasonModal}
      >
        <div style={{ marginBottom: 8, fontSize: 14, color: 'var(--text-secondary)' }}>
          请填写拒绝「{currentRecord?.companyName}」认证申请的原因：
        </div>
        <Input.TextArea
          rows={4}
          value={rejectReason}
          onChange={(e) => setRejectReason(e.target.value)}
          placeholder="请输入拒绝原因..."
        />
      </Modal>
    </div>
  );
};

export default EnterpriseAuditPage;
