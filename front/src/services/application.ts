import request from '@/utils/request';
import type { PageResult } from '@/constants/apiTypes';

// ==================== 类型定义（对齐后端 ApplicationController VO） ====================

/**
 * 投递列表项（ApplicationVO）
 * 注意：id 为字符串——后端雪花 ID（19 位）超出 JS Number 安全整数范围，
 * 后端已对 id 做字符串序列化（@JsonSerialize ToStringSerializer）。
 */
export interface ApplicationListItem {
  id: string;
  jobId: number;
  /** 岗位名称（JOIN job_post） */
  jobTitle: string;
  /** 城市展示名（JOIN job_post） */
  cityName: string;
  /** 企业名称（JOIN hr_company） */
  companyName: string;
  /** 企业 Logo（JOIN hr_company，可为 null） */
  companyLogo: string | null;
  /** 投递状态：SUBMITTED/VIEWED/SCREENED/INTERVIEWING/OFFERABLE/OFFERED/OFFER_ACCEPTED/OFFER_DECLINED/REJECTED/WITHDRAWN */
  status: string;
  /** 投递时间 */
  submittedAt: string;
  /** 更新时间 */
  updatedAt: string;
}

/** 状态时间线项（ApplicationTimelineVO，resume_status_log 正序） */
export interface ApplicationTimelineItem {
  /** 原状态（首次投递时为 null） */
  fromStatus: string | null;
  /** 目标状态 */
  toStatus: string;
  /** 操作人角色：CANDIDATE/HR/SYSTEM */
  operatorRole: string;
  /** 变更原因 */
  reason: string | null;
  /** 变更时间 */
  createdAt: string;
}

/** 投递详情（ApplicationDetailVO，含状态时间线，仅本人可查） */
export interface ApplicationDetail {
  id: string;
  jobId: number;
  jobTitle: string;
  cityName: string;
  companyId: number;
  companyName: string;
  companyLogo: string | null;
  /** 使用的简历 ID */
  resumeId: string;
  status: string;
  /** 投递匹配度(0-100)，Job Agent 投递时计算并快照；未接入模块A时可能为空 */
  matchScore: number | null;
  submittedAt: string;
  viewedAt: string | null;
  screenedAt: string | null;
  interviewingAt: string | null;
  offerableAt: string | null;
  offeredAt: string | null;
  rejectedAt: string | null;
  withdrawnAt: string | null;
  /** 状态时间线（正序） */
  timeline: ApplicationTimelineItem[];
}

/** 一键投递请求（ApplyRequestDTO：jobId 必传，resumeId 可选为空则用默认简历） */
export interface SubmitApplicationRequest {
  jobId: number;
  resumeId?: string;
}

// ==================== API ====================

/** 获取投递列表（分页，仅当前候选人数据） */
export async function getApplicationList(params?: {
  page?: number;
  pageSize?: number;
}): Promise<PageResult<ApplicationListItem>> {
  return request.get('/v1/applications', { params });
}

/** 获取投递详情（含状态时间线，仅本人可查） */
export async function getApplicationDetail(id: string): Promise<ApplicationDetail> {
  return request.get(`/v1/applications/${id}`);
}

/** 一键投递（jobId 必传，resumeId 为空则后端用默认简历） */
export async function submitApplication(data: SubmitApplicationRequest): Promise<ApplicationListItem> {
  return request.post('/v1/applications', data);
}

/** 撤回投递（仅 SUBMITTED/VIEWED/SCREENED 可撤回，面试安排后不可撤回） */
export async function withdrawApplication(id: string): Promise<void> {
  return request.put(`/v1/applications/${id}/withdraw`);
}

/** 接受Offer（仅 OFFERED 待录用状态，终态 OFFER_ACCEPTED） */
export async function acceptOffer(id: string): Promise<void> {
  return request.put(`/v1/applications/${id}/offer/accept`);
}

/** 拒绝Offer（仅 OFFERED 待录用状态，终态 OFFER_DECLINED；rejectReason 选填，透传至 hr_offer） */
export async function declineOffer(id: string, rejectReason?: string): Promise<void> {
  return request.put(
    `/v1/applications/${id}/offer/decline`,
    rejectReason ? { rejectReason } : undefined,
  );
}
