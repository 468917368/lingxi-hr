import axios from 'axios';
import request from '@/utils/request';
import { tokenManager } from '@/utils/token';

// 裸 axios 实例，不走业务拦截器，用于文件下载
const rawRequest = axios.create({
  baseURL: '/api',
  timeout: 10000,
});

// 只注入 token，不做 JSON 解析
rawRequest.interceptors.request.use((config) => {
  const token = tokenManager.getToken();
  if (token) {
    config.headers.Authorization = `Bearer ${token}`;
  }
  return config;
});

// ===== 数据看板 =====

/** 获取核心指标统计 */
export async function getAdminStats() {
  return request.get('/v1/admin/dashboard/stats');
}

/** 获取投递趋势数据 */
export async function getDashboardTrend(params?: { days?: number }) {
  return request.get('/v1/admin/dashboard/trend', { params });
}

/** 获取岗位类型分布 */
export async function getDashboardDistribution() {
  return request.get('/v1/admin/dashboard/job-distribution');
}

/** 获取企业活跃度排行 */
export async function getDashboardCompanyRank() {
  return request.get('/v1/admin/dashboard/company-rank');
}

/** 导出报表 */
export async function exportDashboard(params: { type: string; startDate?: string; endDate?: string }) {
  return rawRequest.get('/v1/admin/dashboard/export', { params, responseType: 'blob' });
}

// ===== 企业认证审核 =====

/** 获取审核列表 */
export async function getCertificationList(params?: {
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) {
  return request.get('/v1/admin/certifications', { params });
}

/** 获取认证详情（含企业信息、营业执照、认证材料等完整字段） */
export async function getCertificationDetail(id: number) {
  return request.get(`/v1/admin/certifications/${id}`);
}

/** 通过审核 */
export async function approveCertification(id: number) {
  return request.post(`/v1/admin/certifications/${id}/approve`);
}

/** 拒绝审核 */
export async function rejectCertification(id: number, reason: string) {
  return request.post(`/v1/admin/certifications/${id}/reject`, { reason });
}

// ===== 企业管理 =====

/** 获取企业列表 */
export async function getCompanyList(params?: {
  certStatus?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) {
  return request.get('/v1/admin/companies', { params });
}

/** 获取企业详情 */
export async function getCompanyDetail(id: number) {
  return request.get(`/v1/admin/companies/${id}`);
}

// ===== HR管理 =====

/** 获取HR列表（按企业分组） */
export async function getHRList(params?: { keyword?: string }) {
  return request.get('/v1/admin/hr', { params });
}

/** 禁用HR */
export async function disableHR(id: number) {
  return request.put(`/v1/admin/hr/${id}/disable`);
}

/** 启用HR */
export async function enableHR(id: number) {
  return request.put(`/v1/admin/hr/${id}/enable`);
}

// ===== 面试官管理 =====

/** 获取面试官列表（按企业分组） */
export async function getInterviewerList(params?: { keyword?: string }) {
  return request.get('/v1/admin/interviewers', { params });
}

/** 禁用面试官 */
export async function disableInterviewer(id: number) {
  return request.put(`/v1/admin/interviewers/${id}/disable`);
}

/** 启用面试官 */
export async function enableInterviewer(id: number) {
  return request.put(`/v1/admin/interviewers/${id}/enable`);
}

// ===== 岗位管理 =====

/** 获取岗位列表（按企业分组） */
export async function getAdminJobList(params?: { status?: string; keyword?: string }) {
  return request.get('/v1/admin/jobs', { params });
}

/** 下架岗位 */
export async function offlineJob(id: number) {
  return request.put(`/v1/admin/jobs/${id}/offline`);
}

// ===== 求职者管理 =====

/** 获取求职者列表 */
export async function getAdminCandidateList(params?: {
  status?: string;
  keyword?: string;
  page?: number;
  size?: number;
}) {
  return request.get('/v1/admin/candidates', { params });
}

/** 获取候选人投递历史 */
export async function getCandidateApplications(id: number, params?: { page?: number; size?: number }) {
  return request.get(`/v1/admin/candidates/${id}/applications`, { params });
}

/** 禁用求职者 */
export async function disableCandidate(id: number) {
  return request.put(`/v1/admin/candidates/${id}/disable`);
}

/** 启用求职者 */
export async function enableCandidate(id: number) {
  return request.put(`/v1/admin/candidates/${id}/enable`);
}

// ===== 系统公告 =====

/** 获取公告列表 */
export async function getAnnouncementList(params?: { page?: number; size?: number }) {
  return request.get('/v1/admin/announcements', { params });
}

/** 创建公告 */
export async function createAnnouncement(data: {
  title: string;
  targetRole: string;
  content: string;
  status?: string;
}) {
  return request.post('/v1/admin/announcements', data);
}

/** 编辑公告 */
export async function updateAnnouncement(id: number, data: {
  title: string;
  targetRole: string;
  content: string;
  status?: string;
}) {
  return request.put(`/v1/admin/announcements/${id}`, data);
}

/** 发布公告 */
export async function publishAnnouncement(id: number) {
  return request.patch(`/v1/admin/announcements/${id}/publish`);
}

/** 撤回公告 */
export async function withdrawAnnouncement(id: number) {
  return request.patch(`/v1/admin/announcements/${id}/withdraw`);
}

// ===== 操作日志 =====

/** 获取操作日志 */
export async function getAuditLogs(params?: {
  type?: string;
  startDate?: string;
  endDate?: string;
  page?: number;
  size?: number;
}) {
  return request.get('/v1/admin/audit-logs', { params });
}

/** 导出操作日志 Excel */
export async function exportAuditLogs(params?: {
  type?: string;
  startDate?: string;
  endDate?: string;
}) {
  return rawRequest.get('/v1/admin/audit-logs/export', {
    params,
    responseType: 'blob',
  });
}

// ===== 系统配置 =====

/** 获取系统配置 */
export async function getSystemConfig() {
  return request.get('/v1/admin/config');
}

/** 更新系统配置 */
export async function updateSystemConfig(data: { configKey: string; configValue: string }[]) {
  return request.put('/v1/admin/config', data);
}
