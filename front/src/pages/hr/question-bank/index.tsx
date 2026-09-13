import React, { useState, useCallback, useEffect, useRef } from 'react';
import {
  Table,
  Button,
  Space,
  Input,
  Select,
  AutoComplete,
  Modal,
  Tag,
  Tooltip,
  Empty,
  Radio,
  message,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  PlusOutlined,
  ReloadOutlined,
  EyeOutlined,
  EditOutlined,
  DeleteOutlined,
  StopOutlined,
  PlayCircleOutlined,
  AuditOutlined,
} from '@ant-design/icons';
import PageHeader from '@/components/PageHeader';
import StatusTag from '@/components/StatusTag';
import {
  getQuestionList,
  getQuestionDetail,
  deleteQuestion,
  changeQuestionStatus,
  reviewQuestion,
} from '@/services/questionBank';
import type {
  HrQuestionListVO,
  HrQuestionDetailVO,
  QuestionStatusAction,
} from '@/services/questionBank';
import {
  QuestionStatus,
  QuestionType,
  QuestionDifficulty,
  QuestionSource,
} from '@/constants/enums';
import { JOB_TYPE_OPTIONS, renderJobTypeTag } from '@/constants/questionBank';
import { getErrorCode } from '@/utils/apiError';
import QuestionDetailDrawer from './components/QuestionDetailDrawer';
import QuestionFormModal from './components/QuestionFormModal';
import styles from './index.less';

const DEFAULT_PAGE_SIZE = 10;

/** 题型 → 中文 */
const TYPE_LABEL: Record<QuestionType, string> = {
  [QuestionType.BASIC]: '基础验证',
  [QuestionType.PROJECT]: '项目深挖',
  [QuestionType.BOUNDARY]: '能力边界',
  [QuestionType.COMPREHENSIVE]: '综合素养',
};

/** 题型 → Tag 颜色 */
const TYPE_COLOR: Record<QuestionType, string> = {
  [QuestionType.BASIC]: 'blue',
  [QuestionType.PROJECT]: 'geekblue',
  [QuestionType.BOUNDARY]: 'purple',
  [QuestionType.COMPREHENSIVE]: 'cyan',
};

/** 难度 → 中文 */
const DIFF_LABEL: Record<QuestionDifficulty, string> = {
  [QuestionDifficulty.EASY]: '简单',
  [QuestionDifficulty.MEDIUM]: '中等',
  [QuestionDifficulty.HARD]: '困难',
};

/** 难度 → Tag 颜色 */
const DIFF_COLOR: Record<QuestionDifficulty, string> = {
  [QuestionDifficulty.EASY]: 'green',
  [QuestionDifficulty.MEDIUM]: 'orange',
  [QuestionDifficulty.HARD]: 'red',
};

/** 状态 → 中文 */
const STATUS_LABEL: Record<QuestionStatus, string> = {
  [QuestionStatus.PENDING_REVIEW]: '待审核',
  [QuestionStatus.ACTIVE]: '已启用',
  [QuestionStatus.INACTIVE]: '已停用',
  [QuestionStatus.REJECTED]: '已拒绝',
};

/** 状态 → StatusTag type */
const STATUS_TAG_TYPE: Record<QuestionStatus, 'success' | 'warning' | 'danger' | 'neutral'> = {
  [QuestionStatus.PENDING_REVIEW]: 'warning',
  [QuestionStatus.ACTIVE]: 'success',
  [QuestionStatus.INACTIVE]: 'neutral',
  [QuestionStatus.REJECTED]: 'danger',
};

/** 来源 → 中文 */
const SOURCE_LABEL: Record<QuestionSource, string> = {
  [QuestionSource.HR_CREATED]: 'HR创建',
  [QuestionSource.AI_GENERATED]: 'AI生成',
};

/** 格式化 ISO 时间 → 展示 */
const fmtTime = (iso?: string) => {
  if (!iso) return '-';
  return iso.replace('T', ' ').substring(0, 16);
};

const questionTypeOptions = Object.values(QuestionType).map((v) => ({
  value: v,
  label: TYPE_LABEL[v],
}));
const difficultyOptions = Object.values(QuestionDifficulty).map((v) => ({
  value: v,
  label: DIFF_LABEL[v],
}));
const statusOptions = Object.values(QuestionStatus).map((v) => ({
  value: v,
  label: STATUS_LABEL[v],
}));

const QuestionBankPage: React.FC = () => {
  // ===== 筛选状态（本地 state，不写 URL；筛选变更重置回第 1 页） =====
  const [jobType, setJobType] = useState('');
  const [questionType, setQuestionType] = useState<QuestionType | undefined>(undefined);
  const [difficulty, setDifficulty] = useState<QuestionDifficulty | undefined>(undefined);
  const [status, setStatus] = useState<QuestionStatus | undefined>(undefined);
  const [page, setPage] = useState(1);

  // ===== 列表数据 =====
  const [list, setList] = useState<HrQuestionListVO[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState<number | null>(null);

  // 列表请求序号：筛选连续变化会并发调用 fetchList，旧响应晚返回时据此丢弃，仅最新请求更新状态
  const fetchSeqRef = useRef(0);

  // ===== 详情抽屉 / 新增编辑弹窗 / 审核弹窗 =====
  const [detailTarget, setDetailTarget] = useState<HrQuestionListVO | null>(null);
  const [formOpen, setFormOpen] = useState(false);
  const [formRecord, setFormRecord] = useState<HrQuestionDetailVO | null>(null);
  const [reviewTarget, setReviewTarget] = useState<HrQuestionListVO | null>(null);
  const [reviewAction, setReviewAction] = useState<'APPROVE' | 'REJECT'>('APPROVE');
  const [reviewReason, setReviewReason] = useState('');
  const [reviewLoading, setReviewLoading] = useState(false);

  // ===== 数据加载（筛选条件变化 → fetchList 引用变化 → effect 重跑） =====
  const fetchList = useCallback(
    async (targetPage: number) => {
      const seq = ++fetchSeqRef.current;
      setLoading(true);
      setLoadError(false);
      try {
        const res = await getQuestionList({
          page: targetPage,
          size: DEFAULT_PAGE_SIZE,
          jobType: jobType.trim() || undefined,
          questionType,
          difficulty,
          status,
        });
        // 过期响应丢弃（已有更新的请求在途/完成）
        if (seq !== fetchSeqRef.current) return;
        setList(res.list);
        setTotal(res.total);
      } catch {
        if (seq !== fetchSeqRef.current) return;
        setLoadError(true);
        setList([]);
        setTotal(0);
      } finally {
        // 仅最新请求复位 loading，避免旧请求提前结束 loading 导致错位
        if (seq === fetchSeqRef.current) setLoading(false);
      }
    },
    [jobType, questionType, difficulty, status],
  );

  useEffect(() => {
    fetchList(page);
  }, [fetchList, page]);

  const handlePageChange = (nextPage: number) => {
    setPage(nextPage);
  };

  const handleReset = () => {
    setJobType('');
    setQuestionType(undefined);
    setDifficulty(undefined);
    setStatus(undefined);
    setPage(1);
  };

  // ===== 详情 / 编辑 =====
  const openCreate = () => {
    setFormRecord(null);
    setFormOpen(true);
  };

  /** 列表行编辑：先拉详情（含敏感字段回填 + version），2401 → 刷新列表 */
  const handleEdit = async (record: HrQuestionListVO) => {
    try {
      const detail = await getQuestionDetail(record.id);
      setFormRecord(detail);
      setFormOpen(true);
    } catch (error) {
      if (getErrorCode(error) === 2401) fetchList(page);
    }
  };

  /** 详情抽屉内编辑：直接使用抽屉已拉取的完整详情 */
  const handleEditFromDrawer = (detail: HrQuestionDetailVO) => {
    setDetailTarget(null);
    setFormRecord(detail);
    setFormOpen(true);
  };

  // ===== 删除（二次确认 + 删空回退页 + 2402 刷新） =====
  const handleDelete = (record: HrQuestionListVO) => {
    Modal.confirm({
      title: '确认删除',
      content: '确定要删除该题目吗？此操作不可恢复。',
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoadingId(record.id);
        try {
          await deleteQuestion(record.id, record.version);
          message.success('题目已删除');
          if (list.length === 1 && page > 1) {
            setPage(page - 1);
          } else {
            fetchList(page);
          }
        } catch {
          // 2402/2401 已由拦截器提示，刷新列表取最新状态
          fetchList(page);
        } finally {
          setActionLoadingId(null);
        }
      },
    });
  };

  // ===== 启停（ACTIVE→停用 / INACTIVE→启用，带 version 乐观锁） =====
  const handleToggle = async (record: HrQuestionListVO) => {
    const action: QuestionStatusAction =
      record.status === QuestionStatus.ACTIVE ? 'DISABLE' : 'ENABLE';
    setActionLoadingId(record.id);
    try {
      await changeQuestionStatus(record.id, { action, version: record.version });
      message.success(action === 'DISABLE' ? '题目已停用' : '题目已启用');
      fetchList(page);
    } catch {
      // 2402/2403 已由拦截器提示，刷新列表取最新状态
      fetchList(page);
    } finally {
      setActionLoadingId(null);
    }
  };

  // ===== 审核（仅 PENDING_REVIEW；REJECT 必填原因；类型层已强制 reason） =====
  const openReview = (record: HrQuestionListVO) => {
    setReviewTarget(record);
    setReviewAction('APPROVE');
    setReviewReason('');
  };

  const handleReviewSubmit = async () => {
    if (!reviewTarget) return;
    if (reviewAction === 'REJECT' && !reviewReason.trim()) {
      message.warning('拒绝时必须填写原因');
      return;
    }
    setReviewLoading(true);
    try {
      if (reviewAction === 'APPROVE') {
        await reviewQuestion(reviewTarget.id, {
          action: 'APPROVE',
          version: reviewTarget.version,
        });
        message.success('审核通过，题目已启用');
      } else {
        await reviewQuestion(reviewTarget.id, {
          action: 'REJECT',
          reason: reviewReason.trim(),
          version: reviewTarget.version,
        });
        message.success('已拒绝该题目');
      }
      setReviewTarget(null);
      setReviewAction('APPROVE');
      setReviewReason('');
      fetchList(page);
    } catch (error) {
      // 2402/2406 已由拦截器提示；2402 版本冲突 → 关闭审核态并刷新
      if (getErrorCode(error) === 2402) {
        setReviewTarget(null);
        fetchList(page);
      }
    } finally {
      setReviewLoading(false);
    }
  };

  // ===== 操作列状态机 =====
  const renderActions = (record: HrQuestionListVO) => {
    const btns: React.ReactNode[] = [];
    const rowLoading = actionLoadingId === record.id;

    // 详情 — 所有状态
    btns.push(
      <Button
        key="view"
        type="link"
        size="small"
        className={styles.actionBtn}
        onClick={() => setDetailTarget(record)}
      >
        <EyeOutlined /> 详情
      </Button>,
    );

    // 编辑 — ACTIVE/INACTIVE/REJECTED（PENDING_REVIEW 仅走审核）
    if (record.status !== QuestionStatus.PENDING_REVIEW) {
      btns.push(
        <Button
          key="edit"
          type="link"
          size="small"
          className={styles.actionBtnPrimary}
          loading={rowLoading}
          onClick={() => handleEdit(record)}
        >
          <EditOutlined /> 编辑
        </Button>,
      );
    }

    // 启停 — ACTIVE 显示停用 / INACTIVE 显示启用
    if (record.status === QuestionStatus.ACTIVE) {
      btns.push(
        <Button
          key="disable"
          type="link"
          size="small"
          className={styles.actionBtnWarn}
          loading={rowLoading}
          onClick={() => handleToggle(record)}
        >
          <StopOutlined /> 停用
        </Button>,
      );
    } else if (record.status === QuestionStatus.INACTIVE) {
      btns.push(
        <Button
          key="enable"
          type="link"
          size="small"
          className={styles.actionBtnPrimary}
          loading={rowLoading}
          onClick={() => handleToggle(record)}
        >
          <PlayCircleOutlined /> 启用
        </Button>,
      );
    }

    // 审核 — 仅 PENDING_REVIEW（预置能力，阶段6.3 AI 导入题起有真实数据）
    if (record.status === QuestionStatus.PENDING_REVIEW) {
      btns.push(
        <Button
          key="review"
          type="link"
          size="small"
          className={styles.actionBtnPrimary}
          loading={rowLoading}
          onClick={() => openReview(record)}
        >
          <AuditOutlined /> 审核
        </Button>,
      );
    }

    // 删除 — 所有状态
    btns.push(
      <Button
        key="delete"
        type="link"
        size="small"
        danger
        loading={rowLoading}
        onClick={() => handleDelete(record)}
      >
        <DeleteOutlined />
      </Button>,
    );

    return <Space size="small" wrap>{btns}</Space>;
  };

  const columns: ColumnsType<HrQuestionListVO> = [
    {
      title: '题干',
      dataIndex: 'content',
      key: 'content',
      width: 320,
      render: (text: string) => (
        <Tooltip title={text}>
          <div className={styles.contentCell}>
            <div className={styles.contentText}>{text}</div>
          </div>
        </Tooltip>
      ),
    },
    {
      title: '技能标签',
      dataIndex: 'skillTags',
      key: 'skillTags',
      width: 220,
      render: (tags: string[]) => (
        <div className={styles.skillTags}>
          {(tags || []).slice(0, 3).map((tag) => (
            <span key={tag} className={styles.skillTag}>{tag}</span>
          ))}
          {(tags || []).length > 3 && (
            <span className={styles.skillTagMore}>+{(tags || []).length - 3}</span>
          )}
        </div>
      ),
    },
    {
      title: '岗位类型',
      dataIndex: 'jobType',
      key: 'jobType',
      width: 130,
      render: (t: string) => (t ? renderJobTypeTag(t) : '-'),
    },
    {
      title: '题型',
      dataIndex: 'questionType',
      key: 'questionType',
      width: 100,
      render: (t: QuestionType) => <Tag color={TYPE_COLOR[t]}>{TYPE_LABEL[t] || t}</Tag>,
    },
    {
      title: '难度',
      dataIndex: 'difficulty',
      key: 'difficulty',
      width: 80,
      render: (d: QuestionDifficulty) => <Tag color={DIFF_COLOR[d]}>{DIFF_LABEL[d] || d}</Tag>,
    },
    {
      title: '来源',
      dataIndex: 'source',
      key: 'source',
      width: 90,
      render: (s: QuestionSource) => <Tag>{SOURCE_LABEL[s] || s}</Tag>,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (s: QuestionStatus) => (
        <StatusTag type={STATUS_TAG_TYPE[s]}>{STATUS_LABEL[s] || s}</StatusTag>
      ),
    },
    {
      title: '创建时间',
      dataIndex: 'createdAt',
      key: 'createdAt',
      width: 150,
      render: (t: string) => <span className={styles.timeCell}>{fmtTime(t)}</span>,
    },
    {
      title: '操作',
      key: 'actions',
      width: 230,
      fixed: 'right',
      render: (_: unknown, record) => renderActions(record),
    },
  ];

  return (
    <div className={styles.page}>
      <PageHeader
        title="题库管理"
        description="管理本企业私有面试题库（新增/编辑/启停/审核）"
        extra={
          <Button type="primary" className={styles.accentBtn} icon={<PlusOutlined />} onClick={openCreate}>
            创建题目
          </Button>
        }
      />

      <div className={styles.card}>
        <div className={styles.filterBar}>
          <div className={styles.filterRow}>
            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>岗位类型</span>
              <AutoComplete
                className={styles.filterInput}
                placeholder="如 Java后端"
                allowClear
                value={jobType}
                onChange={(v) => {
                  setJobType(v);
                  setPage(1);
                }}
                options={JOB_TYPE_OPTIONS}
              />
            </div>
            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>题型</span>
              <Select
                className={styles.filterSelect}
                placeholder="全部"
                allowClear
                value={questionType}
                onChange={(v) => {
                  setQuestionType(v);
                  setPage(1);
                }}
                options={questionTypeOptions}
              />
            </div>
            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>难度</span>
              <Select
                className={styles.filterSelect}
                placeholder="全部"
                allowClear
                value={difficulty}
                onChange={(v) => {
                  setDifficulty(v);
                  setPage(1);
                }}
                options={difficultyOptions}
              />
            </div>
            <div className={styles.filterItem}>
              <span className={styles.filterLabel}>状态</span>
              <Select
                className={styles.filterSelect}
                placeholder="全部"
                allowClear
                value={status}
                onChange={(v) => {
                  setStatus(v);
                  setPage(1);
                }}
                options={statusOptions}
              />
            </div>
            <Button className={styles.resetBtn} icon={<ReloadOutlined />} onClick={handleReset}>
              重置
            </Button>
          </div>
        </div>

        {loadError ? (
          <div className={styles.errorContainer}>
            <Empty description="加载失败，请重试">
              <Button type="primary" onClick={() => fetchList(page)}>重新加载</Button>
            </Empty>
          </div>
        ) : (
          <Table
            columns={columns}
            dataSource={list}
            rowKey="id"
            className={styles.table}
            loading={loading}
            pagination={{
              current: page,
              total,
              pageSize: DEFAULT_PAGE_SIZE,
              showSizeChanger: false,
              onChange: handlePageChange,
              showTotal: (t) => `共 ${t} 个题目`,
            }}
            scroll={{ x: 1400 }}
            locale={{ emptyText: '暂无题库数据' }}
          />
        )}
      </div>

      {/* 详情抽屉 */}
      <QuestionDetailDrawer
        target={detailTarget}
        onClose={() => setDetailTarget(null)}
        onEdit={handleEditFromDrawer}
      />

      {/* 新增/编辑弹窗 */}
      <QuestionFormModal
        open={formOpen}
        record={formRecord}
        onClose={() => setFormOpen(false)}
        onSuccess={() => {
          setFormOpen(false);
          fetchList(page);
        }}
      />

      {/* 审核弹窗（REJECT 必填原因） */}
      <Modal
        title="题目审核"
        open={!!reviewTarget}
        onCancel={() => setReviewTarget(null)}
        onOk={handleReviewSubmit}
        confirmLoading={reviewLoading}
        okText="提交审核"
        destroyOnClose
      >
        {reviewTarget && (
          <div className={styles.reviewBody}>
            <div className={styles.reviewContent}>{reviewTarget.content}</div>
            <div className={styles.reviewAction}>
              <Radio.Group
                value={reviewAction}
                onChange={(e) => setReviewAction(e.target.value as 'APPROVE' | 'REJECT')}
              >
                <Radio value="APPROVE">通过</Radio>
                <Radio value="REJECT">拒绝</Radio>
              </Radio.Group>
            </div>
            {reviewAction === 'REJECT' && (
              <Input.TextArea
                rows={3}
                placeholder="请填写拒绝原因（必填）"
                value={reviewReason}
                onChange={(e) => setReviewReason(e.target.value)}
              />
            )}
          </div>
        )}
      </Modal>
    </div>
  );
};

export default QuestionBankPage;
