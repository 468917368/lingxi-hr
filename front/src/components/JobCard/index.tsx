import React from 'react';
import { EnvironmentOutlined, StarOutlined, StarFilled } from '@ant-design/icons';
import type { CandidateJobListItem } from '@/services/job';
import styles from './index.less';

interface JobCardProps {
  job: CandidateJobListItem;
  onClick?: (job: CandidateJobListItem) => void;
  showMatchScore?: number;
  compact?: boolean;
  /** 收藏展示与回调（可选：未传则完全不渲染收藏按钮，JobCard 不请求接口、不判断登录） */
  favorite?: {
    favorited: boolean;
    loading: boolean;
    onToggle: (jobId: number, targetFavorited: boolean) => void;
  };
}

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

const JobCard: React.FC<JobCardProps> = ({ job, onClick, showMatchScore, compact, favorite }) => {
  // 薪资展示：分 → 元/K；非 negotiable 但金额 null → 面议（D16 空值守卫）
  const salaryText = (() => {
    if (
      job.salary.negotiable ||
      job.salary.minAmount === null ||
      job.salary.maxAmount === null
    ) {
      return '薪资面议';
    }
    const minK = (job.salary.minAmount / 100000).toFixed(1).replace('.0', '');
    const maxK = (job.salary.maxAmount / 100000).toFixed(1).replace('.0', '');
    const suffix = job.salary.months && job.salary.months > 12 ? `·${job.salary.months}薪` : '';
    return `${minK}k-${maxK}k${suffix}`;
  })();

  // 技能标签：C 端列表项直接用 skillTags（无 profile 嵌套，D5）
  const displaySkills = job.skillTags || [];

  // 企业与招聘负责人展示文本（缺省回退"招聘企业"/"招聘负责人"，禁止出现 `-`）
  const companyName = job.companyName || '招聘企业';
  const creatorName = job.creatorName || '招聘负责人';
  const creatorText = creatorName === '招聘负责人'
    ? '招聘负责人'
    : `招聘负责人：${creatorName}`;
  const companyAndCreatorText = `${companyName} · ${creatorText}`;

  return (
    <div
      className={`${styles.card} ${compact ? styles.compact : ''}`}
      onClick={() => onClick?.(job)}
      onKeyDown={(e) => {
        if (e.key === 'Enter' || e.key === ' ') {
          e.preventDefault();
          onClick?.(job);
        }
      }}
      tabIndex={0}
      role="button"
      aria-label={`查看 ${job.title} 详情`}
    >
      <div className={styles.top}>
        <div className={styles.logo}>
          {companyName.charAt(0) || '企'}
        </div>
        <div className={styles.info}>
          <div className={styles.title}>{job.title}</div>
          <div className={styles.company} title={companyAndCreatorText}>
            {companyAndCreatorText}
          </div>
        </div>
        {favorite && (
          // 收藏按钮：onClick 与 onKeyDown 均 stopPropagation，避免误触进入详情（含键盘 Enter 冒泡）
          <button
            type="button"
            className={`${styles.favBtn} ${favorite.favorited ? styles.favActive : ''}`}
            aria-label={favorite.favorited ? '取消收藏' : '收藏岗位'}
            disabled={favorite.loading}
            onClick={(e) => {
              e.stopPropagation();
              favorite.onToggle(job.jobId, !favorite.favorited);
            }}
            onKeyDown={(e) => {
              if (e.key === 'Enter' || e.key === ' ') e.stopPropagation();
            }}
          >
            {favorite.favorited ? <StarFilled /> : <StarOutlined />}
          </button>
        )}
        {showMatchScore !== undefined && (
          <div className={`${styles.match} ${showMatchScore >= 85 ? styles.matchHigh : showMatchScore >= 70 ? styles.matchMid : styles.matchLow}`}>
            🎯 {showMatchScore}%
          </div>
        )}
      </div>

      <div className={styles.salary}>
        {salaryText}
      </div>

      <div className={styles.meta}>
        <span className={styles.metaItem}><EnvironmentOutlined /> {job.cityName}</span>
        <span className={styles.metaItem}>
          {job.minExperienceYears > 0 ? `${job.minExperienceYears}年以上` : '经验不限'}
        </span>
        <span className={styles.metaItem}>
          {educationLabelMap[job.educationRequirement] || job.educationRequirement}
        </span>
      </div>

      <div className={styles.tags}>
        {displaySkills.slice(0, 3).map((tag) => (
          <span key={tag} className={styles.tag}>{tag}</span>
        ))}
        {displaySkills.length > 3 && (
          <span className={styles.tag}>+{displaySkills.length - 3}</span>
        )}
      </div>
    </div>
  );
};

export default JobCard;
