import request from '@/utils/request';
import { sseFetch } from '@/utils/sseClient';
import type {
  AnswerScore,
  GenerateProgress,
  GenerateRequest,
  GenerateResult,
  MockInterviewReport,
  MockQuestion,
} from '@/constants/apiTypes';

/** 生成题目（SSE） */
export function generateQuestions(
  params: GenerateRequest,
  handlers: {
    onProgress: (p: GenerateProgress) => void;
    onResult: (r: GenerateResult) => void;
    onError: (e: { code?: number; message?: string }) => void;
    onDone: () => void;
  },
): { abort: () => void } {
  return sseFetch<GenerateResult>({
    method: 'POST',
    url: '/v1/mock-interview/generate',
    body: params,
    onProgress: (data) => handlers.onProgress(data as GenerateProgress),
    onResult: (data) => handlers.onResult(data as GenerateResult),
    onError: handlers.onError,
    onDone: handlers.onDone,
  });
}

/** 获取题目（继续面试，JSON） */
export interface MockSessionQuestions {
  sessionId: string;
  status?: string;
  questions: MockQuestion[];
  answered?: Record<number, boolean>;
}

export async function getQuestions(sessionId: string): Promise<MockSessionQuestions> {
  return request.get(`/v1/mock-interview/${sessionId}/questions`);
}

/** 提交答案（SSE） */
export function submitAnswer(
  sessionId: string,
  questionNumber: number,
  answer: string,
  handlers: {
    onResult: (s: AnswerScore) => void;
    onError: (e: { code?: number; message?: string }) => void;
    onDone: () => void;
  },
): { abort: () => void } {
  return sseFetch<AnswerScore>({
    method: 'POST',
    url: `/v1/mock-interview/${sessionId}/answer`,
    body: { questionNumber, answer },
    onResult: (data) => handlers.onResult(data as AnswerScore),
    onError: handlers.onError,
    onDone: handlers.onDone,
  });
}

/** 跳过题目（JSON） */
export async function skipQuestion(sessionId: string, questionNumber: number): Promise<void> {
  return request.post(`/v1/mock-interview/${sessionId}/skip`, null, {
    params: { questionNumber },
  });
}

/** 生成报告（SSE） */
export function getReport(
  sessionId: string,
  handlers: {
    onResult: (r: MockInterviewReport) => void;
    onError: (e: { code?: number; message?: string }) => void;
    onDone: () => void;
  },
): { abort: () => void } {
  return sseFetch<MockInterviewReport>({
    method: 'GET',
    url: `/v1/mock-interview/${sessionId}/report`,
    onResult: (data) => handlers.onResult(data as MockInterviewReport),
    onError: handlers.onError,
    onDone: handlers.onDone,
  });
}
