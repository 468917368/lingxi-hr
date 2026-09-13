import request from '@/utils/request';
import type { PageResult } from '@/constants/apiTypes';
import type {
  QuestionStatus,
  QuestionSource,
  QuestionType,
  QuestionDifficulty,
} from '@/constants/enums';

// ================================================================
// HR 企业私有题库管理 — 对齐后端 lingxi-job HrQuestionController
// ⚠️ 列表刻意脱敏：HrQuestionListVO 不含 keyPoints/referenceAnswer/evaluationPoints，
//    敏感字段仅能通过详情接口获取（类型层已隔离，杜绝误用）。
// ================================================================

/** 列表项（严格对齐后端 HrQuestionListVO，脱敏） */
export interface HrQuestionListVO {
  /** 题目ID */
  id: number;
  /** 通用岗位类型（自由字符串） */
  jobType: string;
  /** 题目类型 */
  questionType: QuestionType;
  /** 难度 */
  difficulty: QuestionDifficulty;
  /** 题干 */
  content: string;
  /** 标准化技能标签 */
  skillTags: string[];
  /** 来源 */
  source: QuestionSource;
  /** 状态 */
  status: QuestionStatus;
  /** 乐观锁版本号 */
  version: number;
  /** 创建时间 */
  createdAt: string;
}

/** 评分要点（name + 0~1 weight） */
export interface EvaluationPointDTO {
  /** 评分维度名称 */
  name: string;
  /** 权重（0~1） */
  weight: number;
}

/** 详情（完整字段，含敏感考察评分内容与审核信息） */
export interface HrQuestionDetailVO extends HrQuestionListVO {
  /** 考察要点（敏感） */
  keyPoints: string;
  /** 参考答案（敏感） */
  referenceAnswer: string;
  /** 评分要点（敏感） */
  evaluationPoints: EvaluationPointDTO[];
  /** 创建人用户ID */
  createdBy: number;
  /** 更新时间 */
  updatedAt: string;
  /** 审核人用户ID（有审核记录才有） */
  reviewedBy?: number;
  /** 审核时间 */
  reviewedAt?: string;
  /** 审核意见/拒绝原因 */
  reviewReason?: string;
}

/** 列表查询参数 */
export interface QuestionListParams {
  /** 岗位类型（自由字符串，输入筛选） */
  jobType?: string;
  /** 题目类型 */
  questionType?: QuestionType;
  /** 难度 */
  difficulty?: QuestionDifficulty;
  /** 状态 */
  status?: QuestionStatus;
  /** 页码（1~100） */
  page?: number;
  /** 每页条数（1~50） */
  size?: number;
}

/** 创建请求 */
export interface QuestionCreateRequest {
  /** 岗位类型（必填） */
  jobType: string;
  /** 题目类型（必填） */
  questionType: QuestionType;
  /** 难度（可选，null/空 → MEDIUM） */
  difficulty?: QuestionDifficulty;
  /** 题干（必填，≤2000） */
  content: string;
  /** 标准化技能标签（可选，≤10） */
  skillTags?: string[];
  /** 考察要点（可选） */
  keyPoints?: string;
  /** 参考答案（可选） */
  referenceAnswer?: string;
  /** 评分要点（可选，提供则非空数组） */
  evaluationPoints?: EvaluationPointDTO[];
}

/** 编辑请求 = 创建 + 必传 version（乐观锁） */
export interface QuestionUpdateRequest extends QuestionCreateRequest {
  version: number;
}

/** 启停操作 */
export type QuestionStatusAction = 'ENABLE' | 'DISABLE';

/** 审核操作（REJECT 必传 reason，类型层强制） */
export type QuestionReviewBody =
  | { action: 'APPROVE'; version: number }
  | { action: 'REJECT'; reason: string; version: number };

// ---- Service ----

/** 题库分页列表（四筛：jobType/questionType/difficulty/status） */
export async function getQuestionList(
  params: QuestionListParams,
): Promise<PageResult<HrQuestionListVO>> {
  return request.get('/v1/hr/questions', { params });
}

/** 题库详情（完整字段含敏感内容与审核信息，企业隔离） */
export async function getQuestionDetail(id: number): Promise<HrQuestionDetailVO> {
  return request.get(`/v1/hr/questions/${id}`);
}

/** 新增题目（后端固定 source=HR_CREATED、status=ACTIVE） */
export async function createQuestion(dto: QuestionCreateRequest): Promise<HrQuestionDetailVO> {
  return request.post('/v1/hr/questions', dto);
}

/** 编辑题目（乐观锁 version；REJECTED 编辑后回 PENDING_REVIEW） */
export async function updateQuestion(
  id: number,
  dto: QuestionUpdateRequest,
): Promise<HrQuestionDetailVO> {
  return request.put(`/v1/hr/questions/${id}`, dto);
}

/** 软删除题目（HTTP 200 + code=200） */
export async function deleteQuestion(id: number, version: number): Promise<void> {
  return request.delete(`/v1/hr/questions/${id}`, { params: { version } });
}

/** 启用/停用（仅 ACTIVE ↔ INACTIVE） */
export async function changeQuestionStatus(
  id: number,
  body: { action: QuestionStatusAction; version: number },
): Promise<HrQuestionDetailVO> {
  return request.patch(`/v1/hr/questions/${id}/status`, body);
}

/** 审核流转（仅 PENDING_REVIEW；REJECT 必填 reason） */
export async function reviewQuestion(
  id: number,
  body: QuestionReviewBody,
): Promise<HrQuestionDetailVO> {
  return request.post(`/v1/hr/questions/${id}/review`, body);
}
