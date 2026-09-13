import request from '@/utils/request';

// ================================================================
// 类型定义 — 与后端 lingxi-job V2.9 DTO 对齐
// ================================================================

/** 岗位状态 */
export type JobStatus = 'DRAFT' | 'PUBLISHED' | 'PAUSED' | 'CLOSED';

/** 暂停原因 */
export type PauseReason = 'HC_RESERVED_FULL';

/** 关闭原因 */
export type CloseReason = 'MANUAL' | 'VIOLATION' | 'EXPIRED' | 'HC_CONFIRMED_FULL';

/** 画像来源 */
export type ProfileSource = 'AI' | 'MANUAL' | 'MIXED';

/** 置信度 */
export type ConfidenceLevel = 'LOW' | 'MEDIUM' | 'HIGH';

/** 匹配评分状态 */
export type MatchScoreStatus = 'READY' | 'PROFILE_MISSING' | 'INSUFFICIENT_DATA';

/** 薪资信息（金额/月数在后端面议时会置 null，前端提交与类型需兼容） */
export interface SalaryInfo {
  minAmount: number | null;
  maxAmount: number | null;
  currency: string;
  period: string;
  months: number | null;
  negotiable: boolean;
  rawText: string;
}

/** 核心技能 */
export interface CoreSkill {
  name: string;
  level: 'BASIC' | 'PROFICIENT' | 'ADVANCED' | 'EXPERT';
  required: boolean;
  inferred?: boolean;
  basis?: string;
  confidence?: number;
}

/** 软能力 */
export interface SoftSkill {
  name: string;
  importance: 'LOW' | 'MEDIUM' | 'HIGH';
  inferred?: boolean;
  confidence?: number;
}

/** 隐性要求 */
export interface HiddenRequirement {
  requirement: string;
  basis: string;
  inferred: boolean;
  confidence: number;
  hrConfirmed: boolean;
}

/** 岗位画像（创建/编辑请求结构，对应后端 JobCreateRequest.JobProfileDTO） */
export interface JobProfileDTO {
  jobType: string;
  coreSkills: CoreSkill[];
  softSkills: SoftSkill[];
  industryExperience: string;
  hiddenRequirements: HiddenRequirement[];
  interviewFocus: string[];
  profileSource: ProfileSource;
}

/** C 端岗位列表项（对齐后端 JobCardVO；jobId 为 Long→number） */
export interface CandidateJobListItem {
  jobId: number;
  title: string;
  industryGroupCode: string;
  industryCode: string;
  industryName: string;
  /** 岗位所属企业名称；后端降级时可能为空，页面展示"招聘企业" */
  companyName: string | null;
  /** 岗位录入责任人姓名，C 端展示为招聘负责人；兼容后端灰度期间的缺失值 */
  creatorName?: string | null;
  cityCode: string;
  cityName: string;
  salary: SalaryInfo;
  skillTags: string[];
  minExperienceYears: number;
  educationRequirement: string;
  /** 推荐排序分（SQL 无条件计算，恒返回；仅 sortBy=RECOMMENDED 排序依据） */
  recommendScore: number;
  publishedAt: string;
}

/** C 端岗位详情（对齐后端 JobDetailVO；画像脱敏） */
export interface CandidateJobDetail {
  jobId: number;
  title: string;
  industryGroupCode: string;
  industryCode: string;
  industryName: string;
  /** 岗位所属企业名称；后端降级时可能为空，页面展示"招聘企业" */
  companyName: string | null;
  /** 岗位录入责任人姓名，C 端展示为招聘负责人；兼容后端灰度期间的缺失值 */
  creatorName?: string | null;
  cityCode: string;
  cityName: string;
  minExperienceYears: number;
  educationRequirement: string;
  salary: SalaryInfo;
  jdText: string;
  jdSummary: string;
  profile: {
    jobType: string | null;
    coreSkills: { name: string; level: string; required: boolean }[];
    softSkills: { name: string; importance: string }[];
    industryExperience: string | null;
    interviewFocus: string[];
    profileConfirmed: boolean;
  };
  publishedAt: string;
}

/** 能力匹配结果 */
export interface AbilityMatchResult {
  abilityScore?: number;
  scoreStatus: MatchScoreStatus;
  dimensionScores: Record<string, number>;
  dataCompleteness: number;
  confidenceLevel: ConfidenceLevel;
  advantages: string[];
  risks: string[];
  missingDimensions: string[];
  referenceItems: string[];
}

/** HR 岗位列表项 */
export interface HrJobListItem {
  jobId: string;
  title: string;
  status: JobStatus;
  cityCode: string;
  cityName: string;
  salary: SalaryInfo;
  totalHc: number;
  reservedHc: number;
  confirmedHc: number;
  availableHc: number;
  /** 标准化岗位类型（来自 job_profile） */
  jobType?: string;
  /** 技能标签（核心技能名称列表） */
  skillTags: string[];
  version: number;
  publishedAt?: string;
  updatedAt: string;
  /** 创建人用户ID（复用 job_post.created_by） */
  createdBy?: string;
  /** 创建人姓名（后端已降级为 "用户"+id） */
  createdByName?: string;
}

/** HR 岗位详情（画像字段按后端 HrJobDetailVO 顶层平铺，无 profile 嵌套） */
export interface HrJobDetail {
  jobId: string;
  title: string;
  industryGroupCode: string;
  industryCode: string;
  industryName: string;
  cityCode: string;
  cityName: string;
  minExperienceYears: number;
  educationRequirement: string;
  salary: SalaryInfo;
  totalHc: number;
  reservedHc: number;
  confirmedHc: number;
  availableHc: number;
  jdText: string;
  jdSummary: string;
  status: JobStatus;
  pauseReason?: PauseReason;
  closeReason?: CloseReason;
  publishedAt?: string;
  closedAt?: string;
  expiresAt?: string;
  version: number;
  createdAt: string;
  updatedAt: string;
  /** 创建人用户ID（复用 job_post.created_by） */
  createdBy?: string;
  /** 创建人姓名（后端已降级为 "用户"+id） */
  createdByName?: string;
  // ===== 岗位画像字段（顶层平铺） =====
  jobType?: string;
  coreSkills: CoreSkill[];
  softSkills: SoftSkill[];
  industryExperience: string;
  hiddenRequirements: HiddenRequirement[];
  interviewFocus: string[];
  profileSource?: ProfileSource;
  confirmedBy?: string;
  confirmedAt?: string;
  profileConfirmed: boolean;
  profileVersion?: number;
}

/** JD 解析请求 */
export interface JdParseRequest {
  jdText: string;
  jobTitle?: string;
}

/** JD 解析草稿 */
export interface JobProfileDraft {
  jobType: string;
  coreSkills: CoreSkill[];
  softSkills: SoftSkill[];
  minExperienceYears: number;
  educationRequirement: string;
  industryExperience?: string;
  hiddenRequirements: HiddenRequirement[];
  interviewFocus: string[];
  salary: SalaryInfo;
  jdSummary: string;
  warnings: string[];
}

// ================================================================
// JD 解析响应类型（对齐后端 JdParseResponse；AI 字段可缺省，独立于保存 DTO）
// ================================================================

/** 解析核心技能（后端 level 为 String"1"~"5"，非前端命名枚举） */
export interface ParsedCoreSkill {
  name?: string;
  level?: string;
  required?: boolean;
  basis?: string;
  confidence?: number;
}

/** 解析软能力（importance 为 String"1"~"5"） */
export interface ParsedSoftSkill {
  name?: string;
  importance?: string;
  inferred?: boolean;
  confidence?: number;
}

/** 解析薪资（后端全可空、无 months/negotiable、金额单位分） */
export interface ParsedSalary {
  currency?: string | null;
  period?: string | null;
  minAmount?: number | null;
  maxAmount?: number | null;
  rawText?: string | null;
}

/** 解析隐性要求（字段可缺省，应用时需归一化为 HiddenRequirement） */
export interface ParsedHiddenRequirement {
  requirement?: string;
  basis?: string;
  inferred?: boolean;
  confidence?: number;
  hrConfirmed?: boolean;
}

/** JD 解析结果（对齐后端 JdParseResponse） */
export interface JdParseResult {
  jobType?: string;
  coreSkills?: ParsedCoreSkill[];
  softSkills?: ParsedSoftSkill[];
  minExperienceYears?: number;
  educationRequirement?: string;
  industryExperience?: string;
  interviewFocus?: { name?: string; level?: string; required?: boolean }[];
  hiddenRequirements?: ParsedHiddenRequirement[];
  salary?: ParsedSalary;
  jdSummary?: string;
  warnings?: string[];
}

/** 岗位创建请求（对应后端 JobCreateRequest，无 version/profileVersion） */
export interface JobCreateRequest {
  title: string;
  industryGroupCode: string;
  industryCode: string;
  cityCode: string;
  /** 后端兼容字段；页面不再传递，后端根据 cityCode 回填 */
  cityName?: string;
  minExperienceYears: number;
  educationRequirement: string;
  salary: SalaryInfo;
  totalHc: number;
  jdText: string;
  expiresAt?: string;
  profileConfirmed: boolean;
  profile: JobProfileDTO;
}

/** 岗位更新请求（对应后端 JobUpdateRequest：version 必填；画像三件套整体携带或整体缺失） */
export interface JobUpdateRequest {
  title: string;
  industryGroupCode: string;
  industryCode: string;
  cityCode: string;
  /** 后端兼容字段；页面不再传递，后端根据 cityCode 回填 */
  cityName?: string;
  minExperienceYears: number;
  educationRequirement: string;
  salary: SalaryInfo;
  totalHc: number;
  jdText: string;
  expiresAt?: string;
  /** 岗位乐观锁版本（必填，独立于画像三件套） */
  version: number;
  /** 画像三件套之一（可整体缺失） */
  profileConfirmed?: boolean;
  /** 画像三件套之一（可整体缺失） */
  profileVersion?: number;
  /** 画像三件套之一（可整体缺失） */
  profile?: JobProfileDTO;
}

/** 岗位创建/更新响应 */
export interface JobSaveResponse {
  jobId: string;
  status: JobStatus;
  version: number;
  profileVersion: number;
  createdAt?: string;
  updatedAt?: string;
}

/** HC 调整请求 */
export interface HeadcountRequest {
  totalHc: number;
  version: number;
}

/** HC 调整响应 */
export interface HeadcountResponse {
  totalHc: number;
  reservedHc: number;
  confirmedHc: number;
  availableHc: number;
  jobStatus: JobStatus;
  version: number;
}

/** 状态变更请求 */
export interface StatusChangeRequest {
  action: 'PUBLISH' | 'CLOSE' | 'REOPEN';
  version: number;
  profileVersion?: number;
}

/** 状态变更响应 */
export interface StatusChangeResponse {
  jobId: string;
  status: JobStatus;
  version: number;
  publishedAt?: string;
  closedAt?: string;
  closeReason?: CloseReason;
}

/** 分页响应（后端响应字段为 pageSize，请求参数名仍为 size） */
export interface PageResult<T> {
  list: T[];
  total: number;
  page: number;
  pageSize: number;
}

/** 岗位状态变更日志项（对齐后端 JobStatusLogVO；createdAt ISO 字符串） */
export interface JobStatusLog {
  /** 日志ID（lingxi-job Long 默认序列化为数字） */
  id: number;
  /** 原状态（首次创建为 null，防御性展示"创建"） */
  fromStatus: JobStatus | null;
  /** 目标状态 */
  toStatus: JobStatus;
  /** 变更原因编码（文案映射在详情页展示层，未知值兜底原始展示） */
  reason: string;
  /** 补充说明（违规下架 remark 等，可为空） */
  reasonDetail?: string | null;
  /** 操作人用户ID（0=SYSTEM） */
  operatorId: number;
  /** 操作人角色：HR/SYSTEM/ADMIN（string 兜底新角色） */
  operatorRole: string;
  /** 请求追踪ID（本阶段统一 null，前端不展示） */
  requestId: string | null;
  /** 变更时间 */
  createdAt: string;
}

/** 状态历史分页参数（请求参数名为 page/size，后端响应字段为 pageSize） */
export interface JobStatusHistoryParams {
  page?: number;
  size?: number;
}

// ================================================================
// C 端接口（求职者岗位发现）
// ================================================================

/** C 端岗位搜索列表（13 参数：10 筛选 + sortBy + page + size） */
export interface CandidateJobSearchParams {
  keyword?: string;
  industryGroupCode?: string;
  industryCode?: string;
  cityCode?: string;
  skillTags?: string[];
  salaryMin?: number;
  salaryMax?: number;
  experienceMax?: number;
  education?: string;
  jobType?: string;
  sortBy?: 'RECOMMENDED' | 'LATEST' | 'SALARY_DESC';
  page?: number;
  size?: number;
}

/** 城市主数据选项；code 是提交和筛选的唯一值，name 仅用于展示 */
export interface CityOption {
  code: string;
  name: string;
}

/** C 端岗位搜索选项 */
export interface JobOptionsResponse {
  industries: { groupCode: string; code: string; name: string }[];
  sortOptions: { code: string; name: string }[];
  cities: CityOption[];
}

/** HR 岗位表单城市选项；只返回全部启用城市 */
export interface HrJobOptionsResponse {
  cities: CityOption[];
}

/** 通用 C 端岗位列表参数（从 main 恢复：mock-interview / resume-diagnosis 使用；params 宽松透传 /v1/jobs） */
export interface GetJobListParams {
  keyword?: string;
  status?: string;
  page?: number;
  size?: number;
  pageSize?: number;
}

/** 通用 C 端岗位列表（merge 时 main 的 getJobList 被本分支重写覆盖，恢复其契约供 mock-interview / resume-diagnosis 使用） */
export async function getJobList(
  params: GetJobListParams,
): Promise<PageResult<CandidateJobListItem>> {
  return request.get('/v1/jobs', { params });
}

export async function getCandidateJobs(
  params: CandidateJobSearchParams,
): Promise<PageResult<CandidateJobListItem>> {
  // 请求级局部 paramsSerializer：数组序列化为 skillTags=a&skillTags=b（重复同名键，Codex P1#2 不全局改）
  return request.get('/v1/jobs', { params, paramsSerializer: { indexes: null } });
}

/** C 端搜索选项（行业 + 排序 + 城市下拉） */
export async function getJobOptions(): Promise<JobOptionsResponse> {
  return request.get('/v1/jobs/options');
}

/** HR 岗位表单城市选项（全部启用城市） */
export async function getHrJobOptions(): Promise<HrJobOptionsResponse> {
  return request.get('/v1/hr/jobs/options');
}

/** C 端岗位详情（jobId 后端 Long→number；URL 参数 string 时需 Number() 转换） */
export async function getCandidateJobDetail(
  jobId: string | number,
): Promise<CandidateJobDetail> {
  return request.get(`/v1/jobs/${jobId}`);
}

/** 单个人岗匹配（⚠️ 后端阶段3 未就绪，无 /match 接口；后续阶段接入，本阶段不调用） */
export async function getCandidateJobMatch(
  jobId: string | number,
): Promise<AbilityMatchResult> {
  return request.get(`/v1/jobs/${jobId}/match`);
}

// ================================================================
// B 端接口（HR 岗位管理）
// ================================================================

/** HR 岗位搜索参数 */
export interface HrJobSearchParams {
  keyword?: string;
  status?: JobStatus;
  page?: number;
  size?: number;
}

/** HR 岗位管理列表 */
export async function getHrJobs(
  params: HrJobSearchParams,
): Promise<PageResult<HrJobListItem>> {
  return request.get('/v1/hr/jobs', { params });
}

/** HR 岗位详情 */
export async function getHrJobDetail(
  jobId: string,
): Promise<HrJobDetail> {
  return request.get(`/v1/hr/jobs/${jobId}`);
}

/** HR 岗位状态变更历史（本企业，created_at DESC；前端只取 page=1&size=50） */
export async function getJobStatusHistory(
  jobId: string,
  params: JobStatusHistoryParams,
): Promise<PageResult<JobStatusLog>> {
  return request.get(`/v1/hr/jobs/${jobId}/status-history`, { params });
}

/** JD 解析（对齐后端 JdParseResponse；per-request 40s 超时：后端解析最长 30s，不改全局 timeout） */
export async function parseJobJd(
  payload: JdParseRequest,
): Promise<JdParseResult> {
  return request.post('/v1/hr/jobs/jd/parse', payload, { timeout: 40000 });
}

/** 创建岗位 */
export async function createJob(
  payload: JobCreateRequest,
): Promise<JobSaveResponse> {
  return request.post('/v1/hr/jobs', payload);
}

/** 更新岗位 */
export async function updateJob(
  jobId: string,
  payload: JobUpdateRequest,
): Promise<JobSaveResponse> {
  return request.put(`/v1/hr/jobs/${jobId}`, payload);
}

/** 调整岗位 HC */
export async function updateJobHeadcount(
  jobId: string,
  payload: HeadcountRequest,
): Promise<HeadcountResponse> {
  return request.patch(`/v1/hr/jobs/${jobId}/headcount`, payload);
}

/** 变更岗位状态（发布/关闭/重新开放） */
export async function changeJobStatus(
  jobId: string,
  payload: StatusChangeRequest,
): Promise<StatusChangeResponse> {
  return request.patch(`/v1/hr/jobs/${jobId}/status`, payload);
}

/** 删除草稿 */
export async function deleteDraftJob(
  jobId: string,
  version: number,
): Promise<void> {
  return request.delete(`/v1/hr/jobs/${jobId}`, { params: { version } });
}
