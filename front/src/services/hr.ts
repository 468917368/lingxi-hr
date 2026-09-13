import request from '@/utils/request';
import type { ApplicationStatus, InterviewStatus } from '@/constants/enums';
import type {
  CandidateListItem,
  CandidateMarkAction,
  EvaluationDTO,
  EvaluationDetailVO,
  EvaluationResultVO,
  HcOverviewVO,
  HrAccountProfile,
  HrChangePasswordRequest,
  InterviewCreateDTO,
  InterviewDateRange,
  InterviewMethod,
  InterviewVO,
  MarkCandidateResult,
  OfferCreateDTO,
  OfferCreateResultVO,
  OfferListParams,
  OfferVO,
  PageResult,
  PendingEvaluationVO,
  TopCandidate,
  UpdateHrAccountProfileRequest,
} from '@/constants/apiTypes';
import type { HRPublicInfo } from '@/components/UserProfileCard';
import type { ResumeDetail } from '@/services/resume';

// ==================== HR公开信息 ====================
/** 获取HR公开信息（包含公司信息，供求职者点击HR头像展示） */
export async function getHRPublicInfo(hrId: number): Promise<HRPublicInfo> {
  return request.get(`/v1/hr/${hrId}/public`);
}

// Placeholder: 接口待后端联调时实现

// Dashboard
export async function getDashboardStats() {
  return request.get('/v1/hr/dashboard/stats');
}

export async function getDashboardTrend(params: any) {
  return request.get('/v1/hr/dashboard/trend', { params });
}

// Candidates
/** 候选人列表查询参数 */
export interface CandidateListParams {
  jobId?: number;
  status?: ApplicationStatus;
  minMatchScore?: number;
  keyword?: string;
  sortBy?: 'matchScore' | 'submittedAt'; // aiScore 后端仍按 matchScore 排，前端不提供
  page?: number;
  size?: number;
}

/** HR 岗位列表项（岗位下拉数据源，与候选人 jobId 同源） */
export interface HrJobListItem {
  jobId: number;
  title: string;
  status?: string;
}

/** HR 岗位列表（岗位下拉数据源，与候选人 jobId 同源） */
export async function getHrJobList(
  params: { page?: number; size?: number } = {},
): Promise<PageResult<HrJobListItem>> {
  return request.get('/v1/hr/jobs', { params: { page: 1, size: 50, ...params } });
}

/** 候选人列表 */
export async function getCandidateList(
  params: CandidateListParams,
): Promise<PageResult<CandidateListItem>> {
  return request.get('/v1/hr/candidates/list', { params });
}

/** Top5 高潜推荐 */
export async function getTop5Candidates(params: { jobId?: number }): Promise<TopCandidate[]> {
  return request.get('/v1/hr/candidates/top5', { params });
}

/** 标记合适/不合适 */
export async function markCandidate(
  applicationId: string,
  action: CandidateMarkAction,
): Promise<MarkCandidateResult> {
  return request.put(`/v1/hr/candidates/${applicationId}/mark`, { action });
}

/** 查看候选人简历（HR 人才库） */
export async function getCandidateResume(applicationId: string): Promise<ResumeDetail> {
  return request.get(`/v1/hr/candidates/${applicationId}/resume`);
}

/** 根据用户ID查看候选人简历（人才推荐用） */
export async function getCandidateResumeByUserId(userId: string): Promise<ResumeDetail> {
  return request.get(`/v1/hr/candidates/user/${userId}/resume`);
}

// Interviews
/** 面试列表查询参数 */
export interface InterviewListParams {
  status?: InterviewStatus;
  dateRange?: InterviewDateRange;
  jobId?: number;
  interviewerId?: number; // 面试官端「我的面试」传当前用户 id
  method?: InterviewMethod; // 面试方式筛选
  keyword?: string; // 候选人姓名模糊搜索（需后端支持）
  page?: number;
  size?: number;
}

/** 面试列表 */
export async function getInterviewList(
  params: InterviewListParams,
): Promise<PageResult<InterviewVO>> {
  return request.get('/v1/hr/interviews', { params });
}

/** 创建面试 */
export async function createInterview(dto: InterviewCreateDTO): Promise<InterviewVO> {
  return request.post('/v1/hr/interviews', dto);
}

/** 开始面试 */
export async function startInterview(interviewId: string): Promise<void> {
  return request.post(`/v1/hr/interviews/${interviewId}/start`);
}

/** 取消面试 */
export async function cancelInterview(interviewId: string): Promise<void> {
  return request.post(`/v1/hr/interviews/${interviewId}/cancel`);
}

/** 待评估列表（IN_PROGRESS 且无正式评估） */
export async function getPendingEvaluations(): Promise<PendingEvaluationVO[]> {
  return request.get('/v1/hr/interviews/pending-evaluations');
}

/** 录入/保存评估（isDraft=true 存草稿） */
export async function submitEvaluation(
  interviewId: string,
  dto: EvaluationDTO,
): Promise<EvaluationResultVO> {
  return request.put(`/v1/hr/interviews/${interviewId}/evaluation`, dto);
}

/** 查看评估（无记录 404，silent 避免拦截器弹「暂无评估记录」误导 toast，由页面空表单兜底） */
export async function getInterviewEvaluation(interviewId: string): Promise<EvaluationDetailVO> {
  return request.get(`/v1/hr/interviews/${interviewId}/evaluation`, { silent: true });
}

// ==================== Offers ====================
/** Offer 列表（路径 /offers，非 /list；offerId 为字符串） */
export async function getOfferList(
  params: OfferListParams,
): Promise<PageResult<OfferVO>> {
  return request.get('/v1/hr/offers', { params });
}

/** HC 概览（jobId 可空，全公司聚合） */
export async function getHcOverview(jobId?: number): Promise<HcOverviewVO> {
  return request.get('/v1/hr/offers/hc-overview', { params: { jobId } });
}

/** 发起 Offer */
export async function createOffer(dto: OfferCreateDTO): Promise<OfferCreateResultVO> {
  return request.post('/v1/hr/offers', dto);
}

/** 催促确认 */
export async function urgeOffer(offerId: string): Promise<void> {
  return request.post(`/v1/hr/offers/${offerId}/urge`);
}

/** 撤回 Offer */
export async function retractOffer(offerId: string): Promise<void> {
  return request.post(`/v1/hr/offers/${offerId}/retract`);
}

// ==================== 账号信息（个人中心） ====================
/** 查询账号信息 */
export async function getHrAccountProfile(): Promise<HrAccountProfile> {
  return request.get('/v1/hr/account/profile');
}

/** 更新账号信息（name/avatar/department，至少一个字段；silent 以便页面按 1114 等错误码差异化提示） */
export async function updateHrAccountProfile(data: UpdateHrAccountProfileRequest): Promise<void> {
  return request.put('/v1/hr/account/profile', data, { silent: true });
}

/** 修改密码（成功即 token 失效，需重新登录） */
export async function changeHrPassword(data: HrChangePasswordRequest): Promise<void> {
  return request.put('/v1/hr/account/password', data);
}

/** 发送手机号验证码（改绑新手机号） */
export async function sendHrPhoneCode(newPhone: string): Promise<void> {
  return request.post('/v1/hr/account/phone/send-code', { newPhone });
}

/** 验证并修改手机号（成功即 token 失效，需重新登录） */
export async function changeHrPhone(newPhone: string, code: string): Promise<void> {
  return request.post('/v1/hr/account/phone', { newPhone, code });
}

/** 发送邮箱验证码（改绑新邮箱） */
export async function sendHrEmailCode(email: string): Promise<void> {
  return request.post('/v1/hr/account/email/send-code', { email });
}

/** 验证并更新邮箱 */
export async function changeHrEmail(email: string, code: string): Promise<void> {
  return request.post('/v1/hr/account/email', { email, code });
}
