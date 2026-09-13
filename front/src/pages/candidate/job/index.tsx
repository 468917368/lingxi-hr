import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate, useSearchParams } from 'umi';
import { Input, Select, Pagination, Button, Empty, Skeleton, message } from 'antd';
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons';
import PageHeader from '@/components/PageHeader';
import JobCard from '@/components/JobCard';
import {
  getCandidateJobs,
  getJobOptions,
} from '@/services/job';
import type { CandidateJobListItem, JobOptionsResponse } from '@/services/job';
import { getUserInfo } from '@/services/user';
import { useJobFavorites } from '@/hooks/useJobFavorites';
import useUserStore from '@/stores/userStore';
import { getErrorCode } from '@/utils/apiError';
import { mapUserInfoToStore } from '@/utils/userMapper';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

const PAGE_SIZE = 12;

/** 技能标签最多数量（后端去重后 >10 → 400） */
const MAX_SKILL_TAGS = 10;

const JobListPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams, setSearchParams] = useSearchParams();
  // 用户信息（画像缺失引导判断；挂载时刷新，见下方 useEffect）
  const userInfo = useUserStore((s) => s.userInfo);
  const isLogin = useUserStore((s) => s.isLogin);
  // 收藏：仅已登录候选人启用 Hook（app.tsx 守卫下未登录到不了本页，防御性判断）
  const isCandidate = isLogin && userInfo?.role === 'CANDIDATE';
  const { favoriteIds, idsLoading, togglingIds, toggleFavorite } = useJobFavorites(isCandidate);

  // URL Query 驱动筛选
  const keyword = searchParams.get('keyword') || '';
  const city = searchParams.get('cityCode') || undefined;
  // 用原始字符串做依赖（P0：split 出的数组每次渲染新引用，会造成 fetchJobs 依赖变化 → 无限请求循环）
  const skillTagsParam = searchParams.get('skillTags') || '';
  const sortBy = searchParams.get('sortBy') || undefined;
  const industry = searchParams.get('industryGroupCode') || undefined;
  // P2：page 规范化 1~100（后端上限），避免越界请求
  const page = Math.min(Math.max(Number(searchParams.get('page')) || 1, 1), 100);

  // 智能推荐模式：无任何筛选 + 未显式传 sortBy（或显式 RECOMMENDED）
  // ⚠️ 与后端 isEmptySearch 参数集合（keyword/industryGroupCode/industryCode/cityCode/skillTags/salaryMin/salaryMax/experienceMax/education/jobType）的交集
  //    = 当前页面全部筛选维度（本页无薪资/经验/学历/jobType 控件）；未来新增筛选控件需同步更新此判断
  const isRecommendMode = !keyword && !city && !skillTagsParam && !industry &&
    (!sortBy || sortBy === 'RECOMMENDED');

  // 画像缺失判断（对齐后端 UserProfileVO 字段：desiredJob/desiredCity/workYears/education/desiredSalaryMin/Max）
  const profileMissing = !userInfo?.profile ||
    !(userInfo.profile.desiredJob || userInfo.profile.desiredCity ||
      userInfo.profile.workYears || userInfo.profile.education ||
      userInfo.profile.desiredSalaryMin || userInfo.profile.desiredSalaryMax);
  const showProfileGuide = isRecommendMode && userInfo?.role === 'CANDIDATE' && profileMissing;

  const [jobs, setJobs] = useState<CandidateJobListItem[]>([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [loadError, setLoadError] = useState(false);
  // 技能下拉候选（从岗位列表聚合，接口失败为空 → tags 仍可手动输入）
  const [skillOptions, setSkillOptions] = useState<{ value: string; label: string }[]>([]);
  // P1#2：搜索框受控草稿（初始 = URL keyword，随 URL 同步）
  const [searchDraft, setSearchDraft] = useState(keyword);
  // 搜索选项（行业/排序），加载失败则下拉禁用
  const [options, setOptions] = useState<JobOptionsResponse | null>(null);
  const [optionsFailed, setOptionsFailed] = useState(false);
  // 递增序号：丢弃过期响应，避免乱序覆盖
  const requestSeq = useRef(0);

  // ===== 搜索选项加载（降级：失败禁用下拉） =====
  useEffect(() => {
    getJobOptions()
      .then((res) => setOptions(res))
      .catch(() => setOptionsFailed(true));
  }, []);

  // ===== 进入页面刷新用户画像（P2-3：画像编辑保存不更新 store，返回本页时保证引导判断用最新数据） =====
  useEffect(() => {
    // 未登录无需刷新（画像模式仅登录候选人触发）
    if (!userInfo) return;
    getUserInfo()
      .then((u) => useUserStore.getState().setUserInfo(mapUserInfoToStore(u)))
      .catch(() => { /* 拉取失败忽略，沿用快照 */ });
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const updateParams = useCallback(
    (updates: Record<string, string | undefined>) => {
      const params = new URLSearchParams(searchParams);
      Object.entries(updates).forEach(([k, v]) => {
        if (v) params.set(k, v);
        else params.delete(k);
      });
      setSearchParams(params);
    },
    [searchParams],
  );

  // ===== 数据加载（服务端分页 + 竞态丢弃；P0 依赖原始字符串防循环） =====
  const fetchJobs = useCallback(
    async (targetPage: number) => {
      const seq = ++requestSeq.current;
      setLoading(true);
      setLoadError(false);
      try {
        // 请求内解析 skillTagsParam，不依赖 selectedTags 数组（P0）
        const tags = skillTagsParam ? skillTagsParam.split(',') : [];
        const res = await getCandidateJobs({
          keyword: keyword || undefined,
          cityCode: city,
          skillTags: tags.length ? tags : undefined,
          sortBy: sortBy as 'RECOMMENDED' | 'LATEST' | 'SALARY_DESC' | undefined,
          industryGroupCode: industry,
          page: targetPage,
          size: PAGE_SIZE,
        });
        // 竞态：只接受最新一次请求
        if (seq !== requestSeq.current) return;
        setJobs(res.list);
        setTotal(res.total);
        // 技能下拉候选（从岗位列表 skillTags 聚合、去重；接口失败不更新 → tags 仍可手动输入）
        setSkillOptions(
          Array.from(new Set((res.list || []).flatMap((j) => j.skillTags || []))).map((s) => ({ value: s, label: s })),
        );
      } catch (error) {
        if (seq !== requestSeq.current) return;
        // 业务 400（skillTags>10 等）：拦截器不弹，页面提示（P2：不回退 page，避免掩盖非法参数真因）
        if (getErrorCode(error) === 400) {
          message.error((error as Error).message || '请求参数错误');
        }
        setLoadError(true);
        setJobs([]);
        setTotal(0);
      } finally {
        if (seq === requestSeq.current) setLoading(false);
      }
    },
    [keyword, city, skillTagsParam, sortBy, industry, updateParams],
  );

  useEffect(() => {
    fetchJobs(page);
  }, [fetchJobs, page]);

  const handleSearch = (value: string) => {
    updateParams({ keyword: value.trim() || undefined, page: '1' });
  };

  // P1#2：URL keyword 变化时同步搜索草稿（覆盖重置/前进后退）
  useEffect(() => {
    setSearchDraft(keyword);
  }, [keyword]);

  const handleReset = () => {
    setSearchParams({});
  };

  const handleJobClick = (job: CandidateJobListItem) => {
    navigate(`${ROUTES.CANDIDATE_JOB_DETAIL}/${job.jobId}`);
  };

  const handleToggleFavorite = (jobId: number, target: boolean) => {
    toggleFavorite(jobId, target).then((ok) => {
      if (ok) message.success(target ? '已收藏岗位' : '已取消收藏');
    });
  };

  const handlePageChange = (nextPage: number) => {
    const params = new URLSearchParams(searchParams);
    if (nextPage <= 1) params.delete('page');
    else params.set('page', String(nextPage));
    setSearchParams(params);
  };

  // 技能标签：trim 去空 + 限 10 个（前端预防后端 400）
  const handleSkillTagsChange = (values: string[]) => {
    const cleaned = values
      .map((v) => v.trim())
      .filter(Boolean)
      .slice(0, MAX_SKILL_TAGS);
    updateParams({ skillTags: cleaned.length ? cleaned.join(',') : undefined, page: '1' });
  };

  return (
    <div className={styles.page}>
      <PageHeader
        title="岗位探索"
        description="发现与你匹配的理想岗位"
        extra={
          <span className={styles.count}>
            共 {total} 个岗位
          </span>
        }
      />

      <div className={styles.filterBar}>
        <Input.Search
          placeholder="搜索岗位名称或岗位摘要"
          allowClear
          prefix={<SearchOutlined />}
          value={searchDraft}
          onChange={(e) => setSearchDraft(e.target.value)}
          onSearch={handleSearch}
          className={styles.searchInput}
        />
        <Select
          className={styles.filterSelect}
          placeholder="选择城市"
          allowClear
          disabled={!options || optionsFailed}
          value={city}
          onChange={(v) => updateParams({ cityCode: v || undefined, page: '1' })}
          options={(options?.cities ?? []).map((item) => ({ value: item.code, label: item.name }))}
        />
        <Select
          className={styles.skillTagSelect}
          mode="tags"
          placeholder="按技能标签筛选"
          allowClear
          options={skillOptions}
          value={skillTagsParam ? skillTagsParam.split(',') : []}
          onChange={handleSkillTagsChange}
          tokenSeparators={[',', '、']}
          maxCount={MAX_SKILL_TAGS}
        />
        <Select
          className={styles.filterSelect}
          placeholder="排序"
          allowClear
          disabled={!options || optionsFailed}
          value={sortBy}
          onChange={(v) => updateParams({ sortBy: v || undefined, page: '1' })}
          options={(options?.sortOptions || []).map((s) => ({ value: s.code, label: s.name }))}
        />
        <Select
          className={styles.filterSelect}
          placeholder="行业"
          allowClear
          disabled={!options || optionsFailed}
          value={industry}
          onChange={(v) => updateParams({ industryGroupCode: v || undefined, page: '1' })}
          options={(options?.industries || []).map((i) => ({ value: i.groupCode, label: i.name }))}
        />
        <Button
          className={styles.filterSelect}
          icon={<ReloadOutlined />}
          onClick={handleReset}
        >
          重置
        </Button>
      </div>

      {/* 画像推荐模式提示（无筛选 + 智能推荐；含画像缺失引导） */}
      {isRecommendMode && (
        <div className={styles.recommendBar}>
          <span className={styles.recommendTag}>智能推荐</span>
          <span className={styles.recommendText}>根据求职画像个性化排序</span>
          {showProfileGuide && (
            <a
              className={styles.guideLink}
              onClick={() => navigate(ROUTES.CANDIDATE_PROFILE)}
            >
              完善求职画像，获得更精准推荐 ›
            </a>
          )}
        </div>
      )}

      {/* 技能 OR 语义提示 */}
      <div className={styles.orHint}>
        多选技能任一命中即匹配（OR）
      </div>

      {loadError ? (
        <div className={styles.errorContainer}>
          <Empty description="加载失败，请重试">
            <Button type="primary" onClick={() => fetchJobs(page)}>重新加载</Button>
          </Empty>
        </div>
      ) : loading ? (
        // P1#1：首次/筛选加载中显示骨架，避免空白网格
        <div className={styles.grid}>
          {Array.from({ length: PAGE_SIZE }).map((_, i) => (
            <Skeleton key={i} active paragraph={{ rows: 3 }} />
          ))}
        </div>
      ) : jobs.length === 0 ? (
        <div className={styles.empty}>
          <p>没有找到符合条件的岗位</p>
          <a onClick={handleReset}>重置筛选条件</a>
        </div>
      ) : (
        <>
          <div className={styles.grid}>
            {jobs.map((job) => (
              <JobCard
                key={job.jobId}
                job={job}
                onClick={handleJobClick}
                favorite={isCandidate ? {
                  favorited: favoriteIds.has(job.jobId),
                  loading: idsLoading || togglingIds.has(job.jobId),
                  onToggle: handleToggleFavorite,
                } : undefined}
              />
            ))}
          </div>

          {total > PAGE_SIZE && (
            <div className={styles.pagination}>
              <Pagination
                current={page}
                pageSize={PAGE_SIZE}
                total={total}
                onChange={handlePageChange}
                showSizeChanger={false}
              />
            </div>
          )}
        </>
      )}
    </div>
  );
};

export default JobListPage;
