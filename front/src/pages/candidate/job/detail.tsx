import React, { useState, useEffect, useCallback } from 'react';
import { useParams, useNavigate } from 'umi';
import { Button, Tag, Result, Tooltip, Empty, Skeleton, message } from 'antd';
import {
  ArrowLeftOutlined,
  EnvironmentOutlined,
  CalendarOutlined,
  UserOutlined,
  BookOutlined,
  DollarOutlined,
  StarOutlined,
  StarFilled,
  MessageOutlined,
} from '@ant-design/icons';
import { getCandidateJobDetail } from '@/services/job';
import type { CandidateJobDetail as JobDetail } from '@/services/job';
import { submitApplication, getApplicationList } from '@/services/application';
import { getConversations, createConversation, sendMessage } from '@/services/message';
import { useJobFavorites } from '@/hooks/useJobFavorites';
import useUserStore from '@/stores/userStore';
import useMessageStore from '@/stores/messageStore';
import { getErrorCode } from '@/utils/apiError';
import { ROUTES } from '@/constants/routes';
import { ApplicationStatus } from '@/constants/enums';
import styles from './detail.less';

/** 进行中的投递状态——仅这些状态视为"已投递"，终态（撤回/淘汰/拒Offer）可重新投递 */
const ACTIVE_APPLICATION_STATUSES: string[] = [
  ApplicationStatus.SUBMITTED,
  ApplicationStatus.VIEWED,
  ApplicationStatus.SCREENED,
  ApplicationStatus.INTERVIEWING,
  ApplicationStatus.OFFERABLE,
  ApplicationStatus.OFFERED,
  ApplicationStatus.OFFER_ACCEPTED,
];

/** 学历编码 → 展示（覆盖后端 7 档，未知编码兜底原始值） */
const educationLabelMap: Record<string, string> = {
  NONE: '学历不限',
  JUNIOR_HIGH: '初中',
  HIGH_SCHOOL: '高中',
  ASSOCIATE: '大专',
  BACHELOR: '本科',
  MASTER: '硕士',
  DOCTOR: '博士',
};

/** 分 → k/月 展示（非 negotiable 且金额 null → 面议） */
const fmtSalary = (salary: JobDetail['salary']) => {
  if (
    salary.negotiable ||
    salary.minAmount === null ||
    salary.maxAmount === null
  ) {
    return '薪资面议';
  }
  const minK = (salary.minAmount / 100000).toFixed(1).replace('.0', '');
  const maxK = (salary.maxAmount / 100000).toFixed(1).replace('.0', '');
  const suffix = salary.months && salary.months > 12 ? `·${salary.months}薪` : '';
  return `${minK}k-${maxK}k${suffix}`;
};

const JobDetailPage: React.FC = () => {
  const navigate = useNavigate();
  const { jobId } = useParams<{ jobId: string }>();
  const userInfo = useUserStore((s) => s.userInfo);
  const isLogin = useUserStore((s) => s.isLogin);
  // 收藏：仅已登录候选人启用 Hook（app.tsx 守卫下未登录到不了本页，防御性判断）
  const isCandidate = isLogin && userInfo?.role === 'CANDIDATE';
  const { favoriteIds, idsLoading, togglingIds, toggleFavorite } = useJobFavorites(isCandidate);

  const [job, setJob] = useState<JobDetail | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);
  const [notFound, setNotFound] = useState(false);
  const [notFoundMsg, setNotFoundMsg] = useState('');
  const [submitting, setSubmitting] = useState(false);
  const [alreadyApplied, setAlreadyApplied] = useState(false);
  const [consulting, setConsulting] = useState(false);

  const fetchDetail = useCallback(async () => {
    // jobId 空守卫 + 非数字兜底（后端 HTTP 400）
    if (!jobId || !/^\d+$/.test(jobId)) {
      message.warning('岗位参数无效');
      navigate(ROUTES.CANDIDATE_JOB);
      return;
    }
    setLoading(true);
    setLoadError(false);
    setNotFound(false);
    try {
      const detail = await getCandidateJobDetail(Number(jobId));
      setJob(detail);
    } catch (error) {
      if (getErrorCode(error) === 2101) {
        // 非 PUBLISHED 详情 → 后端消息「岗位不存在或无权访问」
        setNotFound(true);
        setNotFoundMsg((error as Error).message || '岗位不存在或无权访问');
      } else {
        setLoadError(true);
      }
    } finally {
      setLoading(false);
    }
  }, [jobId, navigate]);

  useEffect(() => {
    fetchDetail();
  }, [fetchDetail]);

  // 已登录候选人：投递成功后 / 页面加载时检查是否已投递过该岗位
  useEffect(() => {
    if (!isCandidate || !job) return;
    let cancelled = false;
    getApplicationList({ page: 1, pageSize: 100 })
      .then((list) => {
        if (cancelled) return;
        const has = list.list.some(
          (item) => item.jobId === Number(jobId) && ACTIVE_APPLICATION_STATUSES.includes(item.status),
        );
        setAlreadyApplied(has);
      })
      .catch(() => {
        // 获取投递列表失败忽略，不影响页面主流程
      });
    return () => { cancelled = true; };
  }, [isCandidate, job, jobId]);

  const handleToggleFavorite = (target: boolean) => {
    toggleFavorite(Number(jobId), target).then((ok) => {
      if (ok) message.success(target ? '已收藏岗位' : '已取消收藏');
    });
  };

  const handleApply = async () => {
    setSubmitting(true);
    try {
      await submitApplication({ jobId: Number(jobId) });
      message.success('投递成功！');
      setAlreadyApplied(true);
    } catch (error) {
      const code = getErrorCode(error);
      if (code === 3007) {
        message.warning('请先上传简历后再投递');
      } else if (code === 3003) {
        message.warning('简历正在解析中，请稍后再试');
      } else if (code === 3202) {
        message.warning('您已投递过该岗位');
        setAlreadyApplied(true);
      } else if (code === 3008) {
        message.warning('该岗位已停止招聘');
      } else if (code === 3101) {
        message.error('简历数据异常，请联系客服');
      } else {
        message.error('投递失败，请稍后重试');
      }
    } finally {
      setSubmitting(false);
    }
  };

  // 咨询HR
  const handleConsultHR = async () => {
    if (!isLogin) {
      navigate(ROUTES.LOGIN);
      return;
    }

    if (!job) return;

    setConsulting(true);
    try {
      // 1. 获取现有会话列表，检查是否已有与该岗位HR的会话
      const conversations = await getConversations();
      const existingConv = conversations.find(
        (conv) => conv.targetUser.role === 'HR' && conv.targetUser.company === job.companyName
      );

      if (existingConv) {
        // 2a. 已有会话，直接跳转（不发送消息）
        navigate(`${ROUTES.CANDIDATE_MESSAGE}?conversationId=${existingConv.id}`);
      } else {
        // 2b. 没有会话，创建新会话并发送初始消息
        const conversationId = await createConversation({
          companyId: 0, // 后端会根据岗位信息自动关联
          candidateId: userInfo?.id || 0,
          hrId: 0, // 后端会根据岗位信息自动关联HR
        });

        // 发送咨询消息
        await sendMessage(conversationId, {
          contentType: 'TEXT',
          content: `你好，我想咨询一下「${job.title}」这个岗位的相关信息。`,
        });

        // 更新消息store
        const { setConversations } = useMessageStore.getState();
        const updatedConversations = await getConversations();
        setConversations(updatedConversations);

        // 跳转到消息页面
        message.success('已创建会话，正在跳转...');
        navigate(`${ROUTES.CANDIDATE_MESSAGE}?conversationId=${conversationId}`);
      }
    } catch (error) {
      console.error('咨询HR失败:', error);
      message.error('咨询失败，请稍后重试');
    } finally {
      setConsulting(false);
    }
  };

  if (loading) {
    return (
      <div className={styles.page}>
        <Skeleton active paragraph={{ rows: 6 }} />
      </div>
    );
  }

  if (notFound) {
    return (
      <div className={styles.page}>
        <button type="button" className={styles.backBtn} onClick={() => navigate(ROUTES.CANDIDATE_JOB)}>
          <ArrowLeftOutlined /> 返回岗位列表
        </button>
        <Result
          status="404"
          title={notFoundMsg}
          subTitle="该岗位可能已关闭、暂停或不存在"
          extra={
            <Button type="primary" onClick={() => navigate(ROUTES.CANDIDATE_JOB)}>
              浏览其他岗位
            </Button>
          }
        />
      </div>
    );
  }

  if (loadError || !job) {
    return (
      <div className={styles.page}>
        <Empty description="岗位加载失败">
          <Button type="primary" onClick={fetchDetail}>重新加载</Button>
        </Empty>
      </div>
    );
  }

  const coreSkillNames = (job.profile?.coreSkills || []).map((s) => s.name);
  // 企业与招聘负责人展示文本（缺省回退"招聘企业"/"招聘负责人"，禁止出现 `-`）
  const companyName = job.companyName || '招聘企业';
  const creatorName = job.creatorName || '招聘负责人';
  const creatorText = creatorName === '招聘负责人'
    ? '招聘负责人'
    : `招聘负责人：${creatorName}`;
  // 收藏态（仅正常渲染分支使用；jobId 已在上方校验为数字）
  const currentJobId = Number(jobId);
  const favorited = favoriteIds.has(currentJobId);
  const favLoading = idsLoading || togglingIds.has(currentJobId);

  return (
    <div className={styles.page}>
      <button type="button" className={styles.backBtn} onClick={() => navigate(ROUTES.CANDIDATE_JOB)}>
        <ArrowLeftOutlined /> 返回岗位列表
      </button>

      <div className={styles.headerRow}>
        <div className={styles.titleArea}>
          <h1 className={styles.jobTitle}>{job.title}</h1>
          <p className={styles.companyName} title={companyName}>{companyName}</p>
          <p className={styles.creatorName} title={creatorText}>
            {creatorText}
          </p>
        </div>
        <Tag color="green" className={styles.statusTag}>招聘中</Tag>
      </div>

      <div className={styles.content}>
        <div className={styles.leftCol}>
          {/* 岗位描述 */}
          <div className={styles.card}>
            <div className={styles.cardTitle}>岗位描述</div>
            <div className={styles.jdText}>{job.jdText}</div>
          </div>

          {/* 技能要求（D13：详情无顶层 skillTags，用脱敏画像 coreSkills） */}
          <div className={styles.card}>
            <div className={styles.cardTitle}>技能要求</div>
            <div className={styles.skillTags}>
              {coreSkillNames.map((name) => (
                <span key={name} className={styles.skillTag}>{name}</span>
              ))}
            </div>
          </div>

          {/* 岗位信息 */}
          <div className={styles.card}>
            <div className={styles.cardTitle}>岗位信息</div>
            <div className={styles.metaList}>
              <div className={styles.metaItem}>
                <EnvironmentOutlined />
                <span className={styles.metaLabel}>工作地点</span>
                <span>{job.cityName}</span>
              </div>
              <div className={styles.metaItem}>
                <UserOutlined />
                <span className={styles.metaLabel}>经验要求</span>
                <span>{job.minExperienceYears > 0 ? `${job.minExperienceYears}年以上` : '不限'}</span>
              </div>
              <div className={styles.metaItem}>
                <BookOutlined />
                <span className={styles.metaLabel}>学历要求</span>
                <span>{educationLabelMap[job.educationRequirement] || job.educationRequirement}</span>
              </div>
              <div className={styles.metaItem}>
                <DollarOutlined />
                <span className={styles.metaLabel}>薪资范围</span>
                <span>{fmtSalary(job.salary)}</span>
              </div>
              <div className={styles.metaItem}>
                <CalendarOutlined />
                <span className={styles.metaLabel}>发布时间</span>
                <span>{job.publishedAt ? job.publishedAt.replace('T', ' ').substring(0, 10) : '-'}</span>
              </div>
            </div>
          </div>

          {/* 岗位画像（脱敏字段） */}
          <div className={styles.card}>
            <div className={styles.cardTitle}>岗位画像</div>
            <div className={styles.metaList}>
              <div className={styles.metaItem}>
                <span className={styles.metaLabel}>岗位类型</span>
                <span>{job.profile?.jobType || '-'}</span>
              </div>
              {job.profile?.industryExperience && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>行业经验</span>
                  <span>{job.profile.industryExperience}</span>
                </div>
              )}
              {job.profile?.interviewFocus && job.profile.interviewFocus.length > 0 && (
                <div className={styles.metaItem}>
                  <span className={styles.metaLabel}>面试考察</span>
                  <span>{job.profile.interviewFocus.join('、')}</span>
                </div>
              )}
            </div>
          </div>
        </div>

        <div className={styles.rightCol}>
          {/* 收藏按钮（仅已登录候选人） */}
          {isCandidate && (
            <Button
              className={styles.favBtn}
              icon={favorited ? <StarFilled /> : <StarOutlined />}
              loading={favLoading}
              onClick={() => handleToggleFavorite(!favorited)}
            >
              {favorited ? '已收藏' : '收藏岗位'}
            </Button>
          )}

          {/* 匹配面板禁用（D4：后端阶段3 无 match 接口，不假成功） */}
          <div className={styles.matchPanel}>
            <div className={styles.matchLabel}>AI 能力匹配暂未开放</div>
          </div>

          <div className={styles.bottomActions}>
            <Button
              size="large"
              className={styles.consultBtn}
              icon={<MessageOutlined />}
              loading={consulting}
              onClick={handleConsultHR}
            >
              咨询 HR
            </Button>
            {!isLogin ? (
              <Button
                type="primary"
                size="large"
                className={styles.actionBtn}
                onClick={() => navigate(ROUTES.LOGIN)}
              >
                登录后投递
              </Button>
            ) : alreadyApplied ? (
              <Tooltip title="您已投递过该岗位，可在投递进度中查看">
                <Button type="primary" size="large" className={styles.actionBtn} disabled>
                  已投递
                </Button>
              </Tooltip>
            ) : (
              <Button
                type="primary"
                size="large"
                className={styles.actionBtn}
                loading={submitting}
                onClick={handleApply}
              >
                立即投递
              </Button>
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default JobDetailPage;
