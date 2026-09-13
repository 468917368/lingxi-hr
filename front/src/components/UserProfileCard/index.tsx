import React from 'react';
import { Spin } from 'antd';
import {
  EnvironmentOutlined,
  ClockCircleOutlined,
  BookOutlined,
  FileTextOutlined,
  DollarOutlined,
  CheckCircleOutlined,
} from '@ant-design/icons';
import styles from './index.less';

/** 用户公开信息（不含隐私数据） */
export interface UserPublicInfo {
  id: number;
  name: string;
  avatar?: string | null;
  role: string;
  // 个人信息
  gender?: string | null;
  city?: string | null;
  workYears?: string | null;
  education?: string | null;
  jobStatus?: string | null;
  // 求职意向
  desiredJob?: string | null;
  desiredCity?: string | null;
  desiredSalaryMin?: number | null;
  desiredSalaryMax?: number | null;
  availableFrom?: string | null;
  // 隐私设置相关
  resumePublic?: boolean;
  blindMode?: boolean;
}

/** 公司信息 */
export interface CompanyInfo {
  id: number;
  name: string;
  shortName?: string | null;
  industry?: string | null;
  scale?: string | null;
  stage?: string | null;
  city?: string | null;
  address?: string | null;
  description?: string | null;
  logo?: string | null;
  website?: string | null;
  foundedYear?: number | null;
  certStatus?: string | null;
  tags?: string[] | null;
  benefits?: string[] | null;
  techStack?: string[] | null;
}

/** HR公开信息（包含公司信息） */
export interface HRPublicInfo {
  id: number;
  name: string;
  avatar?: string | null;
  role: string;
  department?: string | null;
  position?: string | null;
  techDirection?: string | null;
  interviewCount?: number | null;
  createdAt?: string | null;
  company: CompanyInfo;
}

interface UserProfileCardProps {
  user: UserPublicInfo | HRPublicInfo | null;
  loading?: boolean;
  visible: boolean;
  isHR?: boolean;
  onClose?: () => void;
}

/** 求职状态映射 */
const JOB_STATUS_MAP: Record<string, { label: string; color: string }> = {
  JOB_SEEKING: { label: '求职中', color: '#059669' },
  EMPLOYED_LOOKING: { label: '在职看机会', color: '#2563EB' },
  NOT_LOOKING: { label: '暂不考虑', color: '#6B7280' },
  LOOKING: { label: '求职中', color: '#059669' },
  OPEN: { label: '开放', color: '#2563EB' },
};

/** 学历映射 */
const EDUCATION_MAP: Record<string, string> = {
  HIGH_SCHOOL: '高中',
  ASSOCIATE: '大专',
  BACHELOR: '本科',
  MASTER: '硕士',
  DOCTOR: '博士',
  MBA: 'MBA',
  POSTDOCTORAL: '博士后',
};

/** 工作年限映射 */
const WORK_YEARS_MAP: Record<string, string> = {
  ZERO: '应届生',
  ONE: '1年',
  TWO: '2年',
  THREE: '3年',
  FOUR: '4年',
  FIVE: '5年',
  SIX: '6年',
  SEVEN: '7年',
  EIGHT: '8年',
  NINE: '9年',
  TEN_PLUS: '10年以上',
};

/** 薪资格式化 */
function formatSalary(min?: number | null, max?: number | null): string {
  if (!min && !max) return '面议';
  if (min && max) {
    const minK = (min / 1000).toFixed(0);
    const maxK = (max / 1000).toFixed(0);
    return `${minK}K-${maxK}K`;
  }
  return '面议';
}

/** 公司规模映射 */
const COMPANY_SCALE_MAP: Record<string, string> = {
  TINY: '1-20人',
  SMALL: '21-99人',
  MEDIUM: '100-499人',
  LARGE: '500-999人',
  XLARGE: '1000-9999人',
  HUGE: '10000人以上',
};

/** 融资阶段映射 */
const COMPANY_STAGE_MAP: Record<string, string> = {
  ANGEL: '天使轮',
  A_ROUND: 'A轮',
  B_ROUND: 'B轮',
  C_ROUND: 'C轮',
  D_ROUND: 'D轮及以上',
  PRE_IPO: 'Pre-IPO',
  PUBLIC: '已上市',
  PROFITABLE: '盈利',
  UNKNOWN: '未知',
};

/** 认证状态映射 */
const CERT_STATUS_MAP: Record<string, { label: string; color: string }> = {
  PENDING: { label: '待认证', color: '#F59E0B' },
  APPROVED: { label: '已认证', color: '#059669' },
  REJECTED: { label: '认证失败', color: '#EF4444' },
};

/** HR信息卡片 */
const HRProfileCard: React.FC<{ user: HRPublicInfo }> = ({ user }) => {
  const certStatus = user.company.certStatus ? CERT_STATUS_MAP[user.company.certStatus] : null;

  return (
    <div className={styles.card}>
      {/* 头部：HR头像 + 名字 + 部门 */}
      <div className={styles.header}>
        <div className={styles.avatar}>
          {user.avatar ? (
            <img src={user.avatar} alt={user.name} />
          ) : (
            <span>{user.name?.charAt(0) || '?'}</span>
          )}
        </div>
        <div className={styles.userInfo}>
          <div className={styles.userName}>{user.name}</div>
          <div className={styles.userMeta}>
            {user.department && (
              <span className={styles.statusBadge} style={{ background: '#2563EB15', color: '#2563EB' }}>
                {user.department}
              </span>
            )}
            {user.position && (
              <span className={styles.statusBadge} style={{ background: '#05966915', color: '#059669', marginLeft: 8 }}>
                {user.position}
              </span>
            )}
          </div>
        </div>
      </div>

      {/* HR信息 */}
      <div className={styles.section}>
        <div className={styles.sectionTitle}>HR信息</div>
        <div className={styles.infoList}>
          {user.techDirection && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>💻</span>
              <span>技术方向：{user.techDirection}</span>
            </div>
          )}
          {user.interviewCount !== undefined && user.interviewCount !== null && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>🎯</span>
              <span>已面试：{user.interviewCount} 场</span>
            </div>
          )}
          {user.createdAt && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>📅</span>
              <span>入职时间：{user.createdAt}</span>
            </div>
          )}
        </div>
      </div>

      {/* 公司信息 */}
      <div className={styles.section}>
        <div className={styles.sectionTitle}>公司信息</div>
        <div className={styles.infoList}>
          <div className={styles.infoItem}>
            <span className={styles.infoIcon}>🏢</span>
            <span>
              {user.company.name}
              {user.company.shortName && user.company.shortName !== user.company.name && (
                <span className={styles.companyAlias}>（{user.company.shortName}）</span>
              )}
            </span>
          </div>
          {certStatus && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>✅</span>
              <span style={{ color: certStatus.color }}>{certStatus.label}</span>
            </div>
          )}
          {user.company.industry && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>📊</span>
              <span>{user.company.industry}</span>
            </div>
          )}
          {user.company.scale && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>👥</span>
              <span>{COMPANY_SCALE_MAP[user.company.scale] || user.company.scale}</span>
            </div>
          )}
          {user.company.stage && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>📈</span>
              <span>{COMPANY_STAGE_MAP[user.company.stage] || user.company.stage}</span>
            </div>
          )}
          {user.company.city && (
            <div className={styles.infoItem}>
              <EnvironmentOutlined className={styles.infoIcon} />
              <span>{user.company.city}</span>
            </div>
          )}
          {user.company.address && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>📍</span>
              <span>{user.company.address}</span>
            </div>
          )}
          {user.company.foundedYear && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>📅</span>
              <span>成立于 {user.company.foundedYear} 年</span>
            </div>
          )}
          {user.company.website && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>🔗</span>
              <a href={user.company.website} target="_blank" rel="noopener noreferrer" className={styles.companyLink}>
                {user.company.website}
              </a>
            </div>
          )}
        </div>
      </div>

      {/* 公司标签 */}
      {user.company.tags && user.company.tags.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>公司标签</div>
          <div className={styles.tagList}>
            {user.company.tags.map((tag, index) => (
              <span key={index} className={styles.tag}>{tag}</span>
            ))}
          </div>
        </div>
      )}

      {/* 公司福利 */}
      {user.company.benefits && user.company.benefits.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>公司福利</div>
          <div className={styles.tagList}>
            {user.company.benefits.map((benefit, index) => (
              <span key={index} className={styles.tag} style={{ background: '#ECFDF5', color: '#059669' }}>{benefit}</span>
            ))}
          </div>
        </div>
      )}

      {/* 技术栈 */}
      {user.company.techStack && user.company.techStack.length > 0 && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>技术栈</div>
          <div className={styles.tagList}>
            {user.company.techStack.map((tech, index) => (
              <span key={index} className={styles.tag} style={{ background: '#EFF6FF', color: '#2563EB' }}>{tech}</span>
            ))}
          </div>
        </div>
      )}

      {/* 公司简介 */}
      {user.company.description && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>公司简介</div>
          <div className={styles.companyDesc}>{user.company.description}</div>
        </div>
      )}
    </div>
  );
};

/** 求职者信息卡片 */
const CandidateProfileCard: React.FC<{ user: UserPublicInfo }> = ({ user }) => {
  const status = user.jobStatus ? JOB_STATUS_MAP[user.jobStatus] : null;

  return (
    <div className={styles.card}>
      {/* 头部：头像 + 名字 + 状态 */}
      <div className={styles.header}>
        <div className={styles.avatar}>
          {user.avatar ? (
            <img src={user.avatar} alt={user.name} />
          ) : (
            <span>{user.name?.charAt(0) || '?'}</span>
          )}
        </div>
        <div className={styles.userInfo}>
          <div className={styles.userName}>{user.name}</div>
          {status && (
            <span className={styles.statusBadge} style={{ background: `${status.color}15`, color: status.color }}>
              <CheckCircleOutlined /> {status.label}
            </span>
          )}
        </div>
      </div>

      {/* 个人信息 */}
      <div className={styles.section}>
        <div className={styles.sectionTitle}>个人信息</div>
        <div className={styles.infoList}>
          {user.gender && (
            <div className={styles.infoItem}>
              <span className={styles.infoIcon}>👤</span>
              <span>{user.gender === 'MALE' ? '男' : user.gender === 'FEMALE' ? '女' : user.gender}</span>
            </div>
          )}
          {user.city && (
            <div className={styles.infoItem}>
              <EnvironmentOutlined className={styles.infoIcon} />
              <span>{user.city}</span>
            </div>
          )}
          {user.workYears && (
            <div className={styles.infoItem}>
              <ClockCircleOutlined className={styles.infoIcon} />
              <span>{WORK_YEARS_MAP[user.workYears] || user.workYears}</span>
            </div>
          )}
          {user.education && (
            <div className={styles.infoItem}>
              <BookOutlined className={styles.infoIcon} />
              <span>{EDUCATION_MAP[user.education] || user.education}</span>
            </div>
          )}
        </div>
      </div>

      {/* 求职意向 */}
      <div className={styles.section}>
        <div className={styles.sectionTitle}>求职意向</div>
        <div className={styles.infoList}>
          {user.desiredJob && (
            <div className={styles.infoItem}>
              <FileTextOutlined className={styles.infoIcon} />
              <span>{user.desiredJob}</span>
            </div>
          )}
          {user.desiredCity && (
            <div className={styles.infoItem}>
              <EnvironmentOutlined className={styles.infoIcon} />
              <span>{user.desiredCity}</span>
            </div>
          )}
          <div className={styles.infoItem}>
            <DollarOutlined className={styles.infoIcon} />
            <span>{formatSalary(user.desiredSalaryMin, user.desiredSalaryMax)}</span>
          </div>
          {user.availableFrom && (
            <div className={styles.infoItem}>
              <ClockCircleOutlined className={styles.infoIcon} />
              <span>到岗时间：{user.availableFrom}</span>
            </div>
          )}
        </div>
      </div>

      {/* 隐私设置 */}
      {(user.resumePublic !== undefined || user.blindMode !== undefined) && (
        <div className={styles.section}>
          <div className={styles.sectionTitle}>隐私设置</div>
          <div className={styles.infoList}>
            {user.resumePublic !== undefined && (
              <div className={styles.infoItem}>
                <span className={styles.infoIcon}>📄</span>
                <span>简历公开：{user.resumePublic ? '是' : '否'}</span>
              </div>
            )}
            {user.blindMode !== undefined && (
              <div className={styles.infoItem}>
                <span className={styles.infoIcon}>🔒</span>
                <span>盲选模式：{user.blindMode ? '已开启' : '未开启'}</span>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};

const UserProfileCard: React.FC<UserProfileCardProps> = ({ user, loading, visible, isHR }) => {
  if (!visible) return null;

  if (loading) {
    return (
      <div className={styles.card}>
        <div className={styles.loading}>
          <Spin size="small" />
        </div>
      </div>
    );
  }

  if (!user) {
    return (
      <div className={styles.card}>
        <div className={styles.empty}>暂无信息</div>
      </div>
    );
  }

  // 根据是否是HR显示不同的卡片
  if (isHR) {
    return <HRProfileCard user={user as HRPublicInfo} />;
  }

  return <CandidateProfileCard user={user as UserPublicInfo} />;
};

export default UserProfileCard;
