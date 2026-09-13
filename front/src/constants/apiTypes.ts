import type { UserRole, ApplicationStatus, InterviewStatus, OfferStatus } from '@/constants/enums';
export type { UserRole };

/** 登录响应 */
export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  expiresIn: number;
  user: UserInfo;
}

/** 用户信息 */
export interface UserInfo {
  id: number;
  phone: string;
  name: string;
  role: UserRole;
  avatar: string | null;
  email?: string;
  nameUpdatedAt?: string; // 姓名最后修改时间
  companyId: number | null;
  company: CompanyInfo | null;
  profile: UserProfile | null;
}

/** 用户画像 */
export interface UserProfile {
  gender: string | null;
  city: string | null;
  workYears: string | null;
  education: string | null;
  jobStatus: string | null;
  desiredJob: string | null;
  desiredCity: string | null;
  desiredSalaryMin: number | null;
  desiredSalaryMax: number | null;
  availableFrom: string | null;
  resumePublic: boolean;
  blindMode: boolean;
}

/** 企业信息 */
export interface CompanyInfo {
  id: number;
  name: string;
  certStatus: CertStatus;
}

/** 认证状态 */
export type CertStatus = 'PENDING' | 'APPROVED' | 'REJECTED';

/** 发送验证码请求 */
export interface SendCodeRequest {
  phone: string;
}

/** 注册请求 */
export interface RegisterRequest {
  phone: string;
  code: string;
  password: string;
  name: string;
  role: 'CANDIDATE' | 'HR';
}

/** HR注册请求（角色固定为HR，走 lingxi-hr） */
export interface HrRegisterRequest {
  phone: string;
  code: string;
  password: string;
  name: string;
}

/** 验证码登录请求 */
export interface LoginByCodeRequest {
  phone: string;
  code: string;
}

/** 密码登录请求 */
export interface LoginByPasswordRequest {
  phone: string;
  password: string;
}

/** 修改密码请求 */
export interface ChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
}

/** HR 账号信息 */
export interface HrAccountProfile {
  id: number;
  name: string;
  phone: string;
  email: string;
  avatar: string | null;
  department: string;
  companyName: string;
  role: string;
}

/** 更新 HR 账号信息（至少传一个字段） */
export interface UpdateHrAccountProfileRequest {
  name?: string;
  avatar?: string;
  department?: string;
}

/** HR 修改密码请求 */
export interface HrChangePasswordRequest {
  oldPassword: string;
  newPassword: string;
  confirmPassword: string;
}

/** 发送手机号验证码（HR 账号，改绑新手机号） */
export interface HrSendPhoneCodeRequest {
  newPhone: string;
}

/** 验证并修改手机号（HR 账号） */
export interface HrChangePhoneRequest {
  newPhone: string;
  code: string;
}

/** 发送邮箱验证码（HR 账号，改绑新邮箱） */
export interface HrSendEmailCodeRequest {
  email: string;
}

/** 验证并更新邮箱（HR 账号） */
export interface HrVerifyEmailRequest {
  email: string;
  code: string;
}

/** 刷新Token请求 */
export interface RefreshTokenRequest {
  refreshToken: string;
}

/** 管理员登录请求 */
export interface AdminLoginRequest {
  username: string;
  password: string;
}

/** 用户信息更新请求 */
export interface UpdateUserInfoRequest {
  name?: string;
  avatar?: string;
  gender?: 'MALE' | 'FEMALE';
  city?: string;
  workYears?: string;
  education?: string;
  jobStatus?: string;
  email?: string;
}

/** 求职意向更新请求 */
export interface UpdateProfileRequest {
  desiredJob?: string;
  desiredCity?: string;
  desiredSalaryMin?: number;
  desiredSalaryMax?: number;
  availableFrom?: string;
}

/** 隐私设置 */
export interface PrivacySettings {
  resumePublic: boolean;
  matchNotify: boolean;
  jobStatus: string;
  blindMode: boolean;
}

/** 登录日志 */
export interface LoginLog {
  id: number;
  loginIp: string;
  loginDevice: string;
  loginLocation: string;
  loginTime: string;
  loginStatus: number; // 1=成功 0=失败
  failReason: string | null;
}

/** 分页结果 */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

// ==================== Mock Interview ====================
export type MockQuestionType = 'BASIC' | 'PROJECT' | 'BOUNDARY' | 'COMPREHENSIVE';
export type MockDifficulty = 'EASY' | 'MEDIUM' | 'HARD';
export type MockProgressCode = 'FETCH_JOB' | 'FETCH_RESUME' | 'GENERATING';

/** 模拟面试题目 */
export interface MockQuestion {
  questionNumber: number;
  questionType: MockQuestionType;
  dimension: string;
  difficulty: MockDifficulty;
  content: string;
}

/** 出题进度事件 */
export interface GenerateProgress {
  sequence: number;
  code: MockProgressCode;
  message: string;
}

/** 生成题目请求 */
export interface GenerateRequest {
  jobId?: number;
  jobTitle?: string;
  questionCount: number;
  resumeId?: number;
}

/** 生成题目结果 */
export interface GenerateResult {
  sessionId: string;
  questions: MockQuestion[];
  resumeUsed: boolean;
}

/** 单题评分结果 */
export interface AnswerScore {
  questionNumber: number;
  techAccuracyScore: number;
  expressionScore: number;
  knowledgeDepthScore: number;
  overallScore: number;
  aiComment: string;
}

/** 题目维度均分 */
export interface DimensionScore {
  name: string;
  score: number;
}

/** 模拟面试报告 */
export interface MockInterviewReport {
  overallScore: number;
  overallLevel: string;
  highlights: string[];
  weaknesses: string[];
  improvementPlan: string;
  /** 各维度均分（字段名以联调为准，做归一化兜底） */
  dimensionScores?: DimensionScore[];
  /** 统计信息 */
  statistics?: {
    totalCount?: number;
    answeredCount?: number;
    skippedCount?: number;
    avgTechAccuracy?: number;
    avgExpression?: number;
    avgKnowledgeDepth?: number;
    [key: string]: unknown;
  };
  /** 逐题回顾 */
  questionReviews?: Array<{
    questionNumber: number;
    content: string;
    userAnswer?: string;
    score?: number;
    aiComment?: string;
  }>;
}

// ========== 消息服务相关类型 ==========

/** 会话用户信息 */
export interface ConversationUser {
  id: number;
  name: string;
  avatar: string | null;
  role: UserRole;
  company: string | null;
  isOnline?: boolean;
}

/** 会话信息 */
export interface Conversation {
  id: string;
  targetUser: ConversationUser;
  lastMessage: string | null;
  lastMessageTime: string | null;
  unreadCount: number;
  isTop: boolean;
  isMuted: boolean;
}

/** 创建会话请求 */
export interface CreateConversationRequest {
  companyId: number;
  candidateId: number;
  hrId: number;
  applicationId?: string; // 投递记录 ID，雪花 ID 用字符串
}

/** 消息类型 */
export type MessageType = 'TEXT' | 'RICH_TEXT' | 'IMAGE' | 'FILE';

/** 内容类型 */
export type ContentType = 'TEXT' | 'IMAGE' | 'FILE' | 'SYSTEM' | 'CARD_RESUME' | 'CARD_JOB';

/** 消息状态 */
export type MessageStatus = 'SENT' | 'DELIVERED' | 'READ' | 'FAILED';

/** 消息信息 */
export interface Message {
  id: string;
  senderId: number;
  senderName: string;
  senderAvatar: string | null;
  msgType: MessageType;
  contentType: ContentType;
  content: string;
  mediaUrl: string | null;
  fileName: string | null;
  fileSize: number | null;
  isRead: boolean;
  status?: MessageStatus;
  createdAt: string;
}

/** 发送消息请求 */
export interface SendMessageRequest {
  msgType?: MessageType;
  contentType: ContentType;
  content: string;
  mediaUrl?: string;
  fileName?: string;
  fileSize?: number;
}

/** 文件上传响应 */
export interface FileUploadResult {
  fileUrl: string;
  fileName: string;
  fileSize: number;
}

// ========== 通知服务相关类型 ==========

/** 通知类型（与后端契约对齐：简历/面试/Offer 使用细分类型） */
export type NotificationType =
  | 'RESUME_VIEWED'        // 投递通知（简历被查看、筛选通过/未通过）
  | 'INTERVIEW_INVITE'     // 面试邀请
  | 'INTERVIEW_REMIND'     // 面试提醒
  | 'OFFER_RECEIVED'       // Offer通知（收到Offer、待确认）
  | 'SYSTEM'               // 系统通知（平台公告、账号安全）
  | 'JOB_RECOMMEND';       // 岗位推荐（AI推荐匹配岗位）

/** 通知信息 */
export interface Notification {
  id: number;
  /** 通知类型（不同角色类型集不同：C端见 NotificationType，HR 端为 NEW_APPLICATION 等，故用 string） */
  type: string;
  typeDesc: string;
  title: string;
  content: string;
  /** 跳转目标类型（后端为小写：application/offer/interview/job，系统通知为 null） */
  targetType: string | null;
  targetId: number | null;
  isRead: boolean;
  createdAt: string;
}

/** 未读通知数 */
export interface UnreadNotificationCount {
  totalCount: number;
  // C端（求职者）- 与后端字段对齐
  resumeViewedCount?: number;      // 简历被查看
  interviewInviteCount?: number;   // 面试邀请
  offerReceivedCount?: number;     // Offer通知
  jobRecommendCount?: number;      // 岗位推荐
  // B端（HR）
  newApplicationCount?: number;
  interviewScheduleCount?: number;
  offerManageCount?: number;
  hcWarningCount?: number;
  companyCertCount?: number;
  talentRecommendCount?: number;   // 人才推荐
  // 通用
  systemCount: number;
}

// ========== HR 候选人管理 ==========

/** 标记操作：SUITABLE=合适 / UNSUITABLE=不合适 */
export type CandidateMarkAction = 'SUITABLE' | 'UNSUITABLE';

/** 候选人投递记录（列表项，id = applicationId，标记用这个；雪花 ID 用字符串） */
export interface CandidateListItem {
  id: string;
  candidateId: number;
  candidateName: string;
  phone: string;
  avatar: string | null;
  jobId: number;
  jobTitle: string;
  status: ApplicationStatus;
  matchScore: number;
  aiScore: number | null; // C 侧预留，恒 null，不展示
  appliedAt: string;
  /** 该投递最近一条 Offer 状态（后端新增；无 Offer 或撤回/过期后可再发，已发/已接受/已拒绝则阻止再发） */
  lastOfferStatus?: OfferStatus | null;
  /** 该投递是否已存在 Offer 记录（旧契约：存在任意 Offer 记录含终态；新契约以后端 lastOfferStatus 为准） */
  hasOfferRecord?: boolean;
}

/** Top5 高潜推荐项 */
export interface TopCandidate {
  rank: number;
  candidateId: number;
  candidateName: string;
  matchScore: number;
  aiScore: number | null;
  /** 投递记录ID（后端 2026-08-06 补，前端「查看简历」直接用它调简历接口） */
  applicationId: string;
  advantages: string[] | null; // ⚠️ 数据库无此字段，前端不展示（Top5 卡片已去掉标签）
  risks: string[] | null;
}

/** 标记结果 */
export interface MarkCandidateResult {
  applicationId: string;
  newStatus: ApplicationStatus;
  newStatusDesc: string;
  notificationSent: boolean;
}

// ========== 面试协同 ==========

export type InterviewMethod = 'OFFLINE' | 'ONLINE' | 'PHONE';
export type InterviewConclusion = 'PASS' | 'PENDING' | 'REJECT';
export type InterviewDateRange = 'TODAY' | 'WEEK' | 'MONTH';

/** 面试列表项 */
export interface InterviewVO {
  interviewId: string;
  applicationId: string;
  jobId: number; // 岗位ID（出题 SSE 路径 {jobId}）
  candidateName: string;
  candidateAvatar: string | null;
  jobTitle: string;
  interviewerName: string;
  scheduledAt: string; // ISO 8601
  method: InterviewMethod;
  methodDesc: string;
  location: string | null;
  remark: string | null;
  status: InterviewStatus;
  statusDesc: string; // 后端枚举 desc，直接展示
  hasQuestions: boolean; // 本期恒 false
  hasEvaluation: boolean; // 是否已有评估记录（含草稿与否见后端语义）
}

/** 创建面试请求 */
export interface InterviewCreateDTO {
  applicationId: string;
  interviewerId: number;
  scheduledAt: string; // 如 2026-08-07T14:00:00
  method: InterviewMethod;
  location?: string;
  remark?: string;
  candidateNote?: string;
}

/** 待评估项 */
export interface PendingEvaluationVO {
  interviewId: string;
  candidateName: string;
  jobTitle: string;
  scheduledAt: string;
  isOverdue: boolean; // scheduledAt 距今 >24h
}

/** 评估入参 */
export interface EvaluationDTO {
  conclusion: InterviewConclusion;
  techScore: number;
  communicationScore: number;
  matchScore: number;
  potentialScore: number;
  comment: string;
  isDraft?: boolean; // true=草稿（默认 false）
}

/** 评估出参 */
export interface EvaluationResultVO {
  evaluationId: string;
  conclusion: InterviewConclusion;
  feedback: string | null; // 本期 null
  applicationStatus: ApplicationStatus;
  notificationSent: boolean;
}

/** 评估详情（查看评估） */
export interface EvaluationDetailVO {
  interviewId: string;
  conclusion: InterviewConclusion;
  techScore: number;
  communicationScore: number;
  matchScore: number;
  potentialScore: number;
  comment: string;
  feedback: string | null;
  isDraft: boolean;
  evaluatorName: string;
  createdAt: string;
}

// ==================== Offer 管理 ====================

/** Offer 列表筛选参数（dateRange 按 createdAt 过滤，后端新增） */
export interface OfferListParams {
  status?: OfferStatus;
  jobId?: number;
  dateRange?: InterviewDateRange;
  page?: number;
  size?: number;
}

/** Offer 列表项（offerId 为字符串，后端 ToStringSerializer） */
export interface OfferVO {
  offerId: string;
  applicationId: string;
  candidateId: number;
  candidateName: string;
  jobId: number;
  jobTitle: string;
  salary: number; // 月薪（元）
  entryDate: string; // YYYY-MM-DD
  level: string | null;
  status: OfferStatus;
  statusDesc: string;
  expiresAt: string; // ISO
  urgeCount: number;
  rejectReason: string | null;
  acceptedAt: string | null;
  rejectedAt: string | null;
  createdAt: string; // ISO
}

/** HC 概览（不传 jobId 时为全公司聚合，jobTitle="全公司"） */
export interface HcOverviewVO {
  jobId: number | null;
  jobTitle: string;
  totalHc: number;
  reservedHc: number;
  confirmedHc: number;
  availableHc: number;
}

/** 发起 Offer 入参 */
export interface OfferCreateDTO {
  applicationId: string;
  salary: number; // 月薪（元）
  entryDate: string; // YYYY-MM-DD
  level?: string;
  remark?: string;
  expiresInDays?: number; // 默认 3
}

/** 发起 Offer 出参 */
export interface OfferCreateResultVO {
  offerId: string;
  status: OfferStatus;
  expiresAt: string;
  notificationSent: boolean;
  salaryWarning: { warn: boolean; message: string } | null;
}
