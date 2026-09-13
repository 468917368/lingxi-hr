import request from '@/utils/request';
import { sseFetch } from '@/utils/sseClient';
import type { QuestionType, QuestionDifficulty, QuestionStatus } from '@/constants/enums';

// ================================================================
// HR 面试出题（企业私有题库 + AI 补题）— 对齐后端 lingxi-job SSE 契约
// ⚠️ 字段名 type/sourceType 区别于 C 端 mock-interview 的 questionType
// ================================================================

/** HR 出题 SSE 进度事件（对齐 AgentSseEvent progress） */
export interface HrQuestionProgress {
  requestId: string;
  sequence: number;
  code: string;
  message: string;
  timestamp: string;
}

/** HR 出题结果题项（对齐后端 result.questions[]） */
export interface HrInterviewQuestion {
  type: QuestionType;
  difficulty: QuestionDifficulty;
  content: string;
  keyPoints: string;
  referenceAnswer: string;
  evaluationDimensions: { name: string; weight: number }[];
  sourceType: 'COMPANY_LIBRARY' | 'AI_GENERATED';
}

/** HR 出题结果（对齐后端 result 事件根级字段） */
export interface HrQuestionResult {
  requestId: string;
  generationMode: 'COMPANY_LIBRARY' | 'MIXED' | 'AI_GENERATED';
  isPersonalized: boolean;
  dataCompleteness: 'SUFFICIENT' | 'INSUFFICIENT';
  questions: HrInterviewQuestion[];
  timestamp: string;
}

/** HR 出题请求参数 */
export interface GenerateQuestionParams {
  /** 投递记录ID（必填，雪花 ID 用字符串） */
  applicationId: string;
  /** 难度（可选，默认 MEDIUM） */
  difficulty?: QuestionDifficulty;
}

/** 出题错误回调结构 */
export interface HrQuestionError {
  code?: number;
  message?: string;
  /** SSE error 事件是否允许前端提供重试操作；建流前普通 JSON 通常不携带该字段 */
  retryable?: boolean;
  /** SSE 错误保留后端 UUID；建流前普通 JSON 无 requestId 时归一化为 null */
  requestId: string | null;
  timestamp: string | null;
}

/** 运行时校验 progress 事件（防契约漂移导致渲染 undefined 字段崩溃；非法则忽略） */
function isHrQuestionProgress(data: unknown): data is HrQuestionProgress {
  const p = data as HrQuestionProgress;
  return !!p && typeof p === 'object' && typeof p.sequence === 'number' && typeof p.message === 'string';
}

/** 运行时校验 result 事件（缺 questions/generationMode 视为非法结果 → onError，而非静默渲染） */
function isHrQuestionResult(data: unknown): data is HrQuestionResult {
  const r = data as HrQuestionResult;
  return (
    !!r &&
    typeof r === 'object' &&
    Array.isArray(r.questions) &&
    typeof r.generationMode === 'string'
  );
}

/**
 * 生成面试题（SSE 流式）
 *
 * 复用 sseFetch：POST + progress/result/done/error 事件 + Bearer Token + abort。
 * 后端 SSE error 事件字段为 errorCode（非 code），在此归一化为 code 透传；
 * 建流前 Result JSON 错误已由 sseClient 非 SSE 响应分支解析后同样走 onError。
 */
export function generateInterviewQuestions(
  jobId: number,
  params: GenerateQuestionParams,
  handlers: {
    onProgress: (p: HrQuestionProgress) => void;
    onResult: (r: HrQuestionResult) => void;
    onError: (e: HrQuestionError) => void;
    onDone: () => void;
  },
): { abort: () => void } {
  return sseFetch<HrQuestionResult>({
    method: 'POST',
    url: `/v1/hr/jobs/${jobId}/interview-questions/generate`,
    body: { applicationId: params.applicationId, difficulty: params.difficulty },
    onProgress: (data) => {
      // 校验通过才透传，非法 progress 忽略（不阻塞主流程）
      if (isHrQuestionProgress(data)) handlers.onProgress(data);
    },
    onResult: (data) => {
      // 校验通过透传；非法结果转 onError，避免渲染 undefined.questions 静默崩溃
      if (isHrQuestionResult(data)) {
        handlers.onResult(data);
      } else {
        handlers.onError({ message: '出题结果格式异常，请重试', requestId: null, timestamp: null });
      }
    },
    onError: (data) => {
      // 兼容两种 error 载体：SSE error 事件字段 errorCode；建流前 Result JSON 字段 code
      const raw = data as {
        errorCode?: number;
        code?: number;
        message?: string;
        retryable?: boolean;
        requestId?: string;
        timestamp?: string;
      };
      handlers.onError({
        code: raw?.errorCode ?? raw?.code,
        message: raw?.message,
        retryable: raw?.retryable,
        requestId: raw?.requestId ?? null,
        timestamp: raw?.timestamp ?? null,
      });
    },
    onDone: handlers.onDone,
  });
}

// ================================================================
// 面试官单题申请入库（阶段6.3）— 对齐后端 AgentQuestionSubmitRequest/Response
// 仅 INTERVIEWER 可调用；companyId/createdBy/source/status/jobType/skillTags
// 由后端 UserContext / 岗位画像推导，前端不传任何控制字段。
// ================================================================

/** 单题申请入库请求（字段与出题结果题项对齐） */
export interface AgentQuestionSubmitRequest {
  /** 题目类型（必填；← 出题结果 q.type） */
  questionType: QuestionType;
  /** 难度（可选，null/空 → MEDIUM） */
  difficulty?: QuestionDifficulty;
  /** AI 生成题干（必填，≤2000） */
  content: string;
  /** 考察要点（可选，≤1000） */
  keyPoints?: string;
  /** 参考答案（可选，≤4000） */
  referenceAnswer?: string;
  /** 评分要点（可选；字段名与出题结果 evaluationDimensions 一致，直接透传） */
  evaluationDimensions?: { name: string; weight: number }[];
}

/** 单题申请入库响应 */
export interface AgentQuestionSubmitResponse {
  /** 入库题目 ID（软删恢复时复用原 id） */
  questionId: number;
  /** 落库状态（恒 PENDING_REVIEW） */
  status: QuestionStatus;
  /** 提示消息 */
  message: string;
}

/** 面试官提交单道 AI 题申请入企业题库 */
export async function submitQuestionToLibrary(
  jobId: number,
  dto: AgentQuestionSubmitRequest,
): Promise<AgentQuestionSubmitResponse> {
  return request.post(`/v1/hr/jobs/${jobId}/interview-questions/submit-to-library`, dto);
}
