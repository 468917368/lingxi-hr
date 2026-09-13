import request from '@/utils/request';
import type { PageResult } from '@/constants/apiTypes';

// ==================== 类型定义（对齐后端 ResumeController VO/DTO） ====================

/**
 * 简历列表项（ResumeVO）
 * 注意：id 为字符串——后端雪花 ID（19 位）超出 JS Number 安全整数范围，
 * 后端已对 id 做字符串序列化（@JsonSerialize ToStringSerializer）。
 */
export interface ResumeListItem {
  id: string;
  fileName: string;
  fileFormat: string;
  isDefault: number; // 1=默认 0=非默认
  parseStatus: string; // PENDING/PARSING/COMPLETED/FAILED
  createdAt: string;
}

/** 卡片章节要点（CardStructure.sections[].points[]） */
export interface CardPoint {
  id: string;
  text: string;
}

/** 卡片章节（CardStructure.sections[]） */
export interface CardSection {
  title: string;
  points: CardPoint[];
  confidence?: 'HIGH' | 'LOW';
  raw_text?: string; // 空章节（仅标题）时展示原始标题
}

/** 卡片渲染指令（cardStructure，DB 存储 JSON 字符串，接口返回对象） */
export interface CardStructure {
  /** 解析 agent 契约格式：章节（当前 agent 输出） */
  sections: CardSection[];
  /** 兼容旧数据/部分来源：cards 格式（同为 title + points 结构） */
  cards?: CardSection[];
  confidence: 'HIGH' | 'LOW';
}

/** 简历详情（ResumeDetailVO，id 为字符串序列化） */
export interface ResumeDetail {
  id: string;
  fileName: string;
  fileFormat: string;
  fileSize: number;
  isDefault: number;
  parseStatus: string;
  resumeMdUrl: string;
  /** 人脸照片 URL（解析提取，可为 null） */
  facePhotoUrl: string | null;
  cardStructure: CardStructure | null;
  phone: string;
  email: string;
  wechat: string;
  candidateName: string;
  /** 盲选模式（隐私设置）。仅 HR/推荐侧简历查看接口返回，候选侧自身简历无此字段 */
  blindMode?: boolean;
  createdAt: string;
  updatedAt: string;
}

/** 上传简历结果（UploadResumeVO，resumeId 为字符串序列化） */
export interface UploadResumeResult {
  resumeId: string;
  fileUrl: string;
  parseStatus: string; // 上传成功恒为 PENDING
}

/** 在线编辑保存请求体（UpdateResumeDTO，cardStructure 必传） */
export interface UpdateResumeRequest {
  cardStructure: CardStructure;
  phone?: string;
  email?: string;
  wechat?: string;
  candidateName?: string;
}

/** 5 维能力模型（AbilityModelVO，雷达图数据；模型不存在时接口返回 null） */
export interface AbilityModel {
  resumeId: string;
  professionalSkillScore: number;
  workExperienceScore: number;
  industryKnowledgeScore: number;
  comprehensiveQualityScore: number;
  learningGrowthScore: number;
  subDimensions: Record<string, unknown> | null;
  updatedAt: string;
}

/** 解析 SSE final 事件 payload */
export interface ParseStreamFinalEvent {
  parse_status: string;
  card_structure: CardStructure;
}

/** 解析 SSE error 事件 payload */
export interface ParseStreamErrorEvent {
  code: number;
  message: string;
}

// ==================== 简历诊断（对齐 DiagnosisController） ====================

/** 简历诊断请求（career 为自由文本职业名，不关联平台岗位） */
export interface DiagnosisRequest {
  career: string;
}

/** 诊断 SSE final 事件 payload */
export interface DiagnosisFinalEvent {
  reportId: string;
  career: string;
  matchScore: number;
  reportMd: string;
  /** 诊断回写后的能力模型（后端 SSE payload 为 snake_case key，缺失时为 undefined） */
  abilityModel?: { scores?: Record<string, number> } | null;
}

/** 诊断历史列表项（DiagnosisHistoryVO） */
export interface DiagnosisHistoryItem {
  id: string;
  career: string;
  matchScore: number;
  createdAt: string;
}

/** 诊断报告详情（DiagnosisDetailVO） */
export interface DiagnosisDetail {
  id: string;
  career: string;
  matchScore: number;
  reportMd: string;
  createdAt: string;
}

/** 诊断任务状态（DiagnosisTaskStatusVO，方案C异步化轮询用） */
export interface DiagnosisTaskStatus {
  /** RUNNING / COMPLETED / FAILED / NONE */
  state: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'NONE';
  /** COMPLETED 时非空：最新报告 ID */
  reportId: string | null;
  career: string;
}

/**
 * 活跃诊断任务 localStorage key 前缀（方案C跨页面感知约定）：
 * 诊断页写入 `diagnosis_pending:{resumeId}:{careerBase64}`，完成/失败/用户查看后清除；
 * CandidateLayout 导航栏扫描该前缀 → 调 status 接口 → 红点提示。
 */
export const PENDING_DIAGNOSIS_PREFIX = 'diagnosis_pending:';

/**
 * 活跃解析任务 localStorage key 前缀（解析异步化跨页面感知约定）：
 * 上传页写入 `parse_pending:{resumeId}`，完成/失败通知后清除；
 * CandidateLayout 导航栏扫描该前缀 → 调 parse-status → 彩点提示 + 桌面通知。
 */
export const PENDING_PARSE_PREFIX = 'parse_pending:';

/** 解析任务状态（ParseTaskStatusVO，异步化轮询用） */
export interface ParseTaskStatus {
  /** RUNNING / COMPLETED / FAILED / NONE */
  state: 'RUNNING' | 'COMPLETED' | 'FAILED' | 'NONE';
}

/** 解析 SSE 事件类型 */
export type ParseStreamEventType =
  | 'thinking'
  | 'progress'
  | 'tool_call'
  | 'tool_result'
  | 'final'
  | 'error'
  | 'heartbeat';

/** 解析 SSE progress 事件 payload（章节级实时进度） */
export interface ParseStreamProgressEvent {
  stage?: 'card' | 'score';
  sections_done?: number;
  titles?: string[];
}

// ==================== API ====================

/** 获取简历列表（分页） */
export async function getResumeList(params?: {
  page?: number;
  pageSize?: number;
}): Promise<PageResult<ResumeListItem>> {
  return request.get('/v1/resumes', { params });
}

/** 上传简历（≤10MB，pdf/doc/docx） */
export async function uploadResume(
  formData: FormData,
  onUploadProgress?: (percent: number) => void,
): Promise<UploadResumeResult> {
  return request.post('/v1/resumes/upload', formData, {
    onUploadProgress: (e) => {
      if (onUploadProgress && e.total) {
        onUploadProgress(Math.round((e.loaded / e.total) * 100));
      }
    },
  });
}

/** 获取简历详情 */
export async function getResumeDetail(id: string): Promise<ResumeDetail> {
  return request.get(`/v1/resumes/${id}`);
}

/** 在线编辑保存（cardStructure + 联系信息） */
export async function updateResume(id: string, data: UpdateResumeRequest): Promise<void> {
  return request.put(`/v1/resumes/${id}`, data);
}

/** 删除简历（逻辑删除，含 MinIO 原件） */
export async function deleteResume(id: string): Promise<void> {
  return request.delete(`/v1/resumes/${id}`);
}

/** 设为默认简历（事务互斥） */
export async function setDefaultResume(id: string): Promise<void> {
  return request.put(`/v1/resumes/${id}/default`);
}

/** 上传/更换简历头像（JPG/PNG，≤2MB），返回新的头像 presigned URL */
export async function uploadFacePhoto(id: string, file: File): Promise<string> {
  const formData = new FormData();
  formData.append('file', file);
  return request.put(`/v1/resumes/${id}/face-photo`, formData);
}

/** 5 维能力模型（雷达图数据；未解析/无模型时返回 null） */
export async function getAbilityModel(id: string): Promise<AbilityModel | null> {
  return request.get(`/v1/resumes/${id}/ability-model`);
}

/**
 * 解析进度 SSE 流 URL（供 fetch 流式读取，不走 axios）
 *
 * <p>注意：
 * <ol>
 *   <li>原生 EventSource 无法携带 Authorization 头，调用方需用 fetch 流式读取。</li>
 *   <li>开发环境直连网关（8080）：umi dev server 的 gzip 压缩会缓冲整个 SSE 流，
 *       小事件（progress/heartbeat）被攒到连接关闭才一次性发出，流式体验完全丢失。
 *       直连网关仍经过 AuthFilter 鉴权（fetch 带 Authorization 头），仅绕过 dev server。
 *       生产环境走同源 /api（Nginx 部署需配置不缓冲 SSE：proxy_buffering off）。</li>
 * </ol>
 */
export function getParseStreamUrl(id: string): string {
  if (process.env.NODE_ENV === 'development') {
    return `http://localhost:8080/api/v1/resumes/${id}/parse-stream`;
  }
  return `/api/v1/resumes/${id}/parse-stream`;
}

/** 获取解析任务状态（跳页/断连后回页轮询的完成信号） */
export async function getParseTaskStatus(id: string): Promise<ParseTaskStatus> {
  return request.get(`/v1/resumes/${id}/parse-status`);
}

/** 获取简历诊断历史列表 */
export async function getDiagnosisHistory(id: string): Promise<DiagnosisHistoryItem[]> {
  return request.get(`/v1/resumes/${id}/diagnosis`);
}

/** 获取诊断报告详情（含 Markdown 正文） */
export async function getDiagnosisDetail(id: string, reportId: string): Promise<DiagnosisDetail> {
  return request.get(`/v1/resumes/${id}/diagnosis/${reportId}`);
}

/** 获取诊断任务状态（跳页后回页轮询的完成信号） */
export async function getDiagnosisTaskStatus(
  id: string,
  career: string,
): Promise<DiagnosisTaskStatus> {
  return request.get(`/v1/resumes/${id}/diagnosis/status`, {
    params: { career },
  });
}

/**
 * 发起简历诊断的 SSE 流 URL（POST 方式，fetch 流式读取，不走 axios）
 *
 * <p>注意：与解析 SSE 同理，开发环境直连网关（8080）绕过 dev server 的 gzip 缓冲。
 */
export function getDiagnosisStreamUrl(id: string): string {
  if (process.env.NODE_ENV === 'development') {
    return `http://localhost:8080/api/v1/resumes/${id}/diagnosis`;
  }
  return `/api/v1/resumes/${id}/diagnosis`;
}
