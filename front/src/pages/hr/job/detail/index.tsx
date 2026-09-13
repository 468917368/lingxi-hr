import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useParams } from 'umi';
import {
  Card,
  Descriptions,
  Button,
  Space,
  Tag,
  Divider,
  Modal,
  message,
  Row,
  Col,
  Alert,
  Tooltip,
  Skeleton,
  Empty,
  Timeline,
} from 'antd';
import {
  ArrowLeftOutlined,
  EditOutlined,
  SendOutlined,
  CloseCircleOutlined,
  DeleteOutlined,
  EnvironmentOutlined,
  ClockCircleOutlined,
  BookOutlined,
  DollarOutlined,
  CalendarOutlined,
  InfoCircleOutlined,
} from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import StatusTag from '@/components/StatusTag';
import { getHrJobDetail, changeJobStatus, deleteDraftJob, getJobStatusHistory } from '@/services/job';
import type { HrJobDetail, JobStatusLog } from '@/services/job';
import { getErrorCode } from '@/utils/apiError';
import { JobStatus, EducationRequirement, PauseReason, CloseReason } from '@/constants/enums';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

const statusLabelMap: Record<string, string> = {
  [JobStatus.DRAFT]: '草稿',
  [JobStatus.PUBLISHED]: '招聘中',
  [JobStatus.PAUSED]: 'HC已预占满',
  [JobStatus.CLOSED]: '已关闭',
};

const statusTagTypeMap: Record<string, 'info' | 'success' | 'warning' | 'danger' | 'neutral'> = {
  [JobStatus.DRAFT]: 'neutral',
  [JobStatus.PUBLISHED]: 'success',
  [JobStatus.PAUSED]: 'warning',
  [JobStatus.CLOSED]: 'danger',
};

const educationLabelMap: Record<string, string> = {
  [EducationRequirement.NONE]: '学历不限',
  [EducationRequirement.COLLEGE]: '大专',
  [EducationRequirement.BACHELOR]: '本科',
  [EducationRequirement.MASTER]: '硕士',
  [EducationRequirement.DOCTOR]: '博士',
  // 兜底：解析响应 LLM 可能返回 PHD（后端岗位保存枚举为 DOCTOR），FE3-001
  PHD: '博士',
};

const closeReasonMap: Record<string, string> = {
  [CloseReason.MANUAL]: 'HR手动关闭',
  [CloseReason.VIOLATION]: '违规关闭',
  [CloseReason.EXPIRED]: '已到期自动关闭',
  [CloseReason.HC_CONFIRMED_FULL]: '正式录用人数已满',
};

/** 状态变更原因 → 文案（未知 reason 兜底原始值，不留白） */
const statusLogReasonMap: Record<string, string> = {
  MANUAL_PUBLISH: '发布岗位',
  MANUAL: '手动关闭',
  REOPEN: '重新开放',
  VIOLATION: '管理员违规下架',
  EXPIRED: '岗位到期关闭',
  HC_RESERVED_FULL: 'HC 预占已满，自动暂停',
  HC_CONFIRMED_FULL: 'HC 已满，自动关闭',
  REJECTED: '候选人拒绝，名额释放',
  NOT_ONBOARDED: '候选人未入职，名额释放',
  D_PERSIST_FAILED: '投递处理失败，名额释放',
};

/** 操作者角色 → 文案（未知角色兜底原始值） */
const operatorRoleMap: Record<string, string> = {
  SYSTEM: '系统',
  ADMIN: '管理员',
  HR: 'HR',
};

/** 状态历史 Timeline 节点色（按目标状态） */
const timelineColorMap: Record<string, string> = {
  [JobStatus.PUBLISHED]: 'green',
  [JobStatus.PAUSED]: 'orange',
  [JobStatus.CLOSED]: 'red',
};

// 分 → 元展示
const fmtSalaryYuan = (amount: number | null) =>
  amount === null ? '-' : `${(amount / 100).toLocaleString()} 元/月`;
const fmtTime = (iso?: string) => {
  if (!iso) return '-';
  return iso.replace('T', ' ').substring(0, 16);
};

const JobDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const params = useParams<{ jobId: string }>();
  const jobId = params.jobId;

  const [job, setJob] = useState<HrJobDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);

  // ===== 状态历史（F-25：只取最近 50 条，不分页） =====
  const [history, setHistory] = useState<JobStatusLog[]>([]);
  const [historyTotal, setHistoryTotal] = useState(0);
  const [historyLoading, setHistoryLoading] = useState(false);
  const [historyError, setHistoryError] = useState(false);

  // ===== 数据加载（异步 + jobId 空守卫） =====
  const fetchDetail = useCallback(async () => {
    if (!jobId) {
      message.warning('岗位参数缺失');
      navigate(ROUTES.HR_JOB);
      return;
    }
    setLoading(true);
    setLoadError(false);
    try {
      const detail = await getHrJobDetail(jobId);
      setJob(detail);
    } catch {
      setLoadError(true);
    } finally {
      setLoading(false);
    }
  }, [jobId, navigate]);

  // 状态历史（仅 page=1&size=50 一次；失败置卡片级错误态，不影响详情主体）
  const fetchHistory = useCallback(async () => {
    if (!jobId) return;
    setHistoryLoading(true);
    setHistoryError(false);
    try {
      const res = await getJobStatusHistory(jobId, { page: 1, size: 50 });
      setHistory(res.list);
      setHistoryTotal(res.total);
    } catch {
      setHistoryError(true);
    } finally {
      setHistoryLoading(false);
    }
  }, [jobId]);

  useEffect(() => {
    fetchDetail();
    fetchHistory(); // 与 fetchDetail 并行拉取状态历史
  }, [fetchDetail, fetchHistory]); // fetchHistory 必须进依赖数组

  // HC 说明文案
  const getHcHint = () => {
    if (!job) return '';
    if (job.availableHc > 0) return `当前仍可发放 ${job.availableHc} 个 Offer`;
    if (job.status === JobStatus.PAUSED && job.pauseReason === PauseReason.HC_RESERVED_FULL) {
      return '待确认 Offer 已占满 HC；释放预冻结后系统自动恢复招聘';
    }
    if (job.status === JobStatus.CLOSED && job.closeReason === CloseReason.HC_CONFIRMED_FULL) {
      return '正式录用人数已满；释放确认名额后系统会自动恢复招聘';
    }
    if (job.status === JobStatus.CLOSED) {
      return `岗位已关闭（${closeReasonMap[job.closeReason || ''] || job.closeReason}），不再对求职者公开`;
    }
    return '';
  };

  // ===== 发布（DRAFT 状态；用当前详情同一快照 version+profileVersion） =====
  const handlePublish = () => {
    if (!job) return;
    if (!job.profileConfirmed) {
      message.warning('画像未确认，请先在编辑页确认画像后再发布');
      return;
    }
    Modal.confirm({
      title: '确认发布',
      content: `确定要发布岗位「${job.title}」吗？发布后将对求职者公开。`,
      okText: '确认发布',
      cancelText: '取消',
      onOk: async () => {
        setActionLoading(true);
        try {
          await changeJobStatus(job.jobId, {
            action: 'PUBLISH',
            version: job.version,
            profileVersion: job.profileVersion,
          });
          message.success('岗位发布成功');
          // 详情与状态历史并行刷新（无依赖，省一次串行往返）
          await Promise.all([fetchDetail(), fetchHistory()]);
        } catch (error) {
          if (getErrorCode(error) === 2102) {
            // 版本冲突：重新加载最新详情
            await fetchDetail();
          }
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // 关闭确认（PAUSED 也显示；成功后停留详情重新拉取，不跳列表）
  const handleClose = () => {
    if (!job) return;
    const hasReserved = job.reservedHc > 0;
    Modal.confirm({
      title: '确认关闭岗位',
      content: (
        <div>
          <p>确定要关闭岗位「{job.title}」吗？关闭后候选人将无法投递。</p>
          {hasReserved && (
            <Alert
              type="warning"
              message={`仍有 ${job.reservedHc} 个已发送待确认 Offer，关闭岗位不会自动撤回这些 Offer。`}
              className={styles.alertGap}
            />
          )}
        </div>
      ),
      okText: '确认关闭',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoading(true);
        try {
          await changeJobStatus(job.jobId, { action: 'CLOSE', version: job.version });
          message.success('岗位已关闭');
          // 详情与状态历史并行刷新（无依赖，省一次串行往返）
          await Promise.all([fetchDetail(), fetchHistory()]);
        } catch (error) {
          if (getErrorCode(error) === 2102) {
            await fetchDetail();
          }
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // 删除草稿
  const handleDelete = () => {
    if (!job) return;
    Modal.confirm({
      title: '确认删除草稿',
      content: `确定要删除草稿「${job.title}」吗？此操作不可恢复。`,
      okText: '确认删除',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setActionLoading(true);
        try {
          await deleteDraftJob(job.jobId, job.version);
          message.success('草稿已删除');
          navigate(ROUTES.HR_JOB);
        } catch (error) {
          if (getErrorCode(error) === 2102) {
            await fetchDetail();
          }
        } finally {
          setActionLoading(false);
        }
      },
    });
  };

  // ===== 加载态 / 错误态 =====
  if (loading) {
    return (
      <div className={`${styles.page} hr-page`}>
        <PageHero title="岗位详情" desc="加载中..." />
        <Card bordered={false} className={styles.card}>
          <Skeleton active paragraph={{ rows: 6 }} />
        </Card>
      </div>
    );
  }

  if (loadError || !job) {
    return (
      <div className={`${styles.page} hr-page`}>
        <PageHero title="岗位详情" desc="加载失败" />
        <Empty description="岗位加载失败或不存在">
          <Button type="primary" onClick={fetchDetail}>重新加载</Button>
          <Button className={styles.backBtnGap} onClick={() => navigate(ROUTES.HR_JOB)}>返回列表</Button>
        </Empty>
      </div>
    );
  }

  return (
    <div className={`${styles.page} hr-page`}>
      {/* Header */}
      <div className={styles.header}>
        <div className={styles.headerLeft}>
          <Button
            className={styles.backBtn}
            icon={<ArrowLeftOutlined />}
            onClick={() => navigate(ROUTES.HR_JOB)}
          >
            返回列表
          </Button>
          <div>
            <div className={styles.headerTop}>
              <h1 className={styles.title}>{job.title}</h1>
              <StatusTag type={statusTagTypeMap[job.status]}>
                {statusLabelMap[job.status]}
              </StatusTag>
              {job.closeReason && job.status === JobStatus.CLOSED && (
                <Tag>{closeReasonMap[job.closeReason] || job.closeReason}</Tag>
              )}
            </div>
            <div className={styles.subtitle}>
              {job.cityName} · 更新于 {fmtTime(job.updatedAt)}
              {job.publishedAt && ` · 发布于 ${fmtTime(job.publishedAt)}`}
              {' · '}岗位版本 v{job.version} / 画像版本 v{job.profileVersion}
            </div>
          </div>
        </div>
        <Space>
          {job.status === JobStatus.DRAFT && (
            <Button
              className={styles.outlineBtn}
              icon={<EditOutlined />}
              onClick={() => navigate(`${ROUTES.HR_JOB_EDIT}/${job.jobId}/edit`)}
            >
              编辑
            </Button>
          )}
          {job.status === JobStatus.DRAFT && (
            <Button
              className={styles.outlineBtn}
              icon={<SendOutlined />}
              loading={actionLoading}
              onClick={handlePublish}
            >
              发布
            </Button>
          )}
          {(job.status === JobStatus.PUBLISHED || job.status === JobStatus.PAUSED) && (
            <Button
              className={styles.dangerBtn}
              icon={<CloseCircleOutlined />}
              loading={actionLoading}
              onClick={handleClose}
            >
              关闭
            </Button>
          )}
          {job.status === JobStatus.DRAFT && (
            <Button danger icon={<DeleteOutlined />} loading={actionLoading} onClick={handleDelete}>
              删除草稿
            </Button>
          )}
        </Space>
      </div>

      {/* 状态说明 */}
      {job.pauseReason && (
        <Alert
          type="warning"
          message="待确认 Offer 已占满 HC，释放后系统将自动恢复"
          className={styles.alertBottom}
        />
      )}

      <Row gutter={20}>
        <Col xs={24} lg={16}>
          {/* 基本信息 */}
          <Card className={styles.card} bordered={false}>
            <div className={styles.sectionTitle}>基本信息</div>
            <Descriptions column={2} className={styles.descriptions}>
              <Descriptions.Item label={<><EnvironmentOutlined /> 工作城市</>}>
                {job.cityName}
              </Descriptions.Item>
              <Descriptions.Item label={<><ClockCircleOutlined /> 经验要求</>}>
                {job.minExperienceYears > 0 ? `${job.minExperienceYears} 年以上` : '不限'}
              </Descriptions.Item>
              <Descriptions.Item label={<><BookOutlined /> 学历要求</>}>
                {educationLabelMap[job.educationRequirement] || job.educationRequirement}
              </Descriptions.Item>
              <Descriptions.Item label={<><DollarOutlined /> 薪资范围</>}>
                {job.salary.negotiable ? (
                  <span className={styles.salaryText}>薪资面议</span>
                ) : (
                  <span className={styles.salaryText}>
                    {fmtSalaryYuan(job.salary.minAmount)} - {fmtSalaryYuan(job.salary.maxAmount)}
                    {job.salary.months && job.salary.months > 12 ? ` / ${job.salary.months}薪` : ''}
                  </span>
                )}
              </Descriptions.Item>
              <Descriptions.Item label="招聘人数">
                {job.totalHc} 人
              </Descriptions.Item>
              <Descriptions.Item label={<><CalendarOutlined /> 发布时间</>}>
                {fmtTime(job.publishedAt)}
              </Descriptions.Item>
              <Descriptions.Item label="发布人">
                {job.createdByName || '-'}
              </Descriptions.Item>
              <Descriptions.Item label="岗位ID" span={2}>
                {job.jobId}
              </Descriptions.Item>
            </Descriptions>
          </Card>

          {/* JD */}
          <Card className={`${styles.card} ${styles.cardGap}`} bordered={false}>
            <div className={styles.sectionTitle}>岗位描述 (JD)</div>
            <pre className={styles.jdText}>{job.jdText}</pre>
          </Card>

          {/* HC 明细 */}
          <Card className={`${styles.card} ${styles.cardGap}`} bordered={false}>
            <div className={styles.sectionTitle}>
              Headcount 明细
              <Tooltip title="HC 数据实时反映当前 Offer 流程中的占用情况">
                <InfoCircleOutlined className={styles.hcInfoIcon} />
              </Tooltip>
            </div>
            <div className={styles.hcGrid}>
              <div className={styles.hcItem}>
                <div className={styles.hcValue}>{job.totalHc}</div>
                <div className={styles.hcLabel}>总 HC</div>
              </div>
              <div className={styles.hcDivider} />
              <div className={styles.hcItem}>
                <div className={`${styles.hcValue} ${styles.hcValueBlue}`}>{job.confirmedHc}</div>
                <div className={styles.hcLabel}>已确认</div>
              </div>
              <div className={styles.hcDivider} />
              <div className={styles.hcItem}>
                <div className={`${styles.hcValue} ${styles.hcValueOrange}`}>{job.reservedHc}</div>
                <div className={styles.hcLabel}>预冻结</div>
              </div>
              <div className={styles.hcDivider} />
              <div className={styles.hcItem}>
                <div className={`${styles.hcValue} ${job.availableHc <= 0 ? styles.hcZero : ''}`}>
                  {job.availableHc}
                </div>
                <div className={styles.hcLabel}>可用</div>
              </div>
            </div>
            <div className={styles.hcHint}>{getHcHint()}</div>
          </Card>
        </Col>

        <Col xs={24} lg={8}>
          {/* 岗位画像（顶层平铺字段） */}
          <Card className={styles.card} bordered={false}>
            <div className={styles.sectionTitle}>岗位画像</div>

            {job.coreSkills && job.coreSkills.length > 0 ? (
              <>
                {/* 核心技能 */}
                <div className={styles.profileSection}>
                  <div className={styles.profileLabel}>核心技能</div>
                  <Space wrap size={[0, 8]}>
                    {job.coreSkills.map((skill, i) => (
                      <Tag key={i} color="blue">
                        {skill.name}
                        <span className={styles.skillMeta}>
                          {skill.required ? '必备' : '加分'}
                        </span>
                        {skill.inferred && (
                          <Tooltip title={`AI推断 · 依据: ${skill.basis || '无'} · 置信度: ${((skill.confidence || 0) * 100).toFixed(0)}%`}>
                            <span className={styles.aiHint}>🤖AI推断</span>
                          </Tooltip>
                        )}
                      </Tag>
                    ))}
                  </Space>
                </div>

                {job.softSkills && job.softSkills.length > 0 && (
                  <>
                    <Divider className={styles.dividerGap} />
                    <div className={styles.profileSection}>
                      <div className={styles.profileLabel}>软能力</div>
                      <Space wrap size={[0, 8]}>
                        {job.softSkills.map((s, i) => (
                          <Tag key={i} color="green" className={styles.softTag}>
                            {s.name}
                          </Tag>
                        ))}
                      </Space>
                    </div>
                  </>
                )}

                {job.industryExperience && (
                  <>
                    <Divider className={styles.dividerGap} />
                    <div className={styles.profileSection}>
                      <div className={styles.profileLabel}>行业经验</div>
                      <p className={styles.profileText}>{job.industryExperience}</p>
                    </div>
                  </>
                )}

                {job.hiddenRequirements && job.hiddenRequirements.length > 0 && (
                  <>
                    <Divider className={styles.dividerGap} />
                    <div className={styles.profileSection}>
                      <div className={styles.profileLabel}>隐性要求</div>
                      {job.hiddenRequirements.map((req, i) => (
                        <div key={i} className={styles.hiddenItem}>
                          <p className={styles.profileText}>{req.requirement}</p>
                        </div>
                      ))}
                    </div>
                  </>
                )}

                {job.interviewFocus && job.interviewFocus.length > 0 && (
                  <>
                    <Divider className={styles.dividerGap} />
                    <div className={styles.profileSection}>
                      <div className={styles.profileLabel}>面试考察重点</div>
                      <ul className={styles.profileList}>
                        {job.interviewFocus.map((item, i) => (
                          <li key={i}>{item}</li>
                        ))}
                      </ul>
                    </div>
                  </>
                )}

              </>
            ) : (
              <p className={styles.profileText}>暂无画像数据</p>
            )}
          </Card>
        </Col>
      </Row>

      {/* 状态历史（F-25：只取最近 50 条，不分页） */}
      <Card className={`${styles.card} ${styles.cardGap}`} bordered={false}>
        <div className={styles.sectionTitle}>状态历史</div>

        {historyLoading ? (
          <Skeleton active paragraph={{ rows: 3 }} />
        ) : historyError ? (
          <div className={styles.historyError}>
            <span className={styles.historyErrorText}>状态历史加载失败</span>
            <Button size="small" onClick={fetchHistory}>重试</Button>
          </div>
        ) : historyTotal === 0 ? (
          <Empty description="暂无状态变更记录" image={Empty.PRESENTED_IMAGE_SIMPLE} />
        ) : (
          <>
            <Timeline
              className={styles.historyTimeline}
              mode="left"
              items={history.map((log) => ({
                // 颜色兜底 gray：未知/未来状态走中性色（DRAFT 等无映射时避免 undefined 落默认蓝）
                color: timelineColorMap[log.toStatus] || 'gray',
                label: <span className={styles.historyTime}>{fmtTime(log.createdAt)}</span>,
                children: (
                  <div className={styles.historyItem}>
                    <div className={styles.historyLine}>
                      <span className={styles.historyTransition}>
                        {statusLabelMap[log.fromStatus || ''] || '创建'} →{' '}
                        {statusLabelMap[log.toStatus] || log.toStatus}
                      </span>
                      <Tag className={styles.historyTag}>{statusLogReasonMap[log.reason] || log.reason}</Tag>
                    </div>
                    <div className={styles.historyMeta}>
                      {operatorRoleMap[log.operatorRole] || log.operatorRole}
                      {log.reasonDetail ? ` · 备注：${log.reasonDetail}` : ''}
                    </div>
                  </div>
                ),
              }))}
            />
            {historyTotal > 50 && (
              <div className={styles.historyLimitHint}>仅展示最近 50 条记录</div>
            )}
          </>
        )}
      </Card>
    </div>
  );
};

export default JobDetailPage;
