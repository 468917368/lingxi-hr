import request from '@/utils/request';
import type { CertStatus } from '@/constants/apiTypes';

/** 邀请码加入结果（lingxi-hr HrJoinCompanyVO） */
export interface JoinResult {
  companyId: number;
  companyName: string;
  role: string;
  status: string;
}

/** 企业信息（lingxi-hr HrCompanyVO） */
export interface HrCompanyInfo {
  id: number;
  name: string;
  shortName: string | null;
  description: string | null;
  industry: string;
  scale: string;
  logoUrl: string | null;
  address: string | null;
  website: string | null;
  inviteCode: string;
  certStatus: CertStatus;
  certRejectReason: string | null;
  status: string;
  createdAt: string;
}

/** 企业成员（lingxi-hr HrMemberVO） */
export interface HrMemberItem {
  id: number;
  userId: number;
  name: string;
  phone: string;
  avatar: string | null;
  role: string; // HR_ADMIN / INTERVIEWER
  department: string;
  techDirection: string | null;
  interviewCount: number;
  status: string;
  createdAt: string;
}

/** 添加成员（lingxi-hr HrMemberDTO，创建面试官） */
export interface CreateMemberRequest {
  phone: string;
  code: string;
  password: string;
  name: string;
  department: string;
  techDirection?: string;
  email?: string;
}

/**
 * 提交企业认证申请（HR 首次入驻）
 * multipart：name/industry/scale/address 等企业字段 + businessLicense(选填) + certMaterial(选填)
 * 后端 lingxi-hr 创建企业 + 创始人成员 + 认证申请，返回 Void
 */
export async function applyCertification(formData: FormData): Promise<void> {
  return request.post('/v1/hr/company/certification', formData);
}

/** 邀请码加入企业 */
export async function joinByInvite(inviteCode: string): Promise<JoinResult> {
  return request.post('/v1/hr/company/members/join-by-invite', { inviteCode });
}

/** 认证状态（lingxi-hr 返回字段） */
interface HrCertificationStatusVO {
  certStatus: CertStatus;
  certRejectReason: string | null;
  submittedAt?: string;
}

/** 获取认证状态 */
export async function getCertificationStatus(): Promise<{
  status: CertStatus;
  rejectReason?: string;
}> {
  const res = (await request.get('/v1/hr/company/certification/status')) as HrCertificationStatusVO;
  return {
    status: res.certStatus,
    rejectReason: res.certRejectReason ?? undefined,
  };
}

/** 获取企业信息 */
export async function getCompanyInfo(): Promise<HrCompanyInfo> {
  return request.get('/v1/hr/company/info');
}

/** 更新企业信息（name 认证后不可改） */
export async function updateCompanyInfo(data: Partial<HrCompanyInfo>): Promise<void> {
  return request.put('/v1/hr/company/info', data);
}

/** 刷新邀请码（HR_ADMIN） */
export async function refreshInviteCode(): Promise<string> {
  return request.put('/v1/hr/company/members/invite-code');
}

/** 成员列表 */
export async function listMembers(params?: { role?: string; status?: string }): Promise<HrMemberItem[]> {
  return request.get('/v1/hr/company/members', { params });
}

/** 添加成员（创建面试官） */
export async function createMember(data: CreateMemberRequest): Promise<void> {
  return request.post('/v1/hr/company/members', data);
}

/** 移除成员（HR_ADMIN，仅面试官可移除） */
export async function removeMember(memberId: number): Promise<void> {
  return request.delete(`/v1/hr/company/members/${memberId}`);
}
