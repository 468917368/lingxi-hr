import React, { useState, useEffect, useCallback } from 'react';
import { Select, Input, DatePicker, Table, Button, Spin, message } from 'antd';
import { SearchOutlined, ExportOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import { getAuditLogs, exportAuditLogs } from '@/services/admin';
import styles from './index.less';

const { RangePicker } = DatePicker;

/** 操作类型枚举 -> 中文展示 */
const TYPE_LABELS: Record<string, string> = {
  LOGIN: '用户登录',
  REVIEW: '审核操作',
  DISABLE: '禁用操作',
  ENABLE: '启用操作',
  OFFLINE: '下架操作',
  ANNOUNCE: '公告操作',
  CONFIG: '配置修改',
};

interface AuditLogRecord {
  id: number;
  createTime: string;
  userId: number;
  userName: string;
  operationType: string;
  detail: string;
  ip: string;
}

const getOperateTypeStyle = (type: string) => {
  if (type === 'REVIEW' || type === 'ENABLE') return 'success' as const;
  if (type === 'DISABLE' || type === 'OFFLINE') return 'danger' as const;
  if (type === 'CONFIG') return 'warning' as const;
  if (type === 'ANNOUNCE') return 'primary' as const;
  return 'info' as const;
};

const AuditLogPage: React.FC = () => {
  const [loading, setLoading] = useState(false);
  const [dataSource, setDataSource] = useState<AuditLogRecord[]>([]);
  const [total, setTotal] = useState(0);
  const [currentPage, setCurrentPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);
  const [typeFilter, setTypeFilter] = useState<string>('全部');
  const [searchText, setSearchText] = useState('');
  const [startDate, setStartDate] = useState<string | undefined>(undefined);
  const [endDate, setEndDate] = useState<string | undefined>(undefined);

  const fetchAuditLogs = useCallback(async () => {
    setLoading(true);
    try {
      const params: Record<string, unknown> = {
        page: currentPage,
        size: pageSize,
      };
      if (typeFilter !== '全部') {
        params.type = typeFilter;
      }
      if (startDate) {
        params.startDate = startDate;
      }
      if (endDate) {
        params.endDate = endDate;
      }
      const res = await getAuditLogs(params);
      const responseData = res as unknown as { list: AuditLogRecord[]; total: number };
      setDataSource(responseData.list || []);
      setTotal(responseData.total || 0);
    } catch (error: any) {
      message.error(error?.message || '获取操作日志失败');
    } finally {
      setLoading(false);
    }
  }, [typeFilter, startDate, endDate, currentPage, pageSize]);

  useEffect(() => {
    fetchAuditLogs();
  }, [fetchAuditLogs]);

  const handleTypeChange = (value: string) => {
    setTypeFilter(value);
    setCurrentPage(1);
  };

  const handleDateChange = (_: any, dateStrings: [string, string]) => {
    setStartDate(dateStrings[0] || undefined);
    setEndDate(dateStrings[1] || undefined);
    setCurrentPage(1);
  };

  const handlePageChange = (page: number, size: number) => {
    setCurrentPage(page);
    setPageSize(size);
  };

  const handleExport = async () => {
    try {
      const params: Record<string, unknown> = {};
      if (typeFilter !== '全部') params.type = typeFilter;
      if (startDate) params.startDate = startDate;
      if (endDate) params.endDate = endDate;

      const response = await exportAuditLogs(params);
      const blob = (response as unknown as { data: Blob }).data;
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = 'audit-logs.xlsx';
      link.click();
      window.URL.revokeObjectURL(url);
      message.success('导出成功');
    } catch {
      message.error('导出失败');
    }
  };

  // 客户端搜索过滤（对当前页数据）
  const filteredData = searchText
    ? dataSource.filter(
        (d) =>
          String(d.userId).includes(searchText) ||
          d.detail?.includes(searchText),
      )
    : dataSource;

  const columns: ColumnsType<AuditLogRecord> = [
    {
      title: '操作时间',
      dataIndex: 'createTime',
      key: 'createTime',
      width: 170,
      render: (t: string) => (t ? t.replace('T', ' ').slice(0, 19) : '-'),
    },
    { title: '操作人ID', dataIndex: 'userId', key: 'userId', width: 100 },
    {
      title: '操作类型',
      dataIndex: 'operationType',
      key: 'operationType',
      width: 110,
      render: (type: string) => (
        <StatusTag type={getOperateTypeStyle(type)}>
          {TYPE_LABELS[type] || type}
        </StatusTag>
      ),
    },
    { title: 'IP地址', dataIndex: 'ip', key: 'ip', width: 150 },
    { title: '详情', dataIndex: 'detail', key: 'detail', ellipsis: true },
  ];

  const typeOptions = [
    { label: '全部', value: '全部' },
    ...Object.entries(TYPE_LABELS).map(([value, label]) => ({
      label,
      value,
    })),
  ];

  return (
    <div className={styles.page}>
      <PageHeader
        title="操作日志"
        description="查看管理员操作记录，追踪系统变更"
        extra={
          <Button icon={<ExportOutlined />} onClick={handleExport}>导出</Button>
        }
      />

      <div className={styles.filterBar}>
        <div className={styles.filterLeft}>
          <Select
            value={typeFilter}
            onChange={handleTypeChange}
            style={{ width: 150 }}
            options={typeOptions}
          />
          <RangePicker
            style={{ width: 260 }}
            placeholder={['开始日期', '结束日期']}
            onChange={handleDateChange}
          />
          <Input
            placeholder="搜索操作人/对象/详情"
            prefix={<SearchOutlined />}
            value={searchText}
            onChange={(e) => setSearchText(e.target.value)}
            style={{ width: 260 }}
            allowClear
          />
        </div>
      </div>

      <div className={styles.table}>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={filteredData}
            rowKey="id"
            pagination={{
              current: currentPage,
              pageSize,
              total,
              showSizeChanger: false,
              onChange: handlePageChange,
            }}
          />
        </Spin>
      </div>
    </div>
  );
};

export default AuditLogPage;
