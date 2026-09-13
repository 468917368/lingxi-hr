/**
 * HR 面试协同（/hr/interview）
 * 面试列表 + 6维筛选（岗位/时间/方式/面试官/状态/关键词）；
 * 状态机驱动操作：开始面试/取消/录入评估(草稿)/查看评估，评估正式提交即 COMPLETED。
 */
import React, { useState, useCallback, useEffect } from 'react';
import {
  Table,
  Button,
  Space,
  message,
  Modal,
  Input,
  Rate,
  Spin,
  Select,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlayCircleOutlined,
  EditOutlined,
  EyeOutlined,
  CloseCircleOutlined,
  UserOutlined,
  SearchOutlined,
  ReloadOutlined,
  VideoCameraOutlined,
  EnvironmentOutlined,
  PhoneOutlined,
  TeamOutlined,
  CalendarOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import {
  getInterviewList,
  startInterview,
  cancelInterview,
  submitEvaluation,
  getInterviewEvaluation,
  getHrJobList,
} from '@/services/hr';
import { listMembers } from '@/services/company';
import type {
  InterviewVO,
  EvaluationDTO,
  InterviewMethod,
  InterviewDateRange,
} from '@/constants/apiTypes';
import { InterviewStatus } from '@/constants/enums';
import styles from './index.less';

const { TextArea } = Input;

const statusColorMap: Record<InterviewStatus, string> = {
  [InterviewStatus.PENDING]: '#D97706',
  [InterviewStatus.SCHEDULED]: '#45B7D1',
  [InterviewStatus.IN_PROGRESS]: '#45B7D1',
  [InterviewStatus.COMPLETED]: '#059669',
  [InterviewStatus.CANCELLED]: '#DC2626',
};

const methodLabelMap: Record<string, string> = {
  OFFLINE: '线下面试',
  ONLINE: '视频面试',
  PHONE: '电话面试',
};

const methodIconMap: Record<string, React.ReactNode> = {
  OFFLINE: <EnvironmentOutlined />,
  ONLINE: <VideoCameraOutlined />,
  PHONE: <PhoneOutlined />,
};

/** 拆 ISO 时间为双行：{ date: '08-07', time: '14:00' } */
const splitDateTime = (t?: string): { date: string; time: string } => {
  if (!t) return { date: '-', time: '' };
  const [date, time] = t.replace('T', ' ').split(' ');
  const [, month, day] = date.split('-');
  return { date: `${month}-${day}`, time: time?.slice(0, 5) ?? '' };
};

const statusFilterOptions = [
  { value: InterviewStatus.PENDING, label: '待安排' },
  { value: InterviewStatus.SCHEDULED, label: '已安排' },
  { value: InterviewStatus.IN_PROGRESS, label: '进行中' },
  { value: InterviewStatus.COMPLETED, label: '已完成' },
  { value: InterviewStatus.CANCELLED, label: '已取消' },
];

const methodFilterOptions = [
  { value: 'OFFLINE', label: '线下面试' },
  { value: 'ONLINE', label: '视频面试' },
  { value: 'PHONE', label: '电话面试' },
];

const dateFilterOptions = [
  { value: 'TODAY', label: '今天' },
  { value: 'WEEK', label: '近7天' },
  { value: 'MONTH', label: '近30天' },
];

const emptyEvalForm: EvaluationDTO = {
  conclusion: 'PENDING',
  techScore: 0,
  communicationScore: 0,
  matchScore: 0,
  potentialScore: 0,
  comment: '',
};

const InterviewPage: React.FC = () => {
  // 面试安排
  const [interviews, setInterviews] = useState<InterviewVO[]>([]);
  const [listLoading, setListLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);

  // Tab1 筛选条件（组合查询，服务端分页）
  const [jobFilter, setJobFilter] = useState<number | undefined>();
  const [dateFilter, setDateFilter] = useState<InterviewDateRange | undefined>();
  const [methodFilter, setMethodFilter] = useState<InterviewMethod | undefined>();
  const [interviewerFilter, setInterviewerFilter] = useState<number | undefined>();
  const [statusFilter, setStatusFilter] = useState<InterviewStatus | undefined>();
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [jobOptions, setJobOptions] = useState<{ value: number; label: string }[]>([]);
  const [interviewerOptions, setInterviewerOptions] = useState<{ value: number; label: string }[]>([]);

  // 评估弹窗
  const [evalTarget, setEvalTarget] = useState<InterviewVO | null>(null);
  const [evalMode, setEvalMode] = useState<'edit' | 'view'>('edit');
  const [evalForm, setEvalForm] = useState<EvaluationDTO>(emptyEvalForm);
  const [evalLoading, setEvalLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const fetchInterviews = useCallback(async () => {
    setListLoading(true);
    try {
      const data = await getInterviewList({
        page,
        size: pageSize,
        jobId: jobFilter,
        dateRange: dateFilter,
        method: methodFilter,
        interviewerId: interviewerFilter,
        status: statusFilter,
        keyword: debouncedKeyword || undefined,
      });
      setInterviews(data.list);
      setTotal(data.total);
    } catch {
      // 拦截器已提示 body.message
    } finally {
      setListLoading(false);
    }
  }, [page, pageSize, jobFilter, dateFilter, methodFilter, interviewerFilter, statusFilter, debouncedKeyword]);

  useEffect(() => {
    fetchInterviews();
  }, [fetchInterviews]);

  // 筛选下拉数据源（岗位 + 面试官，均本企业）
  useEffect(() => {
    getHrJobList({ page: 1, size: 50 })
      .then((data) =>
        setJobOptions((data.list ?? []).map((j) => ({ value: j.jobId, label: j.title }))),
      )
      .catch(() => setJobOptions([]));
    listMembers({ role: 'INTERVIEWER', status: 'ACTIVE' })
      .then((members) =>
        setInterviewerOptions(
          (Array.isArray(members) ? members : []).map((m) => ({ value: m.userId, label: m.name })),
        ),
      )
      .catch(() => setInterviewerOptions([]));
  }, []);

  // 候选人姓名关键词防抖（300ms）
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  /** 重置全部筛选并回到第 1 页 */
  const handleResetFilter = () => {
    setJobFilter(undefined);
    setDateFilter(undefined);
    setMethodFilter(undefined);
    setInterviewerFilter(undefined);
    setStatusFilter(undefined);
    setKeyword('');
    setDebouncedKeyword('');
    setPage(1);
  };

  // 当前激活的筛选条件（顶部概览条回显）
  const activeFilters: { key: string; label: string }[] = [];
  if (jobFilter) {
    const j = jobOptions.find((o) => o.value === jobFilter);
    if (j) activeFilters.push({ key: 'job', label: j.label });
  }
  if (dateFilter) {
    const d = dateFilterOptions.find((o) => o.value === dateFilter);
    if (d) activeFilters.push({ key: 'date', label: d.label });
  }
  if (methodFilter) {
    const m = methodFilterOptions.find((o) => o.value === methodFilter);
    if (m) activeFilters.push({ key: 'method', label: m.label });
  }
  if (interviewerFilter) {
    const iv = interviewerOptions.find((o) => o.value === interviewerFilter);
    if (iv) activeFilters.push({ key: 'interviewer', label: iv.label });
  }
  if (statusFilter) {
    const st = statusFilterOptions.find((o) => o.value === statusFilter);
    if (st) activeFilters.push({ key: 'status', label: st.label });
  }
  if (debouncedKeyword) {
    activeFilters.push({ key: 'keyword', label: `“${debouncedKeyword}”` });
  }

  const handleStart = (record: InterviewVO) => {
    Modal.confirm({
      title: '开始面试',
      content: `确认开始「${record.candidateName}」的面试？`,
      okText: '开始面试',
      cancelText: '取消',
      onOk: async () => {
        await startInterview(record.interviewId);
        message.success('面试已开始');
        fetchInterviews();
      },
    });
  };

  const handleCancel = (record: InterviewVO) => {
    Modal.confirm({
      title: '取消面试',
      content: `确定要取消 ${record.candidateName} 的面试吗？投递状态将回退为已筛选。`,
      okText: '确认取消',
      cancelText: '返回',
      okButtonProps: { danger: true },
      onOk: async () => {
        await cancelInterview(record.interviewId);
        message.success('面试已取消');
        fetchInterviews();
      },
    });
  };

  /** 打开评估弹窗：目标面试 + 模式（edit=录入/编辑草稿，view=只读） */
  const openEval = async (target: InterviewVO, mode: 'edit' | 'view') => {
    setEvalTarget(target);
    setEvalMode(mode);
    setEvalForm({ ...emptyEvalForm });
    setEvalLoading(true);
    try {
      const detail = await getInterviewEvaluation(target.interviewId);
      setEvalForm({
        conclusion: detail.conclusion,
        techScore: detail.techScore,
        communicationScore: detail.communicationScore,
        matchScore: detail.matchScore,
        potentialScore: detail.potentialScore,
        comment: detail.comment,
      });
    } catch {
      // 无草稿/无评估 → 空表单；查看模式由入口状态保证有记录
    } finally {
      setEvalLoading(false);
    }
  };

  const handleSubmitEval = async (isDraft: boolean) => {
    if (!evalTarget) return;
    if (!isDraft) {
      if (evalForm.techScore < 1 || evalForm.communicationScore < 1 ||
          evalForm.matchScore < 1 || evalForm.potentialScore < 1) {
        message.warning('请完成四项维度评分');
        return;
      }
      if (evalForm.comment.trim().length < 20) {
        message.warning('评语至少 20 字');
        return;
      }
    }
    setSubmitting(true);
    try {
      const res = await submitEvaluation(evalTarget.interviewId, { ...evalForm, isDraft });
      if (isDraft) {
        message.success('草稿已保存');
      } else {
        message.success(res.applicationStatus === 'REJECTED' ? '评估已提交，已通知候选人' : '评估已提交');
        if (!res.notificationSent) message.warning('候选人通知发送失败');
      }
      setEvalTarget(null);
      fetchInterviews();
    } catch {
      // 4101/4103/4104 已由拦截器提示
    } finally {
      setSubmitting(false);
    }
  };

  const arrangeColumns: ColumnsType<InterviewVO> = [
    {
      title: '候选人',
      dataIndex: 'candidateName',
      key: 'candidateName',
      width: 190,
      render: (name: string, record) => (
        <div className={styles.candidateCell}>
          <div className={styles.candidateAvatar}>
            {record.candidateAvatar ? (
              <img src={record.candidateAvatar} alt={name} />
            ) : (
              <UserOutlined />
            )}
          </div>
          <div className={styles.candidateInfo}>
            <div className={styles.candidateName}>{name}</div>
            <div className={styles.candidateJob}>{record.jobTitle}</div>
          </div>
        </div>
      ),
    },
    {
      title: '面试时间',
      dataIndex: 'scheduledAt',
      key: 'scheduledAt',
      width: 170,
      render: (t: string) => {
        const { date, time } = splitDateTime(t);
        return (
          <div className={styles.timeCell}>
            <div className={styles.timeDate}>
              <CalendarOutlined /> {date}
            </div>
            <div className={styles.timeClock}>{time}</div>
          </div>
        );
      },
    },
    {
      title: '面试方式',
      dataIndex: 'method',
      key: 'method',
      width: 110,
      render: (m: string) => (
        <span className={styles.methodCell}>
          <span className={styles.methodIcon}>{methodIconMap[m]}</span>
          {methodLabelMap[m] || m}
        </span>
      ),
    },
    {
      title: '面试官',
      dataIndex: 'interviewerName',
      key: 'interviewerName',
      width: 130,
      render: (name: string) => (
        <span className={styles.interviewerCell}>
          <span className={styles.interviewerAvatar}>
            <TeamOutlined />
          </span>
          {name}
        </span>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 110,
      render: (status: InterviewStatus, record) => {
        const color = statusColorMap[status];
        return (
          <span className={styles.badge} style={{ color, background: `${color}14` }}>
            <span className={styles.badgeDot} style={{ background: color }} />
            {record.statusDesc || status}
          </span>
        );
      },
    },
    {
      title: '操作',
      key: 'actions',
      width: 200,
      fixed: 'right',
      render: (_: unknown, record) => {
        const s = record.status;
        return (
          <Space size="small" wrap>
            {s === InterviewStatus.SCHEDULED && (
              <Button type="link" size="small" className={styles.actionBtnPrimary}
                onClick={() => handleStart(record)}>
                <PlayCircleOutlined /> 开始面试
              </Button>
            )}
            {s === InterviewStatus.IN_PROGRESS && (
              <Button type="link" size="small" className={styles.actionBtnPrimary}
                onClick={() => openEval(record, 'edit')}>
                <EditOutlined /> 录入评估
              </Button>
            )}
            {s === InterviewStatus.COMPLETED && (
              <Button type="link" size="small" className={styles.actionBtn}
                onClick={() => openEval(record, 'view')}>
                <EyeOutlined /> 查看评估
              </Button>
            )}
            {(s === InterviewStatus.PENDING || s === InterviewStatus.SCHEDULED) && (
              <Button type="link" size="small" danger
                onClick={() => handleCancel(record)}>
                <CloseCircleOutlined /> 取消
              </Button>
            )}
          </Space>
        );
      },
    },
  ];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="面试协同"
        desc="管理面试安排、跟踪面试进度并完成面试评估"
      />

      <div className={styles.card} style={{ border: '1px solid var(--border)', borderRadius: 'var(--radius-lg)' }}>
        <div className={styles.summaryBar}>
          <div className={styles.summaryLeft}>
            <span className={styles.summaryNum}>{total}</span>
            <span className={styles.summaryUnit}>场面试</span>
            <span className={styles.summaryDesc}>按当前条件筛选</span>
          </div>
          <div className={styles.summaryRight}>
            {activeFilters.map((f) => (
              <span key={f.key} className={styles.summaryChip}>{f.label}</span>
            ))}
            {activeFilters.length === 0 && (
              <span className={styles.summaryEmpty}>未设置筛选 · 显示全部面试</span>
            )}
          </div>
        </div>
        <div className={styles.filterBar}>
          <Select
                placeholder="岗位"
                value={jobFilter}
                onChange={(v) => { setJobFilter(v); setPage(1); }}
                options={jobOptions}
                allowClear
                className={styles.filterSelect}
              />
              <Select
                placeholder="面试时间"
                value={dateFilter}
                onChange={(v) => { setDateFilter(v); setPage(1); }}
                options={dateFilterOptions}
                allowClear
                className={styles.filterSelect}
              />
              <Select
                placeholder="面试方式"
                value={methodFilter}
                onChange={(v) => { setMethodFilter(v); setPage(1); }}
                options={methodFilterOptions}
                allowClear
                className={styles.filterSelect}
              />
              <Select
                placeholder="面试官"
                value={interviewerFilter}
                onChange={(v) => { setInterviewerFilter(v); setPage(1); }}
                options={interviewerOptions}
                allowClear
                className={styles.filterSelect}
              />
              <Select
                placeholder="状态"
                value={statusFilter}
                onChange={(v) => { setStatusFilter(v); setPage(1); }}
                options={statusFilterOptions}
                allowClear
                className={styles.filterSelect}
              />
              <Input
                placeholder="搜索候选人姓名..."
                prefix={<SearchOutlined />}
                value={keyword}
                onChange={(e) => setKeyword(e.target.value)}
                allowClear
                className={styles.filterInput}
              />
              <Button
                icon={<ReloadOutlined />}
                onClick={handleResetFilter}
                className={styles.resetBtn}
              >
                重置
              </Button>
            </div>
            <Table
              columns={arrangeColumns}
              dataSource={interviews}
              rowKey="interviewId"
              loading={listLoading}
              className={styles.table}
              pagination={{
                current: page,
                pageSize,
                total,
                showSizeChanger: true,
                showTotal: (t) => `共 ${t} 场面试`,
                onChange: (p, ps) => {
                  if (ps !== pageSize) { setPageSize(ps); setPage(1); } else { setPage(p); }
                },
              }}
              scroll={{ x: 1000 }}
              locale={{ emptyText: '暂无面试数据' }}
            />
      </div>

      {/* 评估弹窗 */}
      <Modal
        title={evalMode === 'view' ? '查看评估' : '录入面试评估'}
        open={!!evalTarget}
        onCancel={() => setEvalTarget(null)}
        className={styles.evaluateModal}
        footer={
          evalMode === 'view' ? (
            [<Button key="close" onClick={() => setEvalTarget(null)}>关闭</Button>]
          ) : (
            [
              <Button key="draft" loading={submitting} onClick={() => handleSubmitEval(true)}>保存草稿</Button>,
              <Button key="submit" type="primary" loading={submitting}
                className={styles.accentBtn} onClick={() => handleSubmitEval(false)}>提交评估</Button>,
            ]
          )
        }
      >
        {evalLoading ? (
          <div style={{ textAlign: 'center', padding: 24 }}><Spin /></div>
        ) : (
          evalTarget && (
            <div className={styles.evaluateForm}>
              <div className={styles.evaluateInfo}>
                <div className={styles.evalInfoAvatar}>
                  {evalTarget.candidateAvatar ? (
                    <img src={evalTarget.candidateAvatar} alt="" />
                  ) : (
                    <UserOutlined />
                  )}
                </div>
                <div className={styles.evalInfoMain}>
                  <div className={styles.evalInfoName}>{evalTarget.candidateName}</div>
                  <div className={styles.evalInfoMeta}>{evalTarget.jobTitle}</div>
                </div>
                <div className={styles.evalInfoSide}>
                  <div className={styles.evalInfoTime}>
                    <CalendarOutlined /> {splitDateTime(evalTarget.scheduledAt).date} {splitDateTime(evalTarget.scheduledAt).time}
                  </div>
                  <div className={styles.evalInfoMethod}>
                    <span className={styles.methodIcon}>{methodIconMap[evalTarget.method]}</span>
                    {methodLabelMap[evalTarget.method] || evalTarget.method}
                  </div>
                </div>
              </div>
              <div className={styles.evaluateField}>
                <div className={styles.evaluateLabel}>面试结论</div>
                <div className={styles.conclusionGroup}>
                  {([
                    { value: 'PASS', label: '通过', cls: 'pass' },
                    { value: 'PENDING', label: '待定', cls: 'pending' },
                    { value: 'REJECT', label: '淘汰', cls: 'reject' },
                  ] as const).map((opt) => (
                    <button
                      key={opt.value}
                      type="button"
                      disabled={evalMode === 'view'}
                      className={`${styles.conclusionBtn} ${styles[`conclusion_${opt.cls}`]} ${
                        evalForm.conclusion === opt.value ? styles.conclusionActive : ''
                      }`}
                      onClick={() => setEvalForm({ ...evalForm, conclusion: opt.value })}
                    >
                      {opt.label}
                    </button>
                  ))}
                </div>
              </div>
              <div className={styles.evaluateField}>
                <div className={styles.evaluateLabel}>四维评分</div>
                <div className={styles.scoreRow}>
                  <span className={styles.scoreLabel}>技术能力</span>
                  <Rate value={evalForm.techScore} disabled={evalMode === 'view'} count={5}
                    onChange={(v) => setEvalForm({ ...evalForm, techScore: v })} />
                  <span className={styles.scoreValue}>{evalForm.techScore || '—'}/5</span>
                </div>
                <div className={styles.scoreRow}>
                  <span className={styles.scoreLabel}>沟通表达</span>
                  <Rate value={evalForm.communicationScore} disabled={evalMode === 'view'} count={5}
                    onChange={(v) => setEvalForm({ ...evalForm, communicationScore: v })} />
                  <span className={styles.scoreValue}>{evalForm.communicationScore || '—'}/5</span>
                </div>
                <div className={styles.scoreRow}>
                  <span className={styles.scoreLabel}>岗位匹配</span>
                  <Rate value={evalForm.matchScore} disabled={evalMode === 'view'} count={5}
                    onChange={(v) => setEvalForm({ ...evalForm, matchScore: v })} />
                  <span className={styles.scoreValue}>{evalForm.matchScore || '—'}/5</span>
                </div>
                <div className={styles.scoreRow}>
                  <span className={styles.scoreLabel}>发展潜力</span>
                  <Rate value={evalForm.potentialScore} disabled={evalMode === 'view'} count={5}
                    onChange={(v) => setEvalForm({ ...evalForm, potentialScore: v })} />
                  <span className={styles.scoreValue}>{evalForm.potentialScore || '—'}/5</span>
                </div>
              </div>
              <div className={styles.evaluateField}>
                <div className={styles.evaluateLabel}>面试评语</div>
                <TextArea
                  rows={4}
                  value={evalForm.comment}
                  onChange={(e) => setEvalForm({ ...evalForm, comment: e.target.value })}
                  disabled={evalMode === 'view'}
                  placeholder="请详细描述面试表现（正式提交需 ≥20 字）"
                />
              </div>
            </div>
          )
        )}
      </Modal>
    </div>
  );
};

export default InterviewPage;
