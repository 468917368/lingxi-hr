/**
 * HR 人才库（/hr/candidate）
 * 候选人列表 + Top5 高潜推荐，支持状态/匹配度/岗位/关键词/排序筛选；
 * 状态操作：查看简历、标记合适/不合适、邀约面试、发起Offer、发起对话。
 */
import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate } from 'umi';
import {
  Table,
  Select,
  Input,
  Button,
  Space,
  Progress,
  message,
  Modal,
  Spin,
  Form,
  DatePicker,
} from 'antd';
import type { ColumnsType } from 'antd/es/table';
import dayjs from 'dayjs';
import {
  SearchOutlined,
  ReloadOutlined,
  UserOutlined,
  FileTextOutlined,
  CalendarOutlined,
  TrophyOutlined,
  CheckOutlined,
  CloseOutlined,
  StarFilled,
  PhoneOutlined,
  MailOutlined,
  WechatOutlined,
  RadarChartOutlined,
  MessageOutlined,
  DollarOutlined,
} from '@ant-design/icons';
import StatusTag from '@/components/StatusTag';
import EmptyState from '@/components/EmptyState';
import { getAvatarUrl } from '@/utils/fileUrl';
import {
  getCandidateList,
  getCandidateResume,
  getHrJobList,
  getTop5Candidates,
  markCandidate,
  createInterview,
} from '@/services/hr';
import { listMembers } from '@/services/company';
import { getConversations, createConversation } from '@/services/message';
import useUserStore from '@/stores/userStore';
import type {
  CandidateListItem,
  CandidateMarkAction,
  TopCandidate,
  InterviewMethod,
} from '@/constants/apiTypes';
import type { ResumeDetail } from '@/services/resume';
import { ApplicationStatus, isOfferBlocked } from '@/constants/enums';
import styles from './index.less';

const statusLabelMap: Record<ApplicationStatus, string> = {
  [ApplicationStatus.SUBMITTED]: '已投递',
  [ApplicationStatus.VIEWED]: '已查看',
  [ApplicationStatus.SCREENED]: '已筛选',
  [ApplicationStatus.INTERVIEWING]: '面试中',
  [ApplicationStatus.OFFERABLE]: '可录用',
  [ApplicationStatus.OFFERED]: '待录用',
  [ApplicationStatus.OFFER_ACCEPTED]: '已录用',
  [ApplicationStatus.OFFER_DECLINED]: '已拒绝',
  [ApplicationStatus.REJECTED]: '已淘汰',
  [ApplicationStatus.WITHDRAWN]: '已撤回',
};

const statusTagTypeMap: Record<ApplicationStatus, 'info' | 'success' | 'warning' | 'danger' | 'neutral' | 'primary'> = {
  [ApplicationStatus.SUBMITTED]: 'info',
  [ApplicationStatus.VIEWED]: 'neutral',
  [ApplicationStatus.SCREENED]: 'success',
  [ApplicationStatus.INTERVIEWING]: 'info',
  [ApplicationStatus.OFFERABLE]: 'success',
  [ApplicationStatus.OFFERED]: 'warning',
  [ApplicationStatus.OFFER_ACCEPTED]: 'success',
  [ApplicationStatus.OFFER_DECLINED]: 'danger',
  [ApplicationStatus.REJECTED]: 'danger',
  [ApplicationStatus.WITHDRAWN]: 'neutral',
};

/** 状态筛选支持后端全部投递状态（单值） */
const statusFilterOptions = [
  ApplicationStatus.SUBMITTED,
  ApplicationStatus.VIEWED,
  ApplicationStatus.SCREENED,
  ApplicationStatus.INTERVIEWING,
  ApplicationStatus.OFFERABLE,
  ApplicationStatus.OFFERED,
  ApplicationStatus.OFFER_ACCEPTED,
  ApplicationStatus.OFFER_DECLINED,
  ApplicationStatus.REJECTED,
  ApplicationStatus.WITHDRAWN,
].map((s) => ({ value: s, label: statusLabelMap[s] }));

const sortOptions = [
  { value: 'matchScore', label: '匹配度' },
  { value: 'submittedAt', label: '投递时间' },
] as const;

const formatTime = (t?: string) => (t ? t.replace('T', ' ') : '-');

/** 简历解析状态兜底文案 */
const parseStatusText = (s?: string) => {
  switch (s) {
    case 'PENDING':
    case 'PARSING':
      return '简历解析中，请稍后查看';
    case 'FAILED':
      return '简历解析失败，无法展示结构化内容';
    case 'COMPLETED':
      return '简历尚未解析出结构化内容';
    default:
      return '暂无简历内容';
  }
};

/** 简历解析状态徽标 */
const parseStatusBadge = (s?: string): { text: string; cls: string } | null => {
  switch (s) {
    case 'COMPLETED':
      return { text: '已解析', cls: 'done' };
    case 'PENDING':
    case 'PARSING':
      return { text: '解析中', cls: 'parsing' };
    case 'FAILED':
      return { text: '解析失败', cls: 'failed' };
    default:
      return null;
  }
};

/** 简历只读查看（HR 人才库） */
const ResumeViewer: React.FC<{ detail: ResumeDetail }> = ({ detail }) => {
  // 兼容两种库内格式：解析 agent 契约 sections / 旧数据 cards
  const chapters = detail.cardStructure?.cards ?? detail.cardStructure?.sections ?? [];
  const statusBadge = parseStatusBadge(detail.parseStatus);
  const contacts = [
    { icon: <PhoneOutlined />, label: '手机号', value: detail.phone },
    { icon: <MailOutlined />, label: '邮箱', value: detail.email },
    { icon: <WechatOutlined />, label: '微信', value: detail.wechat },
  ].filter((c) => c.value);

  return (
    <div className={styles.resumeViewer}>
      {/* 头部 */}
      <div className={styles.resumeHero}>
        {detail.facePhotoUrl ? (
          <img
            src={getAvatarUrl(detail.facePhotoUrl)}
            alt="照片"
            className={styles.resumeHeroPhoto}
          />
        ) : (
          <div className={styles.resumeHeroAvatar}>
            <UserOutlined />
          </div>
        )}
        <div className={styles.resumeHeroInfo}>
          <div className={styles.resumeHeroName}>{detail.candidateName || '—'}</div>
          <div className={styles.resumeHeroMeta}>
            {detail.fileName}
            {detail.fileFormat ? ` · ${detail.fileFormat.toUpperCase()}` : ''}
            {detail.updatedAt ? ` · 更新于 ${formatTime(detail.updatedAt)}` : ''}
          </div>
        </div>
        {statusBadge && (
          <span className={`${styles.resumeHeroBadge} ${styles[statusBadge.cls]}`}>
            {statusBadge.text}
          </span>
        )}
      </div>

      {/* 联系方式 */}
      {contacts.length > 0 && (
        <div className={styles.resumeContact}>
          {contacts.map((item) => (
            <div key={item.label} className={styles.resumeContactItem}>
              <span className={styles.resumeContactIcon}>{item.icon}</span>
              <span className={styles.resumeContactValue}>{item.value}</span>
            </div>
          ))}
        </div>
      )}

      {/* 章节 */}
      {chapters.length > 0 ? (
        <div className={styles.resumeSections}>
          {chapters.map((section, idx) => (
            <div key={idx} className={styles.resumeSection}>
              <div className={styles.resumeSectionTitle}>
                <span className={styles.resumeSectionBar} />
                {section.title}
                {section.confidence === 'LOW' && (
                  <span className={styles.lowConfBadge}>置信度低</span>
                )}
              </div>
              {section.points.length > 0 ? (
                <div className={styles.resumeSectionPoints}>
                  {section.points.map((p, pIdx) => (
                    <div key={p.id || pIdx} className={styles.resumePoint}>
                      <span className={styles.resumePointDot} />
                      <span className={styles.resumePointText}>{p.text}</span>
                    </div>
                  ))}
                </div>
              ) : (
                <div className={styles.resumePointEmpty}>{section.raw_text || '—'}</div>
              )}
            </div>
          ))}
        </div>
      ) : (
        <div className={styles.resumeEmpty}>{parseStatusText(detail.parseStatus)}</div>
      )}
    </div>
  );
};

const CandidatePage: React.FC = () => {
  const [list, setList] = useState<CandidateListItem[]>([]);
  const [loading, setLoading] = useState(false);
  const [total, setTotal] = useState(0);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(20);
  const [statusFilter, setStatusFilter] = useState<ApplicationStatus | undefined>();
  const [scoreFilter, setScoreFilter] = useState<number | undefined>();
  const [jobFilter, setJobFilter] = useState<number | undefined>();
  const [keyword, setKeyword] = useState('');
  const [debouncedKeyword, setDebouncedKeyword] = useState('');
  const [sortBy, setSortBy] = useState<'matchScore' | 'submittedAt'>('matchScore');
  const [top5, setTop5] = useState<TopCandidate[]>([]);
  const [top5Loading, setTop5Loading] = useState(false);
  const [jobOptions, setJobOptions] = useState<{ value: number; label: string }[]>([]);
  // 简历查看 Modal
  const [resumeVisible, setResumeVisible] = useState(false);
  const [resumeTarget, setResumeTarget] = useState<CandidateListItem | null>(null);
  const [resumeDetail, setResumeDetail] = useState<ResumeDetail | null>(null);
  const [resumeLoading, setResumeLoading] = useState(false);
  const [resumeError, setResumeError] = useState(false);

  // 邀约面试 Modal
  const [createOpen, setCreateOpen] = useState(false);
  const [createTarget, setCreateTarget] = useState<CandidateListItem | null>(null);
  const [createForm] = Form.useForm();
  const [interviewers, setInterviewers] = useState<{ value: number; label: string }[]>([]);
  const [createSubmitting, setCreateSubmitting] = useState(false);

  // 对话入口：当前创建/查询中的候选人（防重复点击）
  const navigate = useNavigate();
  const { userInfo } = useUserStore();
  const [chattingId, setChattingId] = useState<string | null>(null);

  // 岗位下拉（与候选人 jobId 同源）
  useEffect(() => {
    getHrJobList({ page: 1, size: 50 })
      .then((data) => {
        setJobOptions(
          (data.list ?? []).map((j) => ({ value: j.jobId, label: j.title })),
        );
      })
      .catch(() => setJobOptions([]));
  }, []);

  // 面试官下拉（本企业 ACTIVE 面试官）
  useEffect(() => {
    listMembers({ role: 'INTERVIEWER', status: 'ACTIVE' })
      .then((members) =>
        setInterviewers(
          (Array.isArray(members) ? members : []).map((m) => ({
            value: m.userId,
            label: m.name,
          })),
        ),
      )
      .catch(() => setInterviewers([]));
  }, []);

  // 关键词防抖
  useEffect(() => {
    const timer = setTimeout(() => {
      setDebouncedKeyword(keyword.trim());
      setPage(1);
    }, 300);
    return () => clearTimeout(timer);
  }, [keyword]);

  // 候选人列表
  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getCandidateList({
        jobId: jobFilter,
        status: statusFilter,
        minMatchScore: scoreFilter,
        keyword: debouncedKeyword || undefined,
        sortBy,
        page,
        size: pageSize,
      });
      setList(data.list);
      setTotal(data.total);
    } catch {
      // 拦截器已展示错误提示，列表保留原数据
    } finally {
      setLoading(false);
    }
  }, [jobFilter, statusFilter, scoreFilter, debouncedKeyword, sortBy, page, pageSize]);

  useEffect(() => {
    fetchList();
  }, [fetchList]);

  // Top5 高潜推荐
  const fetchTop5 = useCallback(async () => {
    setTop5Loading(true);
    try {
      const data = await getTop5Candidates({ jobId: jobFilter });
      setTop5(data);
    } catch {
      setTop5([]);
    } finally {
      setTop5Loading(false);
    }
  }, [jobFilter]);

  useEffect(() => {
    fetchTop5();
  }, [fetchTop5]);

  // 简历查看：Modal 打开且目标变化时拉取
  useEffect(() => {
    if (!resumeVisible || !resumeTarget) return;
    setResumeLoading(true);
    setResumeError(false);
    getCandidateResume(resumeTarget.id)
      .then((data) => {
        setResumeDetail(data);
        // 简历查看成功：后端已把 SUBMITTED → VIEWED，本地同步行状态（避免整表刷新）
        setList((prev) =>
          prev.map((item) =>
            item.id === resumeTarget.id && item.status === ApplicationStatus.SUBMITTED
              ? { ...item, status: ApplicationStatus.VIEWED }
              : item,
          ),
        );
      })
      .catch(() => setResumeError(true)) // 拦截器已 toast 4011/4300/4301/4303
      .finally(() => setResumeLoading(false));
  }, [resumeVisible, resumeTarget]);

  /** 标记合适/不合适 */
  const handleMark = (record: CandidateListItem, action: CandidateMarkAction) => {
    const isSuitable = action === 'SUITABLE';
    Modal.confirm({
      title: isSuitable ? '标记为合适' : '标记为不合适',
      content: `确定将 ${record.candidateName}（${record.jobTitle}）标记为${isSuitable ? '合适' : '不合适'}吗？将自动通知候选人。`,
      okText: '确认',
      cancelText: '取消',
      onOk: async () => {
        try {
          const res = await markCandidate(record.id, action);
          if (res.notificationSent) {
            message.success(res.newStatusDesc);
          } else {
            message.warning(`${res.newStatusDesc}，但候选人通知发送失败`);
          }
          setList((prev) =>
            prev.map((item) =>
              item.id === record.id ? { ...item, status: res.newStatus } : item,
            ),
          );
        } catch {
          // 4011/4302/3006 已由拦截器提示；刷新列表同步服务端状态
        } finally {
          fetchList();
        }
      },
    });
  };

  const handleViewResume = (record: CandidateListItem) => {
    setResumeTarget(record);
    setResumeDetail(null);
    setResumeError(false);
    setResumeVisible(true);
  };

  /** Top5 卡片查看简历：直接用后端返回的 applicationId 调简历接口（jobTitle 从列表补充展示） */
  const handleViewTopResume = (c: TopCandidate) => {
    if (!c.applicationId) {
      message.warning('未找到该候选人的投递记录');
      return;
    }
    const app = list.find((l) => l.candidateId === c.candidateId);
    handleViewResume({
      id: c.applicationId,
      candidateName: c.candidateName,
      jobTitle: app?.jobTitle ?? '',
    } as CandidateListItem);
  };

  const handleInviteInterview = (record: CandidateListItem) => {
    setCreateTarget(record);
    createForm.resetFields();
    createForm.setFieldsValue({ method: 'ONLINE' as InterviewMethod });
    setCreateOpen(true);
  };

  /** 发起Offer：跳转 Offer 管理页并自动预填发起弹窗 */
  const handleCreateOffer = (record: CandidateListItem) => {
    navigate(
      `/hr/offer?applicationId=${record.id}&jobId=${record.jobId}&candidateName=${encodeURIComponent(
        record.candidateName,
      )}`,
    );
  };

  /** 发起对话：先查已有会话（复用）→ 没有再创建 → 跳转消息页并打开 */
  const handleChat = async (record: CandidateListItem) => {
    if (!userInfo?.companyId || !userInfo.id) {
      message.warning('企业/用户信息缺失，无法发起对话');
      return;
    }
    setChattingId(record.id);
    try {
      // 1) 先查会话列表，命中与该候选人的已有会话则直接复用（不重复创建）
      const convs = await getConversations();
      const existing = convs.find(
        (c) => c.targetUser.id === record.candidateId && c.targetUser.role === 'CANDIDATE',
      );
      const conversationId = existing
        ? existing.id
        : await createConversation({
            companyId: userInfo.companyId,
            hrId: userInfo.id,
            candidateId: record.candidateId,
            applicationId: record.id, // 投递记录 ID，关联上下文
          });
      navigate(`/hr/message?conversationId=${conversationId}`);
    } catch {
      // 错误已由 request 拦截器统一提示（4xx/5xx）
    } finally {
      setChattingId(null);
    }
  };

  /** Top5 推荐卡片发起对话（TopCandidate 无 id，用 applicationId 兼容） */
  const handleChatTop = (c: TopCandidate) => {
    handleChat({
      id: c.applicationId,
      candidateId: c.candidateId,
      candidateName: c.candidateName,
    } as CandidateListItem);
  };

  const handleCreateSubmit = async () => {
    if (!createTarget) return;
    const values = await createForm.validateFields();
    setCreateSubmitting(true);
    try {
      await createInterview({
        applicationId: createTarget.id,
        interviewerId: values.interviewerId,
        scheduledAt: (values.scheduledAt as dayjs.Dayjs).format('YYYY-MM-DDTHH:mm:ss'),
        method: values.method as InterviewMethod,
        location: values.location || undefined,
        remark: values.remark || undefined,
        candidateNote: values.candidateNote || undefined,
      });
      message.success('面试已创建，已通知候选人与面试官');
      setCreateOpen(false);
      fetchList(); // 投递状态已自动 → INTERVIEWING，刷新列表
    } catch {
      // 4102 时间冲突 / 3006 状态不可操作 已由拦截器提示
    } finally {
      setCreateSubmitting(false);
    }
  };

  const canMark = (s: ApplicationStatus) =>
    s === ApplicationStatus.SUBMITTED || s === ApplicationStatus.VIEWED;

  const columns: ColumnsType<CandidateListItem> = [
    {
      title: '候选人',
      dataIndex: 'candidateName',
      key: 'candidateName',
      width: 160,
      render: (name: string, record: CandidateListItem) => (
        <div className={styles.candidateCell}>
          <div className={styles.candidateAvatar}>
            {record.avatar ? (
              <img
                src={getAvatarUrl(record.avatar)}
                alt={name}
                className={styles.candidateAvatarImg}
              />
            ) : (
              <UserOutlined />
            )}
          </div>
          <div>
            <div className={styles.candidateName}>{name}</div>
            <div className={styles.candidateEmail}>{record.phone || '—'}</div>
          </div>
        </div>
      ),
    },
    {
      title: '投递岗位',
      dataIndex: 'jobTitle',
      key: 'jobTitle',
      width: 160,
      render: (title: string, record: CandidateListItem) => (
        <div>
          <div className={styles.jobTitle}>{title}</div>
          <div className={styles.jobCompany}>岗位ID: {record.jobId}</div>
        </div>
      ),
    },
    {
      title: '状态',
      dataIndex: 'status',
      key: 'status',
      width: 100,
      render: (status: ApplicationStatus) => (
        <StatusTag type={statusTagTypeMap[status]}>
          {statusLabelMap[status]}
        </StatusTag>
      ),
    },
    {
      title: '匹配度',
      dataIndex: 'matchScore',
      key: 'matchScore',
      width: 140,
      render: (score: number) => (
        <div className={styles.matchCell}>
          <Progress
            percent={score}
            size="small"
            strokeColor={
              score >= 85 ? '#059669' : score >= 70 ? '#2563EB' : '#D97706'
            }
            trailColor="var(--border)"
            format={() => `${score}%`}
          />
        </div>
      ),
    },
    {
      title: '投递时间',
      dataIndex: 'appliedAt',
      key: 'appliedAt',
      width: 150,
      render: (time: string) => (
        <span className={styles.timeCell}>{formatTime(time)}</span>
      ),
    },
    {
      title: '操作',
      key: 'actions',
      width: 300,
      fixed: 'right',
      render: (_: unknown, record: CandidateListItem) => (
        <Space size="small" wrap>
          <Button
            type="link"
            size="small"
            className={styles.actionBtn}
            onClick={() => handleViewResume(record)}
          >
            <FileTextOutlined /> 查看简历
          </Button>
          <Button
            type="link"
            size="small"
            className={styles.actionBtnPrimary}
            icon={<MessageOutlined />}
            loading={chattingId === record.id}
            onClick={() => handleChat(record)}
          >
            对话
          </Button>
          {canMark(record.status) && (
            <>
              <Button
                type="link"
                size="small"
                className={styles.actionBtnSuccess}
                onClick={() => handleMark(record, 'SUITABLE')}
              >
                <CheckOutlined /> 合适
              </Button>
              <Button
                type="link"
                size="small"
                className={styles.actionBtnDanger}
                onClick={() => handleMark(record, 'UNSUITABLE')}
              >
                <CloseOutlined /> 不合适
              </Button>
            </>
          )}
          {record.status === ApplicationStatus.SCREENED && (
            <Button
              type="link"
              size="small"
              className={styles.actionBtnPrimary}
              onClick={() => handleInviteInterview(record)}
            >
              <CalendarOutlined /> 邀约面试
            </Button>
          )}
          {record.status === ApplicationStatus.OFFERABLE &&
            !isOfferBlocked(record.lastOfferStatus, record.hasOfferRecord) && (
            <Button
              type="link"
              size="small"
              className={styles.actionBtnSuccess}
              icon={<DollarOutlined />}
              onClick={() => handleCreateOffer(record)}
            >
              发起Offer
            </Button>
          )}
        </Space>
      ),
    },
  ];

  const handleReset = () => {
    setStatusFilter(undefined);
    setScoreFilter(undefined);
    setJobFilter(undefined);
    setKeyword('');
    setDebouncedKeyword('');
    setSortBy('matchScore');
    setPage(1);
  };

  // Hero 区当前激活筛选回显
  const activeFilterChips: { key: string; label: string }[] = [];
  if (jobFilter) {
    const j = jobOptions.find((o) => o.value === jobFilter);
    if (j) activeFilterChips.push({ key: 'job', label: j.label });
  }
  if (statusFilter) {
    const s = statusFilterOptions.find((o) => o.value === statusFilter);
    if (s) activeFilterChips.push({ key: 'status', label: s.label });
  }
  if (scoreFilter) {
    activeFilterChips.push({ key: 'score', label: `匹配度 ≥ ${scoreFilter}%` });
  }
  if (debouncedKeyword) {
    activeFilterChips.push({ key: 'keyword', label: `“${debouncedKeyword}”` });
  }

  return (
    <div className={styles.page}>
      {/* Hero 头部 */}
      <div className={styles.hero}>
        <div className={styles.heroDecor} />
        <div className={styles.heroGrid} />
        <div className={styles.heroInner}>
          <div className={styles.heroLeft}>
            <div className={styles.heroTitle}>人才库</div>
            <div className={styles.heroDesc}>浏览和管理所有候选人的投递申请</div>
            {activeFilterChips.length > 0 && (
              <div className={styles.heroChips}>
                {activeFilterChips.map((chip) => (
                  <span key={chip.key} className={styles.heroChip}>{chip.label}</span>
                ))}
                <Button type="link" size="small" className={styles.heroClear} onClick={handleReset}>
                  清除筛选
                </Button>
              </div>
            )}
          </div>
          <div className={styles.heroRight}>
            <div className={styles.heroStat}>
              <span className={styles.heroStatNum}>{total}</span>
              <span className={styles.heroStatLabel}>投递记录</span>
            </div>
            <div className={styles.heroAi}>
              <RadarChartOutlined />
              <span>AI 智能匹配</span>
            </div>
          </div>
        </div>
      </div>

      {/* Top Recommended */}
      <div className={styles.recommendWrap}>
        <div className={styles.recommendHeader}>
          <TrophyOutlined className={styles.trophyIcon} />
          <span>推荐候选人</span>
          <span className={styles.recommendHint}>基于AI匹配算法排序</span>
        </div>
        {top5Loading ? (
          <div className={styles.recommendLoading}>
            <Spin />
          </div>
        ) : top5.length === 0 ? (
          <EmptyState description="暂无推荐候选人" />
        ) : (
          <div className={styles.recommendList}>
            {top5.map((c) => (
              <div key={c.candidateId} className={styles.recommendItem}>
                <div className={styles.rankBadge}>
                  <StarFilled />
                  <span>{c.rank}</span>
                </div>
                <div className={styles.recommendHead}>
                  <div className={styles.recommendAvatar}>
                    {c.candidateName?.slice(0, 1) || '?'}
                  </div>
                  <div className={styles.recommendInfo}>
                    <div className={styles.recommendName}>{c.candidateName}</div>
                    <div className={styles.matchBadge}>匹配 {c.matchScore}%</div>
                  </div>
                </div>
                <div className={styles.recommendActions}>
                  <Button
                    type="link"
                    size="small"
                    className={styles.recommendBtn}
                    icon={<FileTextOutlined />}
                    onClick={() => handleViewTopResume(c)}
                  >
                    查看简历
                  </Button>
                  <Button
                    type="link"
                    size="small"
                    className={styles.recommendBtnPrimary}
                    icon={<MessageOutlined />}
                    loading={chattingId === c.applicationId}
                    onClick={() => handleChatTop(c)}
                  >
                    对话
                  </Button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* Filter & Table */}
      <div className={styles.mainCard}>
        <div className={styles.filterBar}>
          <Select
            placeholder="投递状态"
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
            placeholder="最低匹配度"
            value={scoreFilter}
            onChange={(v) => {
              setScoreFilter(v);
              setPage(1);
            }}
            className={styles.filterSelectNarrow}
            allowClear
            options={[
              { value: 90, label: '>= 90%' },
              { value: 80, label: '>= 80%' },
              { value: 70, label: '>= 70%' },
              { value: 60, label: '>= 60%' },
            ]}
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
          <Input
            placeholder="搜索候选人姓名..."
            prefix={<SearchOutlined />}
            value={keyword}
            onChange={(e) => setKeyword(e.target.value)}
            className={styles.filterInput}
            allowClear
          />
          <Select
            placeholder="排序"
            value={sortBy}
            onChange={(v) => {
              setSortBy(v);
              setPage(1);
            }}
            className={styles.filterSelectNarrow}
            options={[...sortOptions]}
          />
          <Button
            icon={<ReloadOutlined />}
            onClick={handleReset}
            className={styles.resetBtn}
          >
            重置
          </Button>
        </div>

        <Table
          columns={columns}
          dataSource={list}
          rowKey="id"
          loading={loading}
          className={styles.table}
          pagination={{
            current: page,
            pageSize,
            total,
            showSizeChanger: true,
            pageSizeOptions: [10, 20, 50],
            showTotal: (t) => `共 ${t} 条投递记录`,
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
          locale={{ emptyText: '暂无投递记录' }}
        />
      </div>

      {/* 简历查看 Modal */}
      <Modal
        open={resumeVisible}
        onCancel={() => setResumeVisible(false)}
        width={760}
        footer={null}
        title={
          resumeTarget
            ? `${resumeTarget.candidateName}${resumeTarget.jobTitle ? ` — ${resumeTarget.jobTitle}` : ''} 的简历`
            : '候选人简历'
        }
        styles={{ body: { maxHeight: '62vh', overflowY: 'auto' } }}
      >
        {resumeLoading ? (
          <div className={styles.resumeLoading}>
            <Spin size="large" />
          </div>
        ) : resumeError || !resumeDetail ? (
          <EmptyState description={resumeError ? '简历加载失败，请重试' : '暂无简历数据'} />
        ) : (
          <ResumeViewer detail={resumeDetail} />
        )}
      </Modal>

      {/* 邀约面试 Modal */}
      <Modal
        title={createTarget ? `邀约面试 — ${createTarget.candidateName}` : '邀约面试'}
        open={createOpen}
        onCancel={() => setCreateOpen(false)}
        onOk={handleCreateSubmit}
        confirmLoading={createSubmitting}
        okText="创建面试"
        cancelText="取消"
        width={560}
      >
        <Form form={createForm} layout="vertical" initialValues={{ method: 'ONLINE' }}>
          <Form.Item
            name="interviewerId"
            label="面试官"
            rules={[{ required: true, message: '请选择面试官' }]}
          >
            <Select placeholder="请选择面试官" options={interviewers} />
          </Form.Item>
          <Form.Item
            name="scheduledAt"
            label="面试时间"
            rules={[{ required: true, message: '请选择面试时间' }]}
          >
            <DatePicker
              showTime
              style={{ width: '100%' }}
              placeholder="选择面试时间"
              format="YYYY-MM-DD HH:mm"
            />
          </Form.Item>
          <Form.Item
            name="method"
            label="面试方式"
            rules={[{ required: true, message: '请选择面试方式' }]}
          >
            <Select
              options={[
                { value: 'OFFLINE', label: '线下面试' },
                { value: 'ONLINE', label: '视频面试' },
                { value: 'PHONE', label: '电话面试' },
              ]}
            />
          </Form.Item>
          <Form.Item name="location" label="地点 / 视频链接">
            <Input placeholder="面试地点或视频会议链接" />
          </Form.Item>
          <Form.Item name="remark" label="备注（HR 内部）">
            <Input.TextArea rows={2} placeholder="内部备注，候选人不可见" />
          </Form.Item>
          <Form.Item name="candidateNote" label="给候选人留言">
            <Input.TextArea rows={2} placeholder="将随面试邀请发送给候选人" />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  );
};

export default CandidatePage;
