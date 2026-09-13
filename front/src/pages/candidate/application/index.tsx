import React, { useState, useEffect, useRef, useCallback, useMemo } from 'react';
import { Tabs, Timeline, Drawer, Tag, Button, Spin, Modal, Input, message } from 'antd';
import {
  ClockCircleOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  SendOutlined,
} from '@ant-design/icons';
import { useNavigate } from 'umi';
import StatusTag from '@/components/StatusTag';
import PageHeader from '@/components/PageHeader';
import { ROUTES } from '@/constants/routes';
import { ApplicationStatus } from '@/constants/enums';
import {
  getApplicationList,
  getApplicationDetail,
  withdrawApplication,
  acceptOffer,
  declineOffer,
} from '@/services/application';
import type { ApplicationListItem, ApplicationDetail } from '@/services/application';
import { getCandidateJobDetail } from '@/services/job';
import type { CandidateJobDetail } from '@/services/job';
import styles from './index.less';

/** 投递状态中文映射 */
const statusLabelMap: Record<string, string> = {
  [ApplicationStatus.SUBMITTED]: '已投递',
  [ApplicationStatus.VIEWED]: 'HR已查看',
  [ApplicationStatus.SCREENED]: '筛选通过',
  [ApplicationStatus.INTERVIEWING]: '面试中',
  [ApplicationStatus.OFFERABLE]: '可录用',
  [ApplicationStatus.OFFERED]: '待录用',
  [ApplicationStatus.OFFER_ACCEPTED]: '已录用',
  [ApplicationStatus.OFFER_DECLINED]: '已拒绝',
  [ApplicationStatus.REJECTED]: '已淘汰',
  [ApplicationStatus.WITHDRAWN]: '已撤回',
};

const statusTypeMap: Record<string, 'success' | 'warning' | 'danger' | 'info' | 'neutral' | 'primary'> = {
  [ApplicationStatus.SUBMITTED]: 'neutral',
  [ApplicationStatus.VIEWED]: 'info',
  [ApplicationStatus.SCREENED]: 'primary',
  [ApplicationStatus.INTERVIEWING]: 'primary',
  [ApplicationStatus.OFFERABLE]: 'success',
  // 待录用：橙色警示，提醒候选人需要回应 Offer
  [ApplicationStatus.OFFERED]: 'warning',
  [ApplicationStatus.OFFER_ACCEPTED]: 'success',
  [ApplicationStatus.OFFER_DECLINED]: 'neutral',
  [ApplicationStatus.REJECTED]: 'danger',
  [ApplicationStatus.WITHDRAWN]: 'neutral',
};

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
const fmtSalary = (salary: CandidateJobDetail['salary']) => {
  if (salary.negotiable || salary.minAmount === null || salary.maxAmount === null) {
    return '面议';
  }
  const minK = (salary.minAmount / 100000).toFixed(1).replace('.0', '');
  const maxK = (salary.maxAmount / 100000).toFixed(1).replace('.0', '');
  const suffix = salary.months && salary.months > 12 ? `·${salary.months}薪` : '';
  return `${minK}-${maxK}K·${salary.period === 'MONTH' ? '月' : '年'}${suffix}`;
};

/** 单页条数（个人投递量小，一次拉足避免前端筛选偏差） */
const PAGE_SIZE = 20;

/** 时间格式化：ISO → YYYY-MM-DD HH:mm */
const fmtTime = (t?: string | null) => (t ? t.replace('T', ' ').slice(0, 16) : '');

/** JD 折叠预览长度 */
const JD_PREVIEW_LEN = 200;

const ApplicationPage: React.FC = () => {
  const navigate = useNavigate();
  const isMountedRef = useRef(true);

  const [activeTab, setActiveTab] = useState('all');
  const [items, setItems] = useState<ApplicationListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [listLoading, setListLoading] = useState(true);
  const [listError, setListError] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);
  const pageRef = useRef(1);

  // 详情（点击卡片打开 Drawer）
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [detail, setDetail] = useState<ApplicationDetail | null>(null);
  const [detailLoading, setDetailLoading] = useState(false);
  // 岗位发布信息（job 模块公开接口）
  const [jobDetail, setJobDetail] = useState<CandidateJobDetail | null>(null);
  const [jobLoading, setJobLoading] = useState(false);
  const [jobError, setJobError] = useState(false);
  const [jdExpanded, setJdExpanded] = useState(false);
  const [withdrawing, setWithdrawing] = useState(false);
  const [acceptingOffer, setAcceptingOffer] = useState(false);
  const [decliningOffer, setDecliningOffer] = useState(false);
  /** 当前 Drawer 打开投递的岗位 ID（岗位加载失败重试用） */
  const drawerJobIdRef = useRef(0);

  /** 加载投递列表（reset=true 回到第 1 页，false 追加下一页） */
  const fetchList = useCallback(async (reset: boolean) => {
    if (reset) {
      setListLoading(true);
    } else {
      setLoadingMore(true);
    }
    setListError(false);
    try {
      const target = reset ? 1 : pageRef.current + 1;
      const data = await getApplicationList({ page: target, pageSize: PAGE_SIZE });
      if (!isMountedRef.current) return;
      setItems((prev) => (reset ? data.list : [...prev, ...data.list]));
      setTotal(data.total);
      pageRef.current = target;
    } catch {
      if (isMountedRef.current) setListError(true);
    } finally {
      if (isMountedRef.current) {
        setListLoading(false);
        setLoadingMore(false);
      }
    }
  }, []);

  useEffect(() => {
    isMountedRef.current = true;
    fetchList(true);
    return () => {
      isMountedRef.current = false;
    };
  }, [fetchList]);

  /** 加载岗位发布信息（job 模块公开接口；失败独立标记，不影响投递时间线） */
  const fetchJobDetail = useCallback(async (jobId: number) => {
    setJobLoading(true);
    setJobError(false);
    try {
      const data = await getCandidateJobDetail(jobId);
      if (isMountedRef.current) setJobDetail(data);
    } catch {
      if (isMountedRef.current) setJobError(true);
    } finally {
      if (isMountedRef.current) setJobLoading(false);
    }
  }, []);

  /** Tab 切换：重置列表并从第 1 页加载，关闭 Drawer */
  const handleTabChange = (key: string) => {
    setActiveTab(key);
    closeDrawer();
    fetchList(true);
  };

  /** 关闭 Drawer：清空详情与岗位信息 */
  const closeDrawer = () => {
    setSelectedId(null);
    setDetail(null);
    setJobDetail(null);
    setJdExpanded(false);
    drawerJobIdRef.current = 0;
  };

  /** 点击卡片：打开 Drawer，并行加载投递详情 + 岗位发布信息 */
  const handleSelect = (app: ApplicationListItem) => {
    if (selectedId === app.id) {
      closeDrawer();
      return;
    }
    setSelectedId(app.id);
    drawerJobIdRef.current = app.jobId;
    setDetail(null);
    setDetailLoading(true);
    setJobDetail(null);
    setJdExpanded(false);
    // 投递详情（时间线）
    getApplicationDetail(app.id)
      .then((data) => {
        if (isMountedRef.current) setDetail(data);
      })
      .catch(() => {
        // 错误已由拦截器统一提示
      })
      .finally(() => {
        if (isMountedRef.current) setDetailLoading(false);
      });
    // 岗位发布信息（可独立失败，重试按钮只重拉这一路）
    fetchJobDetail(app.jobId);
  };

  /** 撤回投递（仅非终态可撤回，后端已做状态机校验） */
  const handleWithdraw = () => {
    if (!detail) return;
    Modal.confirm({
      title: '撤回投递',
      content: `确认撤回「${detail.jobTitle}」的投递吗？撤回后该投递将不再参与招聘流程。`,
      okText: '确认撤回',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setWithdrawing(true);
        try {
          await withdrawApplication(detail.id);
          message.success('已撤回投递');
          // 刷新列表（状态已变）+ 刷新详情
          fetchList(true);
          if (selectedId) {
            getApplicationDetail(selectedId).then((data) => {
              if (isMountedRef.current) setDetail(data);
            });
          }
        } catch {
          // 错误已由拦截器统一提示
        } finally {
          if (isMountedRef.current) setWithdrawing(false);
        }
      },
    });
  };

  /** 回应 Offer 成功后刷新列表 + 详情 */
  const refreshAfterOfferAction = useCallback(
    (id: string) => {
      fetchList(true);
      getApplicationDetail(id).then((data) => {
        if (isMountedRef.current) setDetail(data);
      });
    },
    [fetchList],
  );

  /** 接受 Offer（仅 OFFERED 待录用，后端状态机校验） */
  const handleAcceptOffer = () => {
    if (!detail) return;
    Modal.confirm({
      title: '接受Offer',
      content: `确认接受「${detail.jobTitle}」的Offer吗？接受后将确认录用。`,
      okText: '确认接受',
      cancelText: '取消',
      onOk: async () => {
        setAcceptingOffer(true);
        try {
          await acceptOffer(detail.id);
          message.success('已接受Offer，恭喜！');
          refreshAfterOfferAction(detail.id);
        } catch {
          // 错误已由拦截器统一提示
        } finally {
          if (isMountedRef.current) setAcceptingOffer(false);
        }
      },
    });
  };

  /** 拒绝 Offer（仅 OFFERED 待录用，后端状态机校验；拒绝原因选填，透传至 hr_offer） */
  const handleDeclineOffer = () => {
    if (!detail) return;
    // 选填拒绝原因（非受控：Modal.confirm 的 content 为创建时静态闭包，受控 state 不会刷新，
    // 故用闭包局部变量承载输入值，onOk 读取）
    let reason = '';
    Modal.confirm({
      title: '拒绝Offer',
      content: (
        <div>
          <div>确认拒绝「{detail.jobTitle}」的Offer吗？拒绝后该投递将进入终态。</div>
          <Input.TextArea
            rows={3}
            maxLength={200}
            showCount
            placeholder="填写拒绝原因（选填）"
            style={{ marginTop: 12 }}
            onChange={(e) => {
              reason = e.target.value;
            }}
          />
        </div>
      ),
      okText: '确认拒绝',
      cancelText: '取消',
      okButtonProps: { danger: true },
      onOk: async () => {
        setDecliningOffer(true);
        try {
          await declineOffer(detail.id, reason.trim() || undefined);
          message.success('已拒绝Offer');
          refreshAfterOfferAction(detail.id);
        } catch {
          // 错误已由拦截器统一提示
        } finally {
          if (isMountedRef.current) setDecliningOffer(false);
        }
      },
    });
  };

  /** 前端 Tab 筛选（后端列表接口仅按候选人隔离，无状态参数） */
  const filtered =
    activeTab === 'all'
      ? items
      : activeTab === 'active'
        ? items.filter(
            (a) =>
              ![
                ApplicationStatus.REJECTED,
                ApplicationStatus.WITHDRAWN,
                ApplicationStatus.OFFERED,
                ApplicationStatus.OFFER_ACCEPTED,
                ApplicationStatus.OFFER_DECLINED,
              ].includes(a.status as ApplicationStatus),
          )
        : activeTab === 'rejected'
          ? items.filter(
              (a) =>
                a.status === ApplicationStatus.REJECTED ||
                a.status === ApplicationStatus.OFFER_DECLINED,
            )
          : items.filter((a) =>
              [ApplicationStatus.OFFERED, ApplicationStatus.OFFER_ACCEPTED].includes(
                a.status as ApplicationStatus,
              ),
            );

  /** 排序：待录用置顶 → 其他进行中 → 已撤回沉底；各组内按投递时间降序 */
  const sorted = useMemo(() => {
    const group = (s: string) =>
      s === ApplicationStatus.OFFERED ? 0 : s === ApplicationStatus.WITHDRAWN ? 2 : 1;
    return [...filtered].sort((a, b) => {
      const d = group(a.status) - group(b.status);
      if (d !== 0) return d;
      return new Date(b.submittedAt).getTime() - new Date(a.submittedAt).getTime();
    });
  }, [filtered]);

  /** 状态时间线圆点：淘汰/拒绝红叉 / 录用绿勾 / 其余时钟 */
  const toDot = (toStatus: string) =>
    toStatus === ApplicationStatus.REJECTED ||
    toStatus === ApplicationStatus.OFFER_DECLINED ? (
      <CloseCircleOutlined style={{ color: '#DC2626' }} />
    ) : toStatus === ApplicationStatus.OFFERED ||
      toStatus === ApplicationStatus.OFFERABLE ||
      toStatus === ApplicationStatus.OFFER_ACCEPTED ? (
      <CheckCircleOutlined style={{ color: '#059669' }} />
    ) : (
      <ClockCircleOutlined style={{ color: 'var(--text-tertiary)' }} />
    );

  /** 是否可撤回（对齐后端 ApplicationStatus.isWithdrawable：仅面试前的三个早期态） */
  const canWithdraw =
    detail &&
    [
      ApplicationStatus.SUBMITTED,
      ApplicationStatus.VIEWED,
      ApplicationStatus.SCREENED,
    ].includes(detail.status as ApplicationStatus);

  /** 是否可回应 Offer（仅 OFFERED 待录用状态） */
  const canRespondToOffer = detail?.status === ApplicationStatus.OFFERED;

  /** 淘汰原因：从时间线提取 REJECTED 项（后端无独立字段，reject_reason 落在状态日志） */
  const rejectItem = detail?.timeline?.find(
    (t) => t.toStatus === ApplicationStatus.REJECTED,
  );

  const tabItems = [
    { key: 'all', label: '全部' },
    { key: 'active', label: '进行中' },
    { key: 'rejected', label: '已淘汰' },
    { key: 'offered', label: '已录用' },
  ];

  /** 分类空文案（有投递记录但当前分类下无记录时展示，不引导去投递） */
  const tabEmptyText: Record<string, string> = {
    active: '暂无进行中的投递',
    rejected: '暂无已淘汰记录',
    offered: '暂无已录用记录',
  };

  // ==================== Drawer 内容 ====================

  /** JD 全文（折叠时截断） */
  const jdText = jobDetail?.jdText ?? '';
  const jdCollapsed = !jdExpanded && jdText.length > JD_PREVIEW_LEN;
  const jdDisplay = jdCollapsed ? `${jdText.slice(0, JD_PREVIEW_LEN)}…` : jdText;

  return (
    <div className={styles.page}>
      <PageHeader
        title="投递进度"
        description="实时追踪你的投递状态"
        extra={
          <Button
            type="primary"
            icon={<SendOutlined />}
            onClick={() => navigate(ROUTES.CANDIDATE_JOB)}
          >
            去投递新岗位
          </Button>
        }
      />

      <Tabs
        className={styles.tabs}
        activeKey={activeTab}
        onChange={handleTabChange}
        items={tabItems}
      />

      {listLoading ? (
        <div className={styles.pageCenter}>
          <Spin size="large" />
        </div>
      ) : listError ? (
        <div className={styles.pageCenter}>
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>🌐</div>
            <div className={styles.emptyTitle}>网络开小差了</div>
            <div className={styles.emptyDesc}>投递记录加载失败，请重试</div>
            <Button type="primary" onClick={() => fetchList(true)}>
              重新加载
            </Button>
          </div>
        </div>
      ) : items.length === 0 ? (
        // 完全没有投递记录 → 引导去投递
        <div className={styles.pageCenter}>
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>📮</div>
            <div className={styles.emptyTitle}>还没有投递记录</div>
            <div className={styles.emptyDesc}>去岗位列表逛逛，遇到合适的岗位就投递吧</div>
            <Button type="primary" onClick={() => navigate(ROUTES.CANDIDATE_JOB)}>
              去投递新岗位
            </Button>
          </div>
        </div>
      ) : sorted.length === 0 ? (
        // 已有投递记录但当前分类下无记录 → 轻量空提示（不引导去投递）
        <div className={styles.pageCenter}>
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>🗂️</div>
            <div className={styles.emptyTitle}>{tabEmptyText[activeTab] || '暂无投递记录'}</div>
          </div>
        </div>
      ) : (
        <>
          <div className={styles.appList}>
            {sorted.map((app) => (
              <div
                key={app.id}
                className={`${styles.appCard} ${selectedId === app.id ? styles.appCardActive : ''} ${
                  app.status === ApplicationStatus.OFFERED ? styles.appCardOffered : ''
                }`}
                onClick={() => handleSelect(app)}
              >
                <div className={styles.appCardTop}>
                  <div>
                    <div className={styles.appTitle}>
                      {app.jobTitle}
                      {app.cityName && <span className={styles.appCity}>{app.cityName}</span>}
                    </div>
                    <div className={styles.appCompany}>{app.companyName}</div>
                  </div>
                  <StatusTag type={statusTypeMap[app.status] || 'neutral'}>
                    {statusLabelMap[app.status] || app.status}
                  </StatusTag>
                </div>
                <div className={styles.appMeta}>
                  <span className={styles.appTime}>投递时间: {fmtTime(app.submittedAt)}</span>
                  {selectedId === app.id && detailLoading && (
                    <span className={styles.appLoading}>
                      <Spin size="small" />
                    </span>
                  )}
                </div>
              </div>
            ))}
          </div>

          {/* 加载更多（列表分页） */}
          {items.length < total && (
            <div className={styles.loadMoreWrap}>
              <Button loading={loadingMore} onClick={() => fetchList(false)}>
                加载更多
              </Button>
            </div>
          )}
        </>
      )}

      {/* 投递详情侧拉框：岗位发布信息 + 投递时间线 */}
      <Drawer
        open={!!selectedId}
        onClose={closeDrawer}
        width={520}
        title="投递详情"
      >
        {/* ===== 岗位发布信息（job 模块公开接口） ===== */}
        {jobLoading ? (
          <div className={styles.drawerJobLoading}>
            <Spin />
            <span className={styles.drawerJobLoadingText}>正在加载岗位信息…</span>
          </div>
        ) : jobError ? (
          <div className={styles.drawerJobError}>
            <div>岗位信息暂时无法加载</div>
            <Button size="small" onClick={() => fetchJobDetail(drawerJobIdRef.current)}>
              重试
            </Button>
          </div>
        ) : jobDetail ? (
          <div className={styles.drawerJob}>
            <div className={styles.drawerJobTitle}>
              {jobDetail.title}
              {jobDetail.cityName && (
                <span className={styles.drawerJobCity}>{jobDetail.cityName}</span>
              )}
            </div>
            <div className={styles.drawerJobCompany}>{jobDetail.companyName}</div>

            {/* 薪资 / 经验 / 学历 */}
            <div className={styles.drawerJobMeta}>
              <div className={styles.drawerJobMetaItem}>
                <span className={styles.drawerJobMetaLabel}>薪资</span>
                <span className={styles.drawerJobMetaValue}>
                  {fmtSalary(jobDetail.salary)}
                </span>
              </div>
              <div className={styles.drawerJobMetaItem}>
                <span className={styles.drawerJobMetaLabel}>经验</span>
                <span className={styles.drawerJobMetaValue}>
                  {jobDetail.minExperienceYears
                    ? `${jobDetail.minExperienceYears}年以上`
                    : '经验不限'}
                </span>
              </div>
              <div className={styles.drawerJobMetaItem}>
                <span className={styles.drawerJobMetaLabel}>学历</span>
                <span className={styles.drawerJobMetaValue}>
                  {educationLabelMap[jobDetail.educationRequirement] ||
                    jobDetail.educationRequirement}
                </span>
              </div>
            </div>

            {/* 岗位描述（JD 折叠） */}
            {jdText && (
              <>
                <div className={styles.drawerSectionTitle}>岗位描述</div>
                <div className={styles.drawerJd}>{jdDisplay}</div>
                {jdCollapsed && (
                  <Button
                    type="link"
                    size="small"
                    className={styles.drawerJdToggle}
                    onClick={() => setJdExpanded(true)}
                  >
                    展开全文
                  </Button>
                )}
              </>
            )}

            {/* 技能画像 */}
            {(jobDetail.profile?.coreSkills?.length > 0 ||
              jobDetail.profile?.industryExperience) && (
              <>
                <div className={styles.drawerSectionTitle}>技能画像</div>
                <div className={styles.drawerSkills}>
                  {jobDetail.profile.coreSkills.map((s) => (
                    <Tag key={s.name} color={s.required ? 'blue' : 'default'}>
                      {s.name}
                    </Tag>
                  ))}
                </div>
                {jobDetail.profile.industryExperience && (
                  <div className={styles.drawerIndustry}>
                    {jobDetail.profile.industryExperience}
                  </div>
                )}
              </>
            )}
          </div>
        ) : null}

        <div className={styles.drawerDivider} />

        {/* ===== 投递进度（时间线 + 淘汰原因 + 撤回） ===== */}
        <div className={styles.drawerTimelineTitle}>
          投递进度
          {detail?.matchScore != null && (
            <span className={styles.drawerMatchScore}>匹配 {detail.matchScore}%</span>
          )}
        </div>

        {detailLoading ? (
          <div className={styles.pageCenter}>
            <Spin />
          </div>
        ) : detail ? (
          <>
            <Timeline
              items={detail.timeline.map((t) => ({
                dot: toDot(t.toStatus),
                children: (
                  <div>
                    <div className={styles.timelineStatus}>
                      {statusLabelMap[t.toStatus] || t.toStatus}
                    </div>
                    {t.reason && (
                      <div className={styles.timelineReason}>
                        {t.toStatus === ApplicationStatus.REJECTED ? '淘汰原因: ' : '说明: '}
                        {t.reason}
                      </div>
                    )}
                    <div className={styles.timelineTime}>{fmtTime(t.createdAt)}</div>
                  </div>
                ),
              }))}
            />

            {/* 淘汰原因卡片（从时间线 REJECTED 项提取） */}
            {detail.status === ApplicationStatus.REJECTED && rejectItem && (
              <div className={styles.drawerRejectCard}>
                <div className={styles.drawerRejectTitle}>淘汰原因</div>
                <div className={styles.drawerRejectText}>{rejectItem.reason || '暂无'}</div>
              </div>
            )}

            {/* Offer 操作（仅 OFFERED 待录用：接受/拒绝进入终态） */}
            {canRespondToOffer && (
              <div className={styles.drawerOfferActions}>
                <Button type="primary" loading={acceptingOffer} onClick={handleAcceptOffer}>
                  接受Offer
                </Button>
                <Button danger loading={decliningOffer} onClick={handleDeclineOffer}>
                  拒绝Offer
                </Button>
              </div>
            )}

            {/* 撤回投递（仅面试前的三个早期态） */}
            {canWithdraw && (
              <div className={styles.drawerActions}>
                <Button danger loading={withdrawing} onClick={handleWithdraw}>
                  撤回投递
                </Button>
              </div>
            )}
          </>
        ) : (
          <div className={styles.pageCenter}>
            <div className={styles.emptyDesc}>详情加载失败</div>
          </div>
        )}
      </Drawer>
    </div>
  );
};

export default ApplicationPage;
