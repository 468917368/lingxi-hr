import React, { useState, useEffect, useRef, useMemo, useCallback } from 'react';
import { Modal, Table, Tag, Spin } from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  CheckCircleOutlined,
  CloseCircleOutlined,
} from '@ant-design/icons';
import { getLoginLogs } from '@/services/user';
import type { LoginLog } from '@/constants/apiTypes';

interface LoginLogModalProps {
  visible: boolean;
  onClose: () => void;
}

/** 格式化设备信息 */
const formatDevice = (device: string): string => {
  if (!device) return '未知设备';
  const match = device.match(/\(([^)]+)\)/);
  if (match) {
    return match[1];
  }
  return device.length > 30 ? device.substring(0, 30) + '...' : device;
};

const LoginLogModal = ({ visible, onClose }: LoginLogModalProps) => {
  const isMountedRef = useRef(true);
  const [loading, setLoading] = useState(false);
  const [logs, setLogs] = useState<LoginLog[]>([]);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(10);

  useEffect(() => {
    isMountedRef.current = true;
    return () => {
      isMountedRef.current = false;
    };
  }, []);

  /** 获取登录日志 */
  const fetchLogs = useCallback(async (p: number, s: number) => {
    setLoading(true);
    try {
      const result = await getLoginLogs(p, s);
      if (isMountedRef.current) {
        setLogs(result.list);
        setTotal(result.total);
      }
    } catch (error) {
      console.error('获取登录日志失败:', error);
    } finally {
      if (isMountedRef.current) {
        setLoading(false);
      }
    }
  }, []);

  useEffect(() => {
    if (visible) {
      fetchLogs(page, pageSize);
    }
  }, [visible, page, pageSize, fetchLogs]);

  /** 翻页 */
  const handlePageChange = useCallback((p: number, s: number) => {
    setPage(p);
    setPageSize(s);
  }, []);

  /** 表格列定义（缓存） */
  const columns: ColumnsType<LoginLog> = useMemo(() => [
    {
      title: 'IP地址',
      dataIndex: 'loginIp',
      key: 'loginIp',
      width: 150,
    },
    {
      title: '物理地址',
      dataIndex: 'loginLocation',
      key: 'loginLocation',
      width: 100,
    },
    {
      title: '登录设备',
      dataIndex: 'loginDevice',
      key: 'loginDevice',
      width: 200,
      render: (device: string) => formatDevice(device),
    },
    {
      title: '登录时间',
      dataIndex: 'loginTime',
      key: 'loginTime',
      width: 160,
    },
    {
      title: '状态',
      dataIndex: 'loginStatus',
      key: 'loginStatus',
      width: 100,
      render: (status: number, record: LoginLog) => (
        <Tag
          icon={status === 1 ? <CheckCircleOutlined /> : <CloseCircleOutlined />}
          color={status === 1 ? 'success' : 'error'}
        >
          {status === 1 ? '成功' : record.failReason || '失败'}
        </Tag>
      ),
    },
  ], []);

  return (
    <Modal
      title="登录日志"
      open={visible}
      onCancel={onClose}
      footer={null}
      width={800}
      destroyOnClose
    >
      <Spin spinning={loading}>
        <Table
          dataSource={logs}
          columns={columns}
          rowKey="id"
          pagination={{
            current: page,
            pageSize: pageSize,
            total: total,
            onChange: handlePageChange,
            showTotal: (t) => `共 ${t} 条记录`,
            size: 'small',
          }}
          size="small"
        />
      </Spin>
    </Modal>
  );
};

export default LoginLogModal;
