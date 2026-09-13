/** 用户角色枚举 */
export enum UserRole {
  CANDIDATE = 'CANDIDATE',
  HR = 'HR',
  INTERVIEWER = 'INTERVIEWER',
  ADMIN = 'ADMIN',
}

/** 岗位状态枚举 */
export enum JobStatus {
  DRAFT = 'DRAFT',
  PUBLISHED = 'PUBLISHED',
  PAUSED = 'PAUSED',
  CLOSED = 'CLOSED',
}

/** 投递状态枚举（对齐后端 ApplicationStatus，唯一权威定义） */
export enum ApplicationStatus {
  SUBMITTED = 'SUBMITTED',
  VIEWED = 'VIEWED',
  SCREENED = 'SCREENED',
  INTERVIEWING = 'INTERVIEWING',
  OFFERABLE = 'OFFERABLE',
  /** 待录用（HR 已发 Offer，候选人未表态） */
  OFFERED = 'OFFERED',
  /** 已录用（候选人接受 Offer，终态） */
  OFFER_ACCEPTED = 'OFFER_ACCEPTED',
  /** 已拒绝（候选人拒绝 Offer，终态） */
  OFFER_DECLINED = 'OFFER_DECLINED',
  REJECTED = 'REJECTED',
  WITHDRAWN = 'WITHDRAWN',
}

/** 面试状态枚举（对齐后端 InterviewStatus） */
export enum InterviewStatus {
  PENDING = 'PENDING', // 待安排
  SCHEDULED = 'SCHEDULED', // 已安排（本期不产生）
  IN_PROGRESS = 'IN_PROGRESS', // 进行中
  COMPLETED = 'COMPLETED', // 已完成（正式评估后）
  CANCELLED = 'CANCELLED', // 已取消
}

/** Offer状态枚举（对齐后端 OfferStatus） */
export enum OfferStatus {
  SENT = 'SENT', // 已发送
  ACCEPTED = 'ACCEPTED', // 已接受
  REJECTED = 'REJECTED', // 已拒绝
  EXPIRED = 'EXPIRED', // 已过期
  WITHDRAWN = 'WITHDRAWN', // 已撤回（原 RETRACTED）
}

/**
 * 该投递是否被 Offer 阻止再次发起。
 * 业务规则：撤回/过期后**可以**重新发起；已发送/已接受/已拒绝**不可**再发。
 * @param offerStatus 最近一条 Offer 状态（后端 candidates/list 的 lastOfferStatus，未返回则回退旧契约 hasOfferRecord）
 */
export const isOfferBlocked = (
  offerStatus?: OfferStatus | null,
  hasOfferRecord?: boolean,
): boolean => {
  if (offerStatus) {
    return (
      offerStatus === OfferStatus.SENT ||
      offerStatus === OfferStatus.ACCEPTED ||
      offerStatus === OfferStatus.REJECTED
    );
  }
  return !!hasOfferRecord;
};

/** 学历要求枚举 */
export enum EducationRequirement {
  NONE = 'NONE',
  COLLEGE = 'COLLEGE',
  BACHELOR = 'BACHELOR',
  MASTER = 'MASTER',
  DOCTOR = 'DOCTOR',
}

/** 求职状态枚举 */
export enum JobSeekingStatus {
  LOOKING = 'LOOKING',
  OPEN = 'OPEN',
  NOT_LOOKING = 'NOT_LOOKING',
}

/** 通知类型枚举（与后端契约对齐；求职者通知页使用 apiTypes 的 NotificationType） */
export enum NotificationType {
  RESUME_VIEWED = 'RESUME_VIEWED',
  INTERVIEW_INVITE = 'INTERVIEW_INVITE',
  INTERVIEW_REMIND = 'INTERVIEW_REMIND',
  OFFER_RECEIVED = 'OFFER_RECEIVED',
  SYSTEM = 'SYSTEM',
  JOB_RECOMMEND = 'JOB_RECOMMEND',
}

/** 岗位暂停原因枚举 */
export enum PauseReason {
  HC_RESERVED_FULL = 'HC_RESERVED_FULL',
}

/** 岗位关闭原因枚举 */
export enum CloseReason {
  MANUAL = 'MANUAL',
  VIOLATION = 'VIOLATION',
  EXPIRED = 'EXPIRED',
  HC_CONFIRMED_FULL = 'HC_CONFIRMED_FULL',
}

/** 岗位画像来源枚举 */
export enum ProfileSource {
  AI = 'AI',
  MANUAL = 'MANUAL',
  MIXED = 'MIXED',
}

/** 置信度枚举 */
export enum ConfidenceLevel {
  LOW = 'LOW',
  MEDIUM = 'MEDIUM',
  HIGH = 'HIGH',
}

/** 匹配评分状态枚举 */
export enum MatchScoreStatus {
  READY = 'READY',
  PROFILE_MISSING = 'PROFILE_MISSING',
  INSUFFICIENT_DATA = 'INSUFFICIENT_DATA',
}

/** 面试题目类型枚举 */
export enum QuestionType {
  BASIC = 'BASIC',
  PROJECT = 'PROJECT',
  BOUNDARY = 'BOUNDARY',
  COMPREHENSIVE = 'COMPREHENSIVE',
}

/** 面试题目难度枚举 */
export enum QuestionDifficulty {
  EASY = 'EASY',
  MEDIUM = 'MEDIUM',
  HARD = 'HARD',
}

/** 企业题库题目状态枚举（对齐后端 QuestionStatusEnum，唯一权威定义） */
export enum QuestionStatus {
  PENDING_REVIEW = 'PENDING_REVIEW', // 待审核（预置能力，阶段6.3 AI 导入题起有真实来源）
  ACTIVE = 'ACTIVE', // 已启用（HR 手工新建固定此状态）
  INACTIVE = 'INACTIVE', // 已停用
  REJECTED = 'REJECTED', // 已拒绝（编辑后回 PENDING_REVIEW）
}

/** 企业题库题目来源枚举（对齐后端 QuestionSourceEnum，唯一权威定义） */
export enum QuestionSource {
  HR_CREATED = 'HR_CREATED', // HR 手工创建
  AI_GENERATED = 'AI_GENERATED', // AI 生成
}
