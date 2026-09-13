import React, { useState, useEffect, useCallback } from 'react';
import { Table, Button, Space, Modal, Input, Select, Form, message, Spin } from 'antd';
import { PlusOutlined, EditOutlined, SendOutlined, RollbackOutlined } from '@ant-design/icons';
import type { ColumnsType } from 'antd/es/table';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import {
  getAnnouncementList,
  createAnnouncement,
  updateAnnouncement,
  publishAnnouncement,
  withdrawAnnouncement,
} from '@/services/admin';
import styles from './index.less';

interface AnnouncementRecord {
  id: number;
  title: string;
  targetRole: string;
  status: string;
  createdAt: string;
  content: string;
}

const targetRoleLabels: Record<string, string> = {
  ALL: '全部用户',
  CANDIDATE: '求职者',
  HR: 'HR用户',
  INTERVIEWER: '面试官',
};

const statusLabels: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '已发布',
  WITHDRAWN: '已撤回',
  ARCHIVED: '已归档',
};

const getStatusType = (status: string) => {
  if (status === 'PUBLISHED') return 'success' as const;
  if (status === 'DRAFT') return 'neutral' as const;
  if (status === 'WITHDRAWN') return 'warning' as const;
  if (status === 'ARCHIVED') return 'neutral' as const;
  return 'neutral' as const;
};

const AnnouncementPage: React.FC = () => {
  const [dataSource, setDataSource] = useState<AnnouncementRecord[]>([]);
  const [loading, setLoading] = useState(false);
  const [modalOpen, setModalOpen] = useState(false);
  const [editingRecord, setEditingRecord] = useState<AnnouncementRecord | null>(null);
  const [form] = Form.useForm();

  const fetchAnnouncements = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getAnnouncementList({ page: 1, size: 50 });
      const responseData = data as unknown as { list: AnnouncementRecord[]; total: number };
      setDataSource(responseData.list || []);
    } catch {
      message.error('获取公告列表失败');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    fetchAnnouncements();
  }, [fetchAnnouncements]);

  // 新建
  const handleCreate = () => {
    setEditingRecord(null);
    form.resetFields();
    setModalOpen(true);
  };

  // 编辑
  const handleEdit = (record: AnnouncementRecord) => {
    setEditingRecord(record);
    form.setFieldsValue({
      title: record.title,
      targetRole: record.targetRole,
      content: record.content,
    });
    setModalOpen(true);
  };

  // 提交（创建/编辑）
  const handleSubmit = () => {
    form.validateFields().then(async (values) => {
      try {
        const apiData: Record<string, unknown> = {
          title: values.title,
          targetRole: values.targetRole,
          content: values.content,
        };
        if (!editingRecord) {
          apiData.status = 'PUBLISHED'; // 新建默认发布
        }
        if (editingRecord) {
          apiData.status = 'DRAFT'; // 编辑后重置为草稿
          await updateAnnouncement(editingRecord.id, apiData);
          message.success('公告已更新');
        } else {
          await createAnnouncement(apiData);
          message.success('公告已创建');
        }
        setModalOpen(false);
        fetchAnnouncements();
      } catch {
        message.error(editingRecord ? '更新公告失败' : '创建公告失败');
      }
    });
  };

  // 发布（草稿→发布）
  const handlePublish = (record: AnnouncementRecord) => {
    Modal.confirm({
      title: '确认发布',
      content: `确认发布公告「${record.title}」？`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: async () => {
        try {
          await publishAnnouncement(record.id);
          message.success('公告已发布');
          fetchAnnouncements();
        } catch {
          message.error('发布公告失败');
        }
      },
    });
  };

  // 撤回
  const handleWithdraw = (record: AnnouncementRecord) => {
    Modal.confirm({
      title: '确认撤回',
      content: `确认撤回公告「${record.title}」？`,
      okText: '确认撤回',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        try {
          await withdrawAnnouncement(record.id);
          message.success('公告已撤回');
          fetchAnnouncements();
        } catch {
          message.error('撤回公告失败');
        }
      },
    });
  };

  const columns: ColumnsType<AnnouncementRecord> = [
    { title: '标题', dataIndex: 'title', key: 'title', width: 200, ellipsis: true },
    {
      title: '目标用户',
      dataIndex: 'targetRole',
      key: 'targetRole',
      width: 100,
      render: (v: string) => targetRoleLabels[v] || v,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: string) => <StatusTag type={getStatusType(status)}>{statusLabels[status] || status}</StatusTag>,
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 160,
      render: (t: string) => (t ? t.replace('T', ' ').slice(0, 16) : '-'),
    },
    {
      title: '操作',
      key: 'action',
      width: 220,
      render: (_, record) => (
        <Space size={8}>
          {record.status !== 'PUBLISHED' && (
            <Button type="link" size="small" icon={<EditOutlined />} onClick={() => handleEdit(record)}>
              编辑
            </Button>
          )}
          {record.status === 'DRAFT' && (
            <Button type="link" size="small" icon={<SendOutlined />} style={{ color: '#059669' }} onClick={() => handlePublish(record)}>
              发布
            </Button>
          )}
          {record.status === 'PUBLISHED' && (
            <Button type="link" size="small" icon={<RollbackOutlined />} danger onClick={() => handleWithdraw(record)}>
              撤回
            </Button>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className={styles.page}>
      <PageHeader
        title="系统公告"
        description="管理系统公告和通知推送"
        extra={
          <Button type="primary" icon={<PlusOutlined />} onClick={handleCreate}>
            发布公告
          </Button>
        }
      />

      <div className={styles.table}>
        <Spin spinning={loading}>
          <Table
            columns={columns}
            dataSource={dataSource}
            rowKey="id"
            pagination={{ pageSize: 10, showSizeChanger: false }}
          />
        </Spin>
      </div>

      <Modal
        title={editingRecord ? '编辑公告' : '发布公告'}
        open={modalOpen}
        onOk={handleSubmit}
        onCancel={() => setModalOpen(false)}
        okText={editingRecord ? '保存' : '创建并发布'}
        cancelText="取消"
        width={640}
      >
        <Form form={form} layout="vertical" className={styles.modalForm}>
          <Form.Item
            name="title"
            label="公告标题"
            rules={[{ required: true, message: '请输入公告标题' }]}
          >
            <Input placeholder="请输入公告标题" maxLength={128} />
          </Form.Item>
          <Form.Item
            name="targetRole"
            label="目标用户"
            rules={[{ required: true, message: '请选择目标用户' }]}
          >
            <Select
              placeholder="请选择目标用户"
              options={[
                { label: '全部用户', value: 'ALL' },
                { label: 'HR用户', value: 'HR' },
                { label: '面试官', value: 'INTERVIEWER' },
                { label: '求职者', value: 'CANDIDATE' },
              ]}
            />
          </Form.Item>
          <Form.Item
            name="content"
            label="公告内容"
            rules={[{ required: true, message: '请输入公告内容' }]}
          >
            <Input.TextArea rows={6} placeholder="请输入公告内容" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default AnnouncementPage;
