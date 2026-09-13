/**
 * 面试官「我的面试」（/interviewer/interview）
 * 仅拉取当前面试官的面试列表（interviewerId），本地 Tab 筛选（全部/待面试/进行中/已完成）；
 * 状态驱动操作：待面试可开始/取消，进行中录入评估（提交即 COMPLETED），已完成查看评估。
 */
import React, { useState, useCallback, useEffect } from 'react';
import { Button, Space, Modal, message, Input, Rate, Spin, Segmented, Empty } from 'antd';
import {
  PlayCircleOutlined,
  EditOutlined,
  EyeOutlined,
  CloseOutlined,
  CalendarOutlined,
  UserOutlined,
  VideoCameraOutlined,
  EnvironmentOutlined,
  PhoneOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import { useAuth } from '@/hooks/useAuth';
import {
  getInterviewList,
  startInterview,
  cancelInterview,
  submitEvaluation,
  getInterviewEvaluation,
} from '@/services/hr';
import type { InterviewVO, EvaluationDTO } from '@/constants/apiTypes';
import { InterviewStatus } from '@/constants/enums';
import styles from './index.less';

const { TextArea } = Input;

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

/** 状态徽章配色（圆点 + 文字） */
const statusColorMap: Record<InterviewStatus, string> = {
  [InterviewStatus.PENDING]: '#D97706',
  [InterviewStatus.SCHEDULED]: '#45B7D1',
  [InterviewStatus.IN_PROGRESS]: '#45B7D1',
  [InterviewStatus.COMPLETED]: '#059669',
  [InterviewStatus.CANCELLED]: '#DC2626',
};

/** 拆 ISO 时间为双行：{ date: '08-07', time: '14:00' } */
const splitDateTime = (t?: string): { date: string; time: string } => {
  if (!t) return { date: '-', time: '' };
  const [date, time] = t.replace('T', ' ').split(' ');
  const [, month, day] = date.split('-');
  return { date: `${month}-${day}`, time: time?.slice(0, 5) ?? '' };
};

const emptyEvalForm: EvaluationDTO = {
  conclusion: 'PENDING',
  techScore: 0,
  communicationScore: 0,
  matchScore: 0,
  potentialScore: 0,
  comment: '',
};

const TAB_OPTIONS = ['全部', '待面试', '进行中', '已完成'];

const InterviewPage: React.FC = () => {
  const { userInfo } = useAuth();
  const [activeTab, setActiveTab] = useState('全部');
  const [list, setList] = useState<InterviewVO[]>([]);
  const [loading, setLoading] = useState(false);

  // 评估弹窗
  const [evalTarget, setEvalTarget] = useState<InterviewVO | null>(null);
  const [evalMode, setEvalMode] = useState<'edit' | 'view'>('edit');
  const [evalLoading, setEvalLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [evalForm, setEvalForm] = useState<EvaluationDTO>(emptyEvalForm);

  const fetchList = useCallback(async () => {
    setLoading(true);
    try {
      const data = await getInterviewList({ interviewerId: userInfo?.id, page: 1, size: 50 });
      setList(data.list);
    } catch {
      // 拦截器已提示 body.message
    } finally {
      setLoading(false);
    }
  }, [userInfo?.id]);

  useEffect(() => {
    if (userInfo?.id) fetchList();
  }, [fetchList, userInfo?.id]);

  const handleStart = (record: InterviewVO) => {
    Modal.confirm({
      title: '开始面试',
      content: `确认开始「${record.candidateName}」的面试？`,
      okText: '开始面试',
      cancelText: '取消',
      onOk: async () => {
        await startInterview(record.interviewId);
        message.success('面试已开始');
        fetchList();
      },
    });
  };

  const handleCancel = (record: InterviewVO) => {
    Modal.confirm({
      title: '取消面试',
      content: `确认取消「${record.candidateName}」的面试安排？投递状态将回退。`,
      okText: '确认取消',
      cancelText: '返回',
      okButtonProps: { danger: true },
      onOk: async () => {
        await cancelInterview(record.interviewId);
        message.success('面试已取消');
        fetchList();
      },
    });
  };

  const openEval = async (record: InterviewVO, mode: 'edit' | 'view') => {
    setEvalTarget(record);
    setEvalMode(mode);
    setEvalForm({ ...emptyEvalForm });
    // 回填既有评估/草稿（getInterviewEvaluation 为 silent，无记录不弹 toast，空表单兜底）
    setEvalLoading(true);
    try {
      const detail = await getInterviewEvaluation(record.interviewId);
      setEvalForm({
        conclusion: detail.conclusion,
        techScore: detail.techScore,
        communicationScore: detail.communicationScore,
        matchScore: detail.matchScore,
        potentialScore: detail.potentialScore,
        comment: detail.comment,
      });
    } catch {
      // 无草稿/无评估 → 空表单
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
      fetchList();
    } catch {
      // 4101/4103/4104 已由拦截器提示
    } finally {
      setSubmitting(false);
    }
  };

  const filtered = (() => {
    if (activeTab === '全部') return list;
    const statusMap: Record<string, InterviewStatus[]> = {
      '待面试': [InterviewStatus.PENDING, InterviewStatus.SCHEDULED],
      '进行中': [InterviewStatus.IN_PROGRESS],
      '已完成': [InterviewStatus.COMPLETED],
    };
    return list.filter((d) => statusMap[activeTab]?.includes(d.status));
  })();

  // 顶部概览统计（基于当前全部列表）
  const stats = {
    pending: list.filter((i) => i.status === InterviewStatus.PENDING || i.status === InterviewStatus.SCHEDULED).length,
    inProgress: list.filter((i) => i.status === InterviewStatus.IN_PROGRESS).length,
    completed: list.filter((i) => i.status === InterviewStatus.COMPLETED).length,
  };

  const renderActions = (record: InterviewVO) => {
    const s = record.status;
    return (
      <Space size={8} wrap>
        {(s === InterviewStatus.PENDING || s === InterviewStatus.SCHEDULED) && (
          <>
            <Button type="primary" ghost size="small" className={styles.pillBtn}
              icon={<PlayCircleOutlined />} onClick={() => handleStart(record)}>
              开始面试
            </Button>
            <Button type="text" size="small" danger icon={<CloseOutlined />}
              onClick={() => handleCancel(record)}>
              取消
            </Button>
          </>
        )}
        {s === InterviewStatus.IN_PROGRESS && (
          <Button type="primary" size="small" className={styles.pillBtn}
            icon={<EditOutlined />} onClick={() => openEval(record, 'edit')}>
            录入评估
          </Button>
        )}
        {s === InterviewStatus.COMPLETED && (
          <Button type="text" size="small" className={styles.viewBtn}
            icon={<EyeOutlined />} onClick={() => openEval(record, 'view')}>
            查看评估
          </Button>
        )}
      </Space>
    );
  };

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="我的面试"
        desc={`面试官：${userInfo?.name || '-'}`}
      />

      {/* 概览 */}
      <div className={styles.summaryRow}>
        <div className={styles.summaryCard}>
          <span className={styles.summaryValue} style={{ color: 'var(--text)' }}>{list.length}</span>
          <span className={styles.summaryLabel}>全部面试</span>
        </div>
        <div className={styles.summaryCard}>
          <span className={styles.summaryValue} style={{ color: '#D97706' }}>{stats.pending}</span>
          <span className={styles.summaryLabel}>待面试</span>
        </div>
        <div className={styles.summaryCard}>
          <span className={styles.summaryValue} style={{ color: '#45B7D1' }}>{stats.inProgress}</span>
          <span className={styles.summaryLabel}>进行中</span>
        </div>
        <div className={styles.summaryCard}>
          <span className={styles.summaryValue} style={{ color: '#059669' }}>{stats.completed}</span>
          <span className={styles.summaryLabel}>已完成</span>
        </div>
      </div>

      {/* 筛选切换 */}
      <div className={styles.tabsBar}>
        <Segmented
          options={TAB_OPTIONS}
          value={activeTab}
          onChange={(v) => setActiveTab(v as string)}
          className={styles.segment}
        />
      </div>

      {/* 卡片式面试列表 */}
      {loading ? (
        <div className={styles.loading}><Spin size="large" /></div>
      ) : filtered.length === 0 ? (
        <div className={styles.empty}><Empty description="暂无面试数据" /></div>
      ) : (
        <div className={styles.listWrap}>
          {filtered.map((item, idx) => {
            const { date, time } = splitDateTime(item.scheduledAt);
            const color = statusColorMap[item.status];
            return (
              <div key={item.interviewId} className={styles.interviewCard} style={{ animationDelay: `${idx * 60}ms` }}>
                <div className={styles.cardShell}>
                  <div className={styles.cardInner}>
                    <div className={styles.cardTop}>
                      <div className={styles.candidateCell}>
                        <div className={styles.candidateAvatar}>
                          {item.candidateAvatar ? (
                            <img src={item.candidateAvatar} alt="" />
                          ) : (
                            <span className={styles.candidateInitial}>
                              {item.candidateName?.slice(0, 1) || <UserOutlined />}
                            </span>
                          )}
                        </div>
                        <div className={styles.candidateInfo}>
                          <div className={styles.candidateName}>{item.candidateName}</div>
                          <div className={styles.candidateJob}>{item.jobTitle}</div>
                        </div>
                      </div>
                      <span className={styles.badge} style={{ color, background: `${color}14` }}>
                        <span className={styles.badgeDot} style={{ background: color }} />
                        {item.statusDesc || item.status}
                      </span>
                    </div>

                    <div className={styles.cardBottom}>
                      <div className={styles.metaRow}>
                        <span className={styles.metaItem}>
                          <span className={styles.metaIcon}><CalendarOutlined /></span>
                          <span className={styles.metaTime}>{date}</span>
                          <span className={styles.metaClock}>{time}</span>
                        </span>
                        <span className={styles.metaItem}>
                          <span className={styles.metaIcon}>{methodIconMap[item.method]}</span>
                          {methodLabelMap[item.method] || item.method}
                        </span>
                        {item.location && (
                          <span className={styles.metaItem} title={item.location}>
                            <span className={styles.metaIcon}><EnvironmentOutlined /></span>
                            <span className={styles.metaLocation}>{item.location}</span>
                          </span>
                        )}
                      </div>
                      <div className={styles.actionRow}>{renderActions(item)}</div>
                    </div>
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}

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
                    <span className={styles.metaIcon}>{methodIconMap[evalTarget.method]}</span>
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
