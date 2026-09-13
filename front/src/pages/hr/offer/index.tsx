/**
 * HR Offer 管理（/hr/offer）
 * Offer 列表 + HC 概览 + 筛选；发起弹窗（isOfferBlocked 阻断重复发起，支持人才库预填）；
 * 状态驱动操作：SENT 可催促/撤回，撤回/过期后可重新发起。
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useLocation, useNavigate } from 'umi';
import {
  Table,
  Button,
  Space,
  message,
  Modal,
  Form,
  Input,
  Select,
  DatePicker,
  Card,
  Row,
  Col,
  InputNumber,
  Drawer,
  Descriptions,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  PlusOutlined,
  EyeOutlined,
  BellOutlined,
  UndoOutlined,
  UserOutlined,
  ExclamationCircleOutlined,
  DollarOutlined,
  ReloadOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import StatusTag from '@/components/StatusTag';
import {
  getOfferList,
  getHcOverview,
  createOffer,
  urgeOffer,
  retractOffer,
  getHrJobList,
  getCandidateList,
} from '@/services/hr';
import type { OfferVO, HcOverviewVO } from '@/constants/apiTypes';
import { ApplicationStatus, OfferStatus, isOfferBlocked } from '@/constants/enums';
import styles from './index.less';

const statusLabelMap: Record<OfferStatus, string> = {
  [OfferStatus.SENT]: '已发送',
  [OfferStatus.ACCEPTED]: '已接受',
  [OfferStatus.REJECTED]: '已拒绝',
  [OfferStatus.EXPIRED]: '已过期',
  [OfferStatus.WITHDRAWN]: '已撤回',
};

const statusTagTypeMap: Record<OfferStatus, 'info' | 'success' | 'warning' | 'danger' | 'neutral'> = {
  [OfferStatus.SENT]: 'info',
  [OfferStatus.ACCEPTED]: 'success',
  [OfferStatus.REJECTED]: 'danger',
  [OfferStatus.EXPIRED]: 'neutral',
  [OfferStatus.WITHDRAWN]: 'warning',
};

const statusFilterOptions = Object.values(OfferStatus).map((s) => ({
  value: s,
  label: statusLabelMap[s],
}));

const dateFilterOptions = [
  { value: 'TODAY', label: '今天' },
  { value: 'WEEK', label: '近7天' },
  { value: 'MONTH', label: '近30天' },
];

/** 拆 ISO 时间为 MM-DD HH:mm */
const formatDateTime = (t?: string | null) => (t ? t.replace('T', ' ').slice(0, 16) : '-');

/** 薪资展示：35000 → ¥35,000 */
const formatSalary = (salary: number) => `¥${salary.toLocaleString()}`;

const OfferPage: React.FC = () => {
  const [offers, setOffers] = useState<OfferVO[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  // 筛选（服务端分页）
  const [statusFilter, setStatusFilter] = useState<OfferStatus | undefined>();
  const [jobFilter, setJobFilter] = useState<number | undefined>();
  const [dateFilter, setDateFilter] = useState<'TODAY' | 'WEEK' | 'MONTH' | undefined>();
  // HC 概览（全公司聚合，不传 jobId）
  const [hc, setHc] = useState<HcOverviewVO | null>(null);
  // 岗位下拉（与候选人/Offer jobId 同源）
  const [jobOptions, setJobOptions] = useState<{ value: number; label: string }[]>([]);
  // 发起 Offer 弹窗
  const [createModal, setCreateModal] = useState(false);
  const [createSubmitting, setCreateSubmitting] = useState(false);
  const [form] = Form.useForm();
  const [candidateOptions, setCandidateOptions] = useState<
    { value: string; label: string; disabled?: boolean }[]
  >([]);
  const [candidateLoading, setCandidateLoading] = useState(false);
  /** 人才库跳转预填（applicationId + jobId + candidateName） */
  const [preset, setPreset] = useState<{
    applicationId: string;
    jobId: number;
    candidateName: string;
  } | null>(null);
  // 详情 Drawer
  const [detail, setDetail] = useState<OfferVO | null>(null);

  const location = useLocation();
  const navigate = useNavigate();

  // 岗位下拉
  useEffect(() => {
    getHrJobList({ page: 1, size: 50 })
      .then((data) =>
        setJobOptions((data.list ?? []).map((j) => ({ value: j.jobId, label: j.title }))),
      )
      .catch(() => setJobOptions([]));
  }, []);

  // 列表（服务端分页）
  const fetchList = useCallback(async () => {
    setListLoading(true);
    try {
      const data = await getOfferList({
        status: statusFilter,
        jobId: jobFilter,
        dateRange: dateFilter,
        page,
        size: pageSize,
      });
      setOffers(data.list);
      setTotal(data.total);
    } catch {
      // 拦截器已统一提示，保留原列表
    } finally {
      setListLoading(false);
    }
  }, [statusFilter, jobFilter, dateFilter, page, pageSize]);

  // HC 概览（全公司聚合）
  const fetchHc = useCallback(async () => {
    try {
      setHc(await getHcOverview());
    } catch {
      setHc(null);
    }
  }, []);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  useEffect(() => {
    fetchHc();
  }, [fetchHc]);

  // 人才库跳转：带 applicationId/jobId/candidateName → 自动打开发起弹窗并预填
  useEffect(() => {
    const params = new URLSearchParams(location.search);
    const applicationId = params.get('applicationId');
    const jobId = params.get('jobId');
    const candidateName = params.get('candidateName');
    if (applicationId && jobId && candidateName) {
      const p = {
        applicationId, // 雪花 ID 保持字符串，不可 Number()（会精度丢失）
        jobId: Number(jobId),
        candidateName,
      };
      setPreset(p);
      form.resetFields();
      form.setFieldsValue({ jobId: p.jobId, applicationId: p.applicationId });
      setCandidateOptions([{ value: p.applicationId, label: p.candidateName }]);
      setCreateModal(true);
      navigate('/hr/offer', { replace: true }); // 清掉 query，避免刷新重复弹窗
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [location.search]);

  /** 打开发起弹窗（普通入口，无预填） */
  const openCreateModal = () => {
    setPreset(null);
    form.resetFields();
    setCandidateOptions([]);
    setCreateModal(true);
  };

  /** 岗位变化 → 拉取该岗位 OFFERABLE 候选人 */
  const loadCandidates = async (jobId: number) => {
    setCandidateLoading(true);
    try {
      const data = await getCandidateList({
        status: ApplicationStatus.OFFERABLE,
        jobId,
        page: 1,
        size: 100,
      });
      setCandidateOptions(
        (data.list ?? []).map((c) => {
          const blocked = isOfferBlocked(c.lastOfferStatus, c.hasOfferRecord);
          let label = c.candidateName;
          if (blocked) {
            // 有具体 Offer 状态则标注具体状态（已接受/已拒绝/已发送），否则回退旧文案
            label = c.lastOfferStatus
              ? `${c.candidateName}（${statusLabelMap[c.lastOfferStatus]}）`
              : `${c.candidateName}（已发过Offer）`;
          }
          return {
            value: c.id, // = applicationId
            label,
            disabled: blocked,
          };
        }),
      );
    } catch {
      setCandidateOptions([]);
      message.error('候选人加载失败');
    } finally {
      setCandidateLoading(false);
    }
  };

  const handleJobChange = (jobId: number | undefined) => {
    form.setFieldsValue({ applicationId: undefined });
    if (jobId) {
      loadCandidates(jobId);
    } else {
      setCandidateOptions([]);
    }
  };

  const handleCreateSubmit = async () => {
    const values = await form.validateFields(); // 校验失败 → reject → Modal 保持打开
    setCreateSubmitting(true);
    try {
      const res = await createOffer({
        applicationId: values.applicationId,
        salary: values.salary,
        entryDate: (values.entryDate as dayjs.Dayjs).format('YYYY-MM-DD'),
        level: values.level || undefined,
        remark: values.remark || undefined,
        expiresInDays: values.expiresInDays ?? 3,
      });
      if (res.salaryWarning?.warn) {
        message.warning(res.salaryWarning.message);
      }
      message.success('Offer已发送');
      setCreateModal(false);
      form.resetFields();
      fetchList();
      fetchHc();
    } catch {
      // 4205 重复发起 / 4203 HC不足 等已由拦截器提示
    } finally {
      setCreateSubmitting(false);
    }
  };

  const handleUrge = (record: OfferVO) => {
    Modal.confirm({
      title: '催促确认',
      content: `确定向 ${record.candidateName} 发送催促确认提醒吗？`,
      okText: '确认催促',
      cancelText: '取消',
      onOk: async () => {
        await urgeOffer(record.offerId); // 失败 → 拦截器提示，Modal 保持打开
        message.success('已发送催促提醒');
        fetchList();
      },
    });
  };

  const handleRetract = (record: OfferVO) => {
    Modal.confirm({
      title: '确认撤回Offer',
      content: `确定要撤回发给 ${record.candidateName} 的Offer吗？撤回后状态将变为「已撤回」，且不可再次发起。`,
      okText: '确认撤回',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        await retractOffer(record.offerId);
        message.success('Offer已撤回');
        fetchList();
        fetchHc();
      },
    });
  };

  const handleReset = () => {
    setStatusFilter(undefined);
    setJobFilter(undefined);
    setDateFilter(undefined);
    setPage(1);
  };

  const columns: ColumnsType<OfferVO> = [
    {
      title: '候选人',
      dataIndex: 'candidateName',
      key: 'candidateName',
      width: 120,
      render: (name: string) => (
        <div className={styles.candidateCell}>
          <div className={styles.candidateAvatar}>
            <UserOutlined />
          </div>
          <span className={styles.candidateName}>{name}</span>
        </div>
      ),
    },
    {
      title: '岗位',
      dataIndex: 'jobTitle',
      key: 'jobTitle',
      width: 180,
    },
    {
      title: '薪资',
      dataIndex: 'salary',
      key: 'salary',
      width: 110,
      render: (salary: number) => (
        <span className={styles.salaryText}>
          <DollarOutlined style={{ marginRight: 2 }} />
          {formatSalary(salary)}
        </span>
      ),
    },
    {
      title: '职级',
      dataIndex: 'level',
      key: 'level',
      width: 100,
      render: (level: string | null) => level || '—',
    },
    {
      title: '入职日期',
      dataIndex: 'entryDate',
      key: 'entryDate',
      width: 110,
      render: (d: string) => d || '—',
    },
    {
      title: '发送时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 130,
      render: (t: string) => <span className={styles.timeCell}>{formatDateTime(t)}</span>,
    },
    {
      title: '过期时间',
      dataIndex: 'expiresAt',
      key: 'expiresAt',
      width: 130,
      render: (t: string, record: OfferVO) => {
        const isExpired = record.status === OfferStatus.SENT && new Date(t) < new Date();
        return (
          <span className={`${styles.timeCell} ${isExpired ? styles.timeExpired : ''}`}>
            {isExpired && (
              <ExclamationCircleOutlined style={{ marginRight: 4, color: '#DC2626' }} />
            )}
            {formatDateTime(t)}
          </span>
        );
      },
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: OfferStatus, record: OfferVO) => (
        <StatusTag type={statusTagTypeMap[status]}>
          {record.statusDesc || statusLabelMap[status]}
        </StatusTag>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_: unknown, record: OfferVO) => (
        <Space size="small" wrap>
          <Button
            type="link"
            size="small"
            className={styles.actionBtn}
            onClick={() => setDetail(record)}
          >
            <EyeOutlined /> 详情
          </Button>
          {record.status === OfferStatus.SENT && (
            <>
              <Button
                type="link"
                size="small"
                className={styles.actionBtnPrimary}
                onClick={() => handleUrge(record)}
              >
                <BellOutlined /> 催促
              </Button>
              <Button type="link" size="small" danger onClick={() => handleRetract(record)}>
                <UndoOutlined /> 撤回
              </Button>
            </>
          )}
        </Space>
      ),
    },
  ];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="Offer管理"
        desc="管理所有Offer的发放、跟踪和确认"
        extra={
          <Button
            type="primary"
            className={styles.accentBtn}
            icon={<PlusOutlined />}
            onClick={openCreateModal}
          >
            发起Offer
          </Button>
        }
      />

      {/* HC Overview（全公司聚合） */}
      <Row gutter={20} className={styles.hcRow}>
        <Col xs={12} sm={6}>
          <div className={styles.hcCard}>
            <div className={styles.hcValue}>{hc ? hc.totalHc : '-'}</div>
            <div className={styles.hcLabel}>总HC</div>
          </div>
        </Col>
        <Col xs={12} sm={6}>
          <div className={`${styles.hcCard} ${styles.hcReserved}`}>
            <div className={styles.hcValue}>{hc ? hc.reservedHc : '-'}</div>
            <div className={styles.hcLabel}>已锁定</div>
          </div>
        </Col>
        <Col xs={12} sm={6}>
          <div className={`${styles.hcCard} ${styles.hcConfirmed}`}>
            <div className={styles.hcValue}>{hc ? hc.confirmedHc : '-'}</div>
            <div className={styles.hcLabel}>已确认</div>
          </div>
        </Col>
        <Col xs={12} sm={6}>
          <div className={`${styles.hcCard} ${styles.hcAvailable}`}>
            <div className={styles.hcValue}>{hc ? hc.availableHc : '-'}</div>
            <div className={styles.hcLabel}>可用</div>
          </div>
        </Col>
      </Row>

      {/* 筛选 + 列表 */}
      <Card className={styles.card} bordered={false}>
        <div className={styles.filterBar}>
          <Select
            placeholder="Offer状态"
            value={statusFilter}
            onChange={(v) => {
              setStatusFilter(v);
              setPage(1);
            }}
            className={styles.filterSelect}
            allowClear
            options={statusFilterOptions}
          />
          <Select
            placeholder="按岗位筛选"
            value={jobFilter}
            onChange={(v) => {
              setJobFilter(v);
              setPage(1);
            }}
            className={styles.filterSelect}
            allowClear
            options={jobOptions}
          />
          <Select
            placeholder="发送时间"
            value={dateFilter}
            onChange={(v) => {
              setDateFilter(v);
              setPage(1);
            }}
            className={styles.filterSelectNarrow}
            allowClear
            options={dateFilterOptions}
          />
          <Button icon={<ReloadOutlined />} onClick={handleReset} className={styles.resetBtn}>
            重置
          </Button>
        </div>

        <Table
          columns={columns}
          dataSource={offers}
          rowKey="offerId"
          loading={listLoading}
          className={styles.table}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (t) => `共 ${t} 份Offer`,
            onChange: (p, ps) => {
              if (ps !== pageSize) {
                setPageSize(ps);
                setPage(1);
              } else {
                setPage(p);
              }
            },
          }}
          scroll={{ x: 1100 }}
          locale={{ emptyText: '暂无Offer数据' }}
        />
      </Card>

      {/* 发起 Offer Modal */}
      <Modal
        title="发起Offer"
        open={createModal}
        onCancel={() => setCreateModal(false)}
        onOk={handleCreateSubmit}
        okText="发送Offer"
        cancelText="取消"
        confirmLoading={createSubmitting}
        className={styles.createModal}
        okButtonProps={{ className: styles.accentBtn }}
      >
        <Form form={form} layout="vertical" className={styles.modalForm}>
          <Form.Item
            name="jobId"
            label="岗位"
            rules={[{ required: true, message: '请选择岗位' }]}
          >
            <Select
              placeholder="选择岗位"
              options={jobOptions}
              disabled={!!preset}
              onChange={handleJobChange}
            />
          </Form.Item>
          <Form.Item
            name="applicationId"
            label="候选人"
            rules={[{ required: true, message: '请选择候选人' }]}
          >
            <Select
              placeholder="选择候选人"
              options={candidateOptions}
              loading={candidateLoading}
              disabled={!!preset}
            />
          </Form.Item>
          <Form.Item
            name="salary"
            label="薪资（月薪·元）"
            rules={[{ required: true, message: '请输入薪资' }]}
          >
            <InputNumber
              style={{ width: '100%' }}
              min={1}
              precision={0}
              addonBefore="¥"
              placeholder="如 35000"
            />
          </Form.Item>
          <Form.Item
            name="entryDate"
            label="入职日期"
            rules={[{ required: true, message: '请选择入职日期' }]}
          >
            <DatePicker
              style={{ width: '100%' }}
              disabledDate={(d) => !!d && d.isBefore(dayjs(), 'day')}
              placeholder="选择入职日期"
            />
          </Form.Item>
          <Form.Item name="level" label="职级">
            <Input placeholder="如 P6 / 高级工程师" />
          </Form.Item>
          <Form.Item name="remark" label="备注">
            <Input.TextArea rows={2} placeholder="选填" />
          </Form.Item>
          <Form.Item name="expiresInDays" label="有效期（天）" initialValue={3}>
            <InputNumber style={{ width: '100%' }} min={1} max={30} precision={0} />
          </Form.Item>
        </Form>
      </Modal>

      {/* 详情 Drawer */}
      <Drawer
        title={detail ? `Offer详情 — ${detail.candidateName}` : 'Offer详情'}
        width={480}
        open={!!detail}
        onClose={() => setDetail(null)}
      >
        {detail && (
          <Descriptions column={1} size="middle" bordered>
            <Descriptions.Item label="候选人">{detail.candidateName}</Descriptions.Item>
            <Descriptions.Item label="岗位">{detail.jobTitle}</Descriptions.Item>
            <Descriptions.Item label="薪资">{formatSalary(detail.salary)}</Descriptions.Item>
            <Descriptions.Item label="职级">{detail.level || '—'}</Descriptions.Item>
            <Descriptions.Item label="状态">
              <StatusTag type={statusTagTypeMap[detail.status]}>
                {detail.statusDesc || statusLabelMap[detail.status]}
              </StatusTag>
            </Descriptions.Item>
            <Descriptions.Item label="入职日期">{detail.entryDate || '—'}</Descriptions.Item>
            <Descriptions.Item label="发送时间">{formatDateTime(detail.createdAt)}</Descriptions.Item>
            <Descriptions.Item label="过期时间">{formatDateTime(detail.expiresAt)}</Descriptions.Item>
            <Descriptions.Item label="催促次数">{detail.urgeCount}</Descriptions.Item>
            <Descriptions.Item label="接受时间">
              {detail.acceptedAt ? formatDateTime(detail.acceptedAt) : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="拒绝时间">
              {detail.rejectedAt ? formatDateTime(detail.rejectedAt) : '—'}
            </Descriptions.Item>
            <Descriptions.Item label="拒绝原因">{detail.rejectReason || '—'}</Descriptions.Item>
          </Descriptions>
        )}
      </Drawer>
    </div>
  );
};

export default OfferPage;
