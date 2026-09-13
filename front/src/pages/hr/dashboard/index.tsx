/**
 * HR 工作台（/hr/dashboard）
 * 无独立统计接口：并行聚合候选人/面试/Offer/岗位等既有列表接口，
 * 本地计算统计卡片、近7天投递趋势、招聘漏斗与待办事项；各接口独立降级。
 */
import React, { useState, useEffect } from 'react';
import {
  FileTextOutlined,
  ClockCircleOutlined,
  CalendarOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import { Card, List, Button } from 'antd';
import PageHero from '@/components/PageHero';
import StatCard from '@/components/StatCard';
import { useAuth } from '@/hooks/useAuth';
import {
  getCandidateList,
  getInterviewList,
  getOfferList,
  getPendingEvaluations,
} from '@/services/hr';
import { getHrJobs } from '@/services/job';
import type { HrJobListItem } from '@/services/job';
import type { CandidateListItem } from '@/constants/apiTypes';
import { ApplicationStatus, JobStatus, OfferStatus } from '@/constants/enums';
import styles from './index.less';

/** 日期按键（按本地日期归桶） */
const dayKey = (d: Date) => `${d.getFullYear()}-${d.getMonth() + 1}-${d.getDate()}`;

/** 相对时间（用于待办事项） */
const timeAgo = (iso?: string): string => {
  if (!iso) return '';
  const diff = Date.now() - new Date(iso).getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min}分钟前`;
  const hour = Math.floor(min / 60);
  if (hour < 24) return `${hour}小时前`;
  return `${Math.floor(hour / 24)}天前`;
};

/** 近7天（含今天）投递趋势，从候选人 appliedAt 聚合（最多取最新 50 条，仅供参考） */
const buildTrend = (items: CandidateListItem[]) => {
  const days = Array.from({ length: 7 }, (_, i) => {
    const d = new Date();
    d.setHours(0, 0, 0, 0);
    d.setDate(d.getDate() - (6 - i));
    return d;
  });
  const counts = days.map(() => 0);
  const idxByKey = new Map(days.map((d, i) => [dayKey(d), i]));
  items.forEach((c) => {
    if (!c.appliedAt) return;
    const idx = idxByKey.get(dayKey(new Date(c.appliedAt)));
    if (idx !== undefined) counts[idx]++;
  });
  return days.map((d, i) => ({
    day: `${d.getMonth() + 1}-${String(d.getDate()).padStart(2, '0')}`,
    count: counts[i],
  }));
};

interface TodoItem {
  id: string;
  title: string;
  desc: string;
  time: string;
  type: 'review' | 'interview' | 'offer';
}

const funnelColors = ['var(--accent)', 'var(--accent-2)', 'var(--accent-3)', '#059669'];

const DashboardPage: React.FC = () => {
  const { userInfo } = useAuth();

  // 统计数据卡
  const [stats, setStats] = useState({ todayApplications: 0, pendingScreening: 0, weekInterviews: 0, monthOffers: 0 });
  const [trendChange, setTrendChange] = useState<{ text: string; up: boolean } | null>(null);
  // 投递趋势 / 招聘漏斗 / 待办
  const [trendData, setTrendData] = useState<{ day: string; count: number }[]>([]);
  const [funnelStages, setFunnelStages] = useState<{ label: string; count: number; color: string }[]>([]);
  const [pendingTasks, setPendingTasks] = useState<TodoItem[]>([]);
  const [statsLoading, setStatsLoading] = useState(true);

  // 在招岗位（真实接口；后端 size 限制 1~50）
  const [jobs, setJobs] = useState<HrJobListItem[]>([]);
  const [jobsLoading, setJobsLoading] = useState(false);

  useEffect(() => {
    let active = true;
    setStatsLoading(true);

    // 各接口独立降级，避免单个接口失败拖垮整页
    const safePage = (p: Promise<{ list: unknown[]; total: number }>) =>
      p.catch(() => ({ list: [], total: 0 }));
    const safeList = <T,>(p: Promise<T[]>, fallback: T[] = [] as T[]) =>
      p.catch(() => fallback);

    Promise.all([
      // 全部投递（total 供漏斗「投递」；list 按提交时间倒序取最新 50，供今日/趋势聚合）
      safePage(getCandidateList({ sortBy: 'submittedAt', page: 1, size: 50 })),
      // 待筛选（total 供「待处理」，list 供待办）
      safePage(getCandidateList({ status: ApplicationStatus.SUBMITTED, page: 1, size: 5 })),
      // 漏斗各阶段当前人数
      safePage(getCandidateList({ status: ApplicationStatus.SCREENED, page: 1, size: 1 })),
      safePage(getCandidateList({ status: ApplicationStatus.INTERVIEWING, page: 1, size: 1 })),
      safePage(getCandidateList({ status: ApplicationStatus.OFFER_ACCEPTED, page: 1, size: 1 })),
      // 本周面试（后端 WEEK = 近7天，含各状态）
      safePage(getInterviewList({ dateRange: 'WEEK', page: 1, size: 1 })),
      // 本月录用 Offer
      safePage(getOfferList({ status: OfferStatus.ACCEPTED, dateRange: 'MONTH', page: 1, size: 1 })),
      // 待办：待评估面试
      safeList(getPendingEvaluations()),
      // 待办：待确认 Offer
      safePage(getOfferList({ status: OfferStatus.SENT, page: 1, size: 5 })),
    ]).then(
      ([
        recent,
        submitted,
        screened,
        interviewing,
        accepted,
        weekInterviews,
        monthOffers,
        pendingEvals,
        sentOffers,
      ]) => {
        if (!active) return;

        // 今日投递 + 趋势
        const trend = buildTrend(recent.list as CandidateListItem[]);
        const todayCount = trend[trend.length - 1]?.count ?? 0;
        const yesterdayCount = trend[trend.length - 2]?.count ?? 0;
        if (yesterdayCount > 0) {
          const pct = Math.round(((todayCount - yesterdayCount) / yesterdayCount) * 100);
          setTrendChange({ text: `${pct >= 0 ? '+' : ''}${pct}% 较昨日`, up: pct >= 0 });
        } else {
          setTrendChange(null);
        }

        setStats({
          todayApplications: todayCount,
          pendingScreening: submitted.total,
          weekInterviews: weekInterviews.total,
          monthOffers: monthOffers.total,
        });
        setTrendData(trend);

        // 招聘漏斗（各阶段当前人数）
        setFunnelStages([
          { label: '投递', count: recent.total, color: funnelColors[0] },
          { label: '筛选通过', count: screened.total, color: funnelColors[1] },
          { label: '面试中', count: interviewing.total, color: funnelColors[2] },
          { label: '已录用', count: accepted.total, color: funnelColors[3] },
        ]);

        // 待办事项（真实来源组合）
        const todos: TodoItem[] = [
          ...(pendingEvals as { interviewId: string; candidateName: string; jobTitle: string; scheduledAt: string; isOverdue: boolean }[]).map((e) => ({
            id: `eval-${e.interviewId}`,
            title: `${e.candidateName} 的面试待评估`,
            desc: `${e.jobTitle}${e.isOverdue ? ' · 已超24h' : ''}`,
            time: timeAgo(e.scheduledAt),
            type: 'interview' as const,
          })),
          ...(submitted.list as CandidateListItem[]).map((c) => ({
            id: `screen-${c.id}`,
            title: `${c.candidateName} 的简历待筛选`,
            desc: `投递岗位: ${c.jobTitle}`,
            time: timeAgo(c.appliedAt),
            type: 'review' as const,
          })),
          ...(sentOffers.list as { offerId: string; candidateName: string; jobTitle: string; expiresAt: string }[]).map((o) => ({
            id: `offer-${o.offerId}`,
            title: `${o.candidateName} 的 Offer 待确认`,
            desc: `岗位: ${o.jobTitle} · 等待候选人回复`,
            time: timeAgo(o.expiresAt),
            type: 'offer' as const,
          })),
        ].sort((a, b) => {
          // 超24h 待评估优先，其次按类型稳定排序，其余按时间倒序
          if (a.type === 'interview' && a.desc.includes('超24h')) return -1;
          if (b.type === 'interview' && b.desc.includes('超24h')) return 1;
          return 0;
        });
        setPendingTasks(todos.slice(0, 8));

        setStatsLoading(false);
      },
    );

    return () => {
      active = false;
    };
  }, []);

  // 在招岗位（真实接口；后端 size 限制 1~50）
  useEffect(() => {
    setJobsLoading(true);
    getHrJobs({ status: JobStatus.PUBLISHED, page: 1, size: 50 })
      .then((res) => setJobs(res.list))
      .catch(() => setJobs([]))
      .finally(() => setJobsLoading(false));
  }, []);

  const maxTrend = Math.max(...trendData.map((d) => d.count), 1);

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero
        title="工作台"
        desc={`欢迎回来, ${userInfo?.name || '-'}`}
      />

      {/* Stat Cards */}
      <div className={styles.statGrid}>
        <StatCard
          icon={<FileTextOutlined />}
          label="今日投递"
          value={statsLoading ? '--' : stats.todayApplications}
          change={trendChange?.text}
          changeUp={trendChange?.up}
        />
        <StatCard
          icon={<ClockCircleOutlined />}
          label="待处理"
          value={statsLoading ? '--' : stats.pendingScreening}
        />
        <StatCard
          icon={<CalendarOutlined />}
          label="本周面试"
          value={statsLoading ? '--' : stats.weekInterviews}
        />
        <StatCard
          icon={<CheckCircleOutlined />}
          label="本月录用"
          value={statsLoading ? '--' : stats.monthOffers}
        />
      </div>

      {/* Two-column grid */}
      <div className={styles.twoCol}>
        {/* 投递趋势 */}
        <Card className={styles.panel} title="投递趋势（近7天）" bordered={false} loading={statsLoading}>
          <div className={styles.chartBars}>
            {trendData.map((item) => (
              <div key={item.day} className={styles.barCol}>
                <div className={styles.barLabel}>{item.count}</div>
                <div
                  className={styles.bar}
                  style={{ height: `${(item.count / maxTrend) * 100}%` }}
                />
                <div className={styles.barDay}>{item.day}</div>
              </div>
            ))}
          </div>
        </Card>

        {/* 招聘漏斗 */}
        <Card className={styles.panel} title="招聘漏斗（当前各阶段人数）" bordered={false} loading={statsLoading}>
          <div className={styles.funnel}>
            {funnelStages.map((stage) => {
              const maxCount = Math.max(...funnelStages.map((s) => s.count), 1);
              const widthPercent = Math.round((stage.count / maxCount) * 100);
              return (
                <div key={stage.label} className={styles.funnelRow}>
                  <span className={styles.funnelLabel}>{stage.label}</span>
                  <div className={styles.funnelBarWrap}>
                    <div
                      className={styles.funnelBar}
                      style={{
                        width: `${widthPercent}%`,
                        backgroundColor: stage.color,
                      }}
                    >
                      <span className={styles.funnelCount}>{stage.count}</span>
                    </div>
                  </div>
                </div>
              );
            })}
          </div>
        </Card>
      </div>

      {/* 待办事项 + 岗位概况 */}
      <div className={styles.twoCol}>
        <Card
          className={styles.panel}
          title="待办事项"
          bordered={false}
          loading={statsLoading}
          extra={
            <Button type="link" className={styles.linkBtn}>
              全部
            </Button>
          }
        >
          <List
            dataSource={pendingTasks}
            locale={{ emptyText: '暂无待办事项' }}
            renderItem={(item) => (
              <List.Item className={styles.taskItem}>
                <div className={styles.taskContent}>
                  <div className={styles.taskDot} />
                  <div>
                    <div className={styles.taskTitle}>{item.title}</div>
                    <div className={styles.taskDesc}>{item.desc}</div>
                  </div>
                </div>
                <span className={styles.taskTime}>{item.time}</span>
              </List.Item>
            )}
            split={false}
          />
        </Card>

        <Card className={styles.panel} title="在招岗位" bordered={false} loading={jobsLoading}>
          <div className={styles.jobList}>
            {jobs.length === 0 ? (
              <div className={styles.jobListInfo}>暂无在招岗位</div>
            ) : (
              jobs.map((job) => (
                <div key={job.jobId} className={styles.jobListItem}>
                  <div className={styles.jobListInfo}>
                    <div className={styles.jobListTitle}>{job.title}</div>
                    <div className={styles.jobListMeta}>
                      {job.cityName} / {salaryText(job)}
                    </div>
                  </div>
                  <div className={styles.jobListHC}>
                    HC: {job.confirmedHc}/{job.totalHc}
                  </div>
                </div>
              ))
            )}
          </div>
        </Card>
      </div>
    </div>
  );
};

/** 薪资展示（分→K；面议/空值 → 面议） */
const salaryText = (job: HrJobListItem) => {
  const s = job.salary;
  if (!s || s.negotiable) return '薪资面议';
  if (s.minAmount !== null && s.maxAmount !== null) {
    return `${(s.minAmount / 100000).toFixed(1).replace('.0', '')}K-${(s.maxAmount / 100000).toFixed(1).replace('.0', '')}K`;
  }
  return '薪资面议';
};

export default DashboardPage;
