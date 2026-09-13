import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'umi';
import {
  Table,
  Button,
  Space,
  Tabs,
  Tooltip,
  message,
  Modal,
  Empty,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import {
  ReloadOutlined,
  PlusOutlined,
  EyeOutlined,
  EditOutlined,
  SendOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import StatusTag from '@/components/StatusTag';
import {
  getHrJobs,
  getHrJobDetail,
  changeJobStatus,
  deleteDraftJob,
} from '@/services/job';
import type { HrJobListItem, JobStatus } from '@/services/job';
import { getErrorCode } from '@/utils/apiError';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

/** 状态 → 展示文案 */
const statusLabelMap: Record<string, string> = {
  DRAFT: '草稿',
  PUBLISHED: '招聘中',
  PAUSED: 'HC已预占满',
  CLOSED: '已关闭',
};

/** 状态 → StatusTag type */
const statusTagTypeMap: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'neutral'> = {
  DRAFT: 'neutral',
  PUBLISHED: 'success',
  PAUSED: 'warning',
  CLOSED: 'danger',
};

/** 分 → k 展示（薪资单位为分，月薪换算成 k/月） */
const fmtSalary = (minAmount: number | null, maxAmount: number | null, months: number | null) => {
  if (minAmount === null || maxAmount === null) return '薪资面议';
  const minK = (minAmount / 100000).toFixed(1).replace('.0', '');
  const maxK = (maxAmount / 100000).toFixed(1).replace('.0', '');
  const monthStr = months && months > 12 ? `·${months}薪` : '';
  return `${minK}k-${maxK}k${monthStr}`;
};

/** 格式化 ISO 时间 → 展示 */
const fmtTime = (iso?: string) => {
  if (!iso) return '-';
  return iso.replace('T', ' ').substring(0, 16);
};

const DEFAULT_PAGE_SIZE = 10;

/**
 * 岗位名称单元格：仅文本真实溢出（scrollWidth > clientWidth）时启用 Ant Tooltip；
 * 不设置原生 title 属性——未溢出时无任何提示框，溢出时仅 antd Tooltip 展示完整名称，
 * 避免短标题悬停出现无意义的浏览器原生提示。
 */
const EllipsisJobTitle: React.FC<{ title: string }> = ({ title }) => {
  const spanRef = useRef<HTMLSpanElement>(null);
  const [overflow, setOverflow] = useState(false);

  const checkOverflow = useCallback(() => {
    const el = spanRef.current;
    if (el) setOverflow(el.scrollWidth > el.clientWidth);
  }, []);

  // 文本变化时重测；窗口缩放后重测（表格列宽固定，resize 即覆盖列宽变化场景）
  useEffect(() => {
    checkOverflow();
    window.addEventListener('resize', checkOverflow);
    return () => window.removeEventListener('resize', checkOverflow);
  }, [checkOverflow, title]);

  const content = (
    <span ref={spanRef} className={styles.jobTitle}>
      {title}
    </span>
  );
  // 溢出时用 Tooltip 展示完整名称；未溢出时不渲染 Tooltip、不设原生 title
  return overflow ? <Tooltip title={title}>{content}</Tooltip> : content;
};

const JobListPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();

  // 从 URL Query 恢复筛选状态（status / page）
  const activeTab = searchParams.get('status') || 'ALL';
  const page = Math.max(Number(searchParams.get('page')) || 1, 1);

  const [jobs, setJobs] = useState<HrJobListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  const [actionLoadingId, setActionLoadingId] = useState<string | null>(null);

  // ===== 数据加载 =====
  const fetchJobs = useCallback(
    async (targetPage: number, statusKey: string) => {
      setLoading(true);
      setLoadError(false);
      try {
        const params =
          statusKey === 'ALL'
            ? { page: targetPage, size: DEFAULT_PAGE_SIZE }
            : { status: statusKey as JobStatus, page: targetPage, size: DEFAULT_PAGE_SIZE };
        const res = await getHrJobs(params);
        setJobs(res.list);
        setTotal(res.total);
      } catch {
        setLoadError(true);
        setJobs([]);
        setTotal(0);
      } finally {
        setLoading(false);
      }
    },
    [],
  );

  useEffect(() => {
    fetchJobs(page, activeTab);
  }, [fetchJobs, page, activeTab]);

  const handleTabChange = (key: string) => {
    const params = new URLSearchParams(searchParams);
    if (key === 'ALL') params.delete('status');
    else params.set('status', key);
    params.set('page', '1');
    setSearchParams(params);
  };

  const handlePageChange = (nextPage: number) => {
    const params = new URLSearchParams(searchParams);
    if (nextPage <= 1) params.delete('page');
    else params.set('page', String(nextPage));
    setSearchParams(params);
  };

  const handleReset = () => {
    setSearchParams({});
  };

  const handleReload = () => {
    fetchJobs(page, activeTab);
  };

  // ===== 状态操作（发布/关闭/删除，统一 2102 用 getErrorCode 判断） =====
  const handlePublish = (job: HrJobListItem) => {
    Modal.confirm({
      title: '确认发布',
      content: `确定要发布岗位"${job.title}"吗？发布后将对求职者公开。`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: async () => {
        setActionLoadingId(job.jobId);
        try {
          // 发布前先取详情快照：version + profileVersion 必须来自同一时间点（列表 VO 无 profileVersion）
          const detail = await getHrJobDetail(job.jobId);
          if (!detail.profileConfirmed) {
            message.warning('画像未确认，请先在编辑页确认画像后再发布');
            return;
          }
          await changeJobStatus(job.jobId, {
            action: 'PUBLISH',
            version: detail.version,
            profileVersion: detail.profileVersion,
          });
          message.success('岗位发布成功');
          fetchJobs(page, activeTab);
        } catch (error) {
          if (getErrorCode(error) === 2101) {
            message.error('岗位不存在或已关闭');
          } else if (getErrorCode(error) !== 2102) {
            // 2102 已由拦截器提示，其余错误也无需重复提示
          }
          // 2102 冲突：重新请求最新状态
          fetchJobs(page, activeTab);
        } finally {
          setActionLoadingId(null);
        }
      },
    });
  };

  const handleClose = (job: HrJobListItem) => {
    const hasReserved = job.reservedHc > 0;
    Modal.confirm({
      title: '确认关闭',
      content: hasReserved
        ? `岗位"${job.title}"仍有 ${job.reservedHc} 个已发送待确认Offer。关闭后候选人将无法投递，但不影响已发送的Offer。确定关闭？`
        : `确定要关闭岗位"${job.title}"吗？关闭后候选人将无法投递。`,
      okText: '确认关闭',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoadingId(job.jobId);
        try {
          await changeJobStatus(job.jobId, { action: 'CLOSE', version: job.version });
          message.success('岗位已关闭');
          fetchJobs(page, activeTab);
        } catch (error) {
          if (getErrorCode(error) !== 2102) {
            // 拦截器已提示，2102 时重新加载最新状态
          }
          fetchJobs(page, activeTab);
        } finally {
          setActionLoadingId(null);
        }
      },
    });
  };

  const handleDelete = (job: HrJobListItem) => {
    Modal.confirm({
      title: '确认删除',
      content: `确定要删除草稿"${job.title}"吗？此操作不可恢复。`,
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoadingId(job.jobId);
        try {
          await deleteDraftJob(job.jobId, job.version);
          message.success('草稿已删除');
          // 当前页删空且 page>1 → 请求上一页
          if (jobs.length === 1 && page > 1) {
            handlePageChange(page - 1);
          } else {
            fetchJobs(page, activeTab);
          }
        } catch (error) {
          if (getErrorCode(error) !== 2102) {
            // 拦截器已提示
          }
          fetchJobs(page, activeTab);
        } finally {
          setActionLoadingId(null);
        }
      },
    });
  };

  // ===== 操作按钮按状态矩阵（仅 DRAFT 可编辑/发布/删除） =====
  const renderActions = (record: HrJobListItem) => {
    const btns: React.ReactNode[] = [];

    // 查看 — 所有状态
    btns.push(
      <Button
        key="view"
        type="link" size="small"
        onClick={() => navigate(`${ROUTES.HR_JOB_DETAIL}/${record.jobId}`)}
      ><EyeOutlined /> 查看</Button>,
    );

    // 编辑 — 仅 DRAFT（updateJob 仅 DRAFT，否则 2104）
    if (record.status === 'DRAFT') {
      btns.push(
        <Button
          key="edit"
          type="link" size="small"
          loading={actionLoadingId === record.jobId}
          onClick={() => navigate(`${ROUTES.HR_JOB_EDIT}/${record.jobId}/edit`)}
        ><EditOutlined /> 编辑</Button>,
      );
    }

    // 发布 — 仅 DRAFT
    if (record.status === 'DRAFT') {
      btns.push(
        <Button
          key="publish" type="link" size="small" className={styles.actionBtnPrimary}
          loading={actionLoadingId === record.jobId}
          onClick={() => handlePublish(record)}
        ><SendOutlined /> 发布</Button>,
      );
    }

    // 关闭 — PUBLISHED 或 PAUSED（不带 closeReason，后端固定 MANUAL）
    if (record.status === 'PUBLISHED' || record.status === 'PAUSED') {
      btns.push(
        <Button
          key="close" type="link" size="small" danger
          loading={actionLoadingId === record.jobId}
          onClick={() => handleClose(record)}
        ><CloseCircleOutlined /> 关闭</Button>,
      );
    }

    // 删除 — 仅 DRAFT
    if (record.status === 'DRAFT') {
      btns.push(
        <Button
          key="delete" type="link" size="small" danger
          loading={actionLoadingId === record.jobId}
          onClick={() => handleDelete(record)}
        ><DeleteOutlined /></Button>,
      );
    }

    return <Space size="small" wrap>{btns}</Space>;
  };

  const columns: ColumnsType<HrJobListItem> = [
    {
      title: '岗位名称',
      dataIndex: 'title',
      key: 'title',
      width: 280,
      // 单行省略；仅溢出时显示 Tooltip（EllipsisJobTitle 内部按真实渲染宽度判断）
      render: (text: string) => (
        <div className={styles.jobTitleCell}>
          <EllipsisJobTitle title={text} />
        </div>
      ),
    },
    {
      title: '城市',
      dataIndex: 'cityName',
      key: 'cityName',
      width: 70,
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 90,
      render: (status: string) => (
        <StatusTag type={statusTagTypeMap[status]}>
          {statusLabelMap[status]}
        </StatusTag>
      ),
    },
    {
      title: '核心技能',
      dataIndex: 'skillTags',
      key: 'skillTags',
      width: 140,
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
      title: '薪资',
      key: 'salary',
      width: 110,
      render: (_: unknown, record: HrJobListItem) => (
        <span className={styles.timeCell}>
          {fmtSalary(record.salary.minAmount, record.salary.maxAmount, record.salary.months)}
        </span>
      ),
    },
    {
      title: 'HC明细',
      key: 'hcDetail',
      width: 120,
      render: (_: unknown, record: HrJobListItem) => {
        const { totalHc, reservedHc, confirmedHc, availableHc } = record;
        const hasWarning = availableHc <= 0;
        return (
          <div className={styles.hcCell}>
            <span className={styles.hcText}>
              总{totalHc} / 确认{confirmedHc} / 预冻{reservedHc}
            </span>
            <span className={`${styles.hcAvailable} ${hasWarning ? styles.hcFull : ''}`}>
              可用{availableHc}
            </span>
          </div>
        );
      },
    },
    {
      title: '发布时间',
      dataIndex: 'publishedAt',
      key: 'publishedAt',
      width: 135,
      render: (time: string | undefined) => (
        <span className={styles.timeCell}>{fmtTime(time)}</span>
      ),
    },
    {
      title: '更新时间',
      dataIndex: 'updatedAt',
      key: 'updatedAt',
      width: 135,
      render: (time: string) => (
        <span className={styles.timeCell}>{fmtTime(time)}</span>
      ),
    },
    {
      title: '发布人',
      dataIndex: 'createdByName',
      key: 'createdByName',
      width: 90,
      render: (name: string | undefined, record: HrJobListItem) => (
        <span className={styles.timeCell}>{name || record.createdBy || '-'}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 220,
      fixed: 'right',
      render: (_: unknown, record: HrJobListItem) => renderActions(record),
    },
  ];

  const tabItems = [
    { key: 'ALL', label: '全部' },
    { key: 'PUBLISHED', label: '招聘中' },
    { key: 'DRAFT', label: '草稿' },
    { key: 'PAUSED', label: 'HC已预占满' },
    { key: 'CLOSED', label: '已关闭' },
  ];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="岗位管理"
        desc="管理本企业岗位、状态与Headcount"
        extra={
          <Button
            type="primary"
            className={styles.accentBtn}
            icon={<PlusOutlined />}
            onClick={() => navigate(ROUTES.HR_JOB_CREATE)}
          >
            创建新岗位
          </Button>
        }
      />

      <div className={styles.card}>
        <div className={styles.filterBar}>
          <Tabs
            activeKey={activeTab}
            onChange={handleTabChange}
            items={tabItems}
            className={styles.statusTabs}
          />
          {/* 阶段一隐藏关键字搜索框：后端 list 无 keyword 参数，本地过滤当前页会跨页漏结果（L1） */}
          <div className={styles.searchRow}>
            <Button
              className={styles.resetBtn}
              icon={<ReloadOutlined />}
              onClick={handleReset}
            >
              重置
            </Button>
          </div>
        </div>

        {loadError ? (
          <div className={styles.errorContainer}>
            <Empty description="加载失败，请重试">
              <Button type="primary" onClick={handleReload}>重新加载</Button>
            </Empty>
          </div>
        ) : (
          <Table
            columns={columns}
            dataSource={jobs}
            rowKey="jobId"
            className={styles.table}
            size="middle"
            loading={loading}
            pagination={{
              current: page,
              total,
              pageSize: DEFAULT_PAGE_SIZE,
              showSizeChanger: false,
              onChange: handlePageChange,
              showTotal: (t) => `共 ${t} 个岗位`,
            }}
            scroll={{ x: 1380 }}
            locale={{ emptyText: '暂无岗位数据' }}
          />
        )}
      </div>
    </div>
  );
};

export default JobListPage;
