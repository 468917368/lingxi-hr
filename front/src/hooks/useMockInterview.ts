/**
 * 模拟面试状态机 Hook（C端模拟面试页 /candidate/mock-interview 使用）
 * 阶段机：prep → generating → answering → report；
 * 封装全部 SSE 交互（生成题目/提交答案/生成报告，支持 abort）与断点恢复
 * （localStorage 存 sessionId，挂载时恢复至首个未答题，全答完自动出报告）；
 * 用 ref 保存权威数据避免回调闭包过期，组件卸载自动中断进行中的 SSE。
 */
import { useCallback, useEffect, useRef, useState } from 'react';
import { message } from 'antd';
import type {
  AnswerScore,
  GenerateProgress,
  GenerateRequest,
  MockInterviewReport,
  MockQuestion,
} from '@/constants/apiTypes';
import {
  generateQuestions,
  getQuestions,
  getReport,
  skipQuestion,
  submitAnswer,
} from '@/services/mockInterview';

export type MockInterviewPhase = 'prep' | 'generating' | 'answering' | 'report';

/** 已记录答案（含跳过标记） */
export interface RecordedAnswer extends AnswerScore {
  skipped?: boolean;
  answer?: string;
}

/** 本地存储的 session 标识 */
const SESSION_KEY = 'mock_interview_session_id';

/** progress 事件按 sequence 去重置顶 */
function upsertProgress(prev: GenerateProgress[], p: GenerateProgress): GenerateProgress[] {
  const exists = prev.some((item) => item.sequence === p.sequence);
  const next = exists
    ? prev.map((item) => (item.sequence === p.sequence ? p : item))
    : [...prev, p];
  return next.sort((a, b) => a.sequence - b.sequence);
}

export function useMockInterview() {
  // ===== 渲染状态 =====
  const [phase, setPhase] = useState<MockInterviewPhase>('prep');
  const [progressSteps, setProgressSteps] = useState<GenerateProgress[]>([]);
  const [sessionId, setSessionId] = useState<string | null>(null);
  const [questions, setQuestions] = useState<MockQuestion[]>([]);
  const [currentIndex, setCurrentIndex] = useState(0);
  const [currentScore, setCurrentScore] = useState<AnswerScore | null>(null);
  const [scores, setScores] = useState<Record<number, RecordedAnswer>>({});
  const [report, setReport] = useState<MockInterviewReport | null>(null);
  const [busy, setBusy] = useState(false);

  // ===== 权威数据 ref（供回调读取最新值，避免闭包过期） =====
  const sessionIdRef = useRef<string | null>(null);
  const questionsRef = useRef<MockQuestion[]>([]);
  const scoresRef = useRef<Record<number, RecordedAnswer>>({});
  const currentIndexRef = useRef(0);
  const abortRef = useRef<{ abort: () => void } | null>(null);

  /** 出报告：SSE GET report，成功后清 localStorage */
  const generateReport = useCallback(() => {
    const sid = sessionIdRef.current;
    if (!sid) return;
    abortRef.current?.abort();
    setBusy(true);
    setPhase('report');
    abortRef.current = getReport(sid, {
      onResult: (r) => {
        setReport(r);
        setBusy(false);
        localStorage.removeItem(SESSION_KEY);
      },
      onError: (e) => {
        setBusy(false);
        message.error(e?.message || '报告生成失败，请重试');
      },
      onDone: () => {
        /* 流结束，无额外处理 */
      },
    });
  }, []);

  /** 前进到下一题；已是末题则出报告 */
  const goToNextQuestion = useCallback(() => {
    const next = currentIndexRef.current + 1;
    if (next >= questionsRef.current.length) {
      generateReport();
    } else {
      currentIndexRef.current = next;
      setCurrentIndex(next);
      setCurrentScore(null);
    }
  }, [generateReport]);

  /** 提交当前题目答案（SSE） */
  const submitAnswerText = useCallback((text: string) => {
    const q = questionsRef.current[currentIndexRef.current];
    if (!q) return;
    if (text.trim().length < 20) {
      message.warning('答案需要至少20个字符');
      return;
    }
    abortRef.current?.abort();
    setBusy(true);
    const qn = q.questionNumber;
    abortRef.current = submitAnswer(sessionIdRef.current!, qn, text.trim(), {
      onResult: (s) => {
        scoresRef.current[qn] = { ...s, answer: text.trim() };
        setScores({ ...scoresRef.current });
        setCurrentScore(s);
        setBusy(false);
      },
      onError: (e) => {
        setBusy(false);
        message.error(e?.message || '评分失败，请重试');
      },
      onDone: () => {
        /* 流结束 */
      },
    });
  }, []);

  /** 跳过当前题目（JSON，计0分） */
  const skipCurrentQuestion = useCallback(() => {
    const q = questionsRef.current[currentIndexRef.current];
    const sid = sessionIdRef.current;
    if (!q || !sid) return;
    setBusy(true);
    skipQuestion(sid, q.questionNumber)
      .then(() => {
        scoresRef.current[q.questionNumber] = {
          questionNumber: q.questionNumber,
          techAccuracyScore: 0,
          expressionScore: 0,
          knowledgeDepthScore: 0,
          overallScore: 0,
          aiComment: '已跳过',
          skipped: true,
        };
        setScores({ ...scoresRef.current });
        setBusy(false);
        goToNextQuestion();
      })
      .catch(() => setBusy(false));
  }, [goToNextQuestion]);

  /** 开始模拟面试：SSE generate → 进入答题阶段 */
  const startInterview = useCallback((params: GenerateRequest) => {
    abortRef.current?.abort();
    setPhase('generating');
    setProgressSteps([]);
    setBusy(true);
    abortRef.current = generateQuestions(params, {
      onProgress: (p) => setProgressSteps((prev) => upsertProgress(prev, p)),
      onResult: (r) => {
        sessionIdRef.current = r.sessionId;
        questionsRef.current = r.questions || [];
        scoresRef.current = {};
        currentIndexRef.current = 0;
        setSessionId(r.sessionId);
        setQuestions(r.questions || []);
        setScores({});
        setCurrentIndex(0);
        setCurrentScore(null);
        if (r.sessionId) localStorage.setItem(SESSION_KEY, r.sessionId);
        setPhase('answering');
        setBusy(false);
      },
      onError: (e) => {
        setBusy(false);
        setPhase('prep');
        message.error(e?.message || '生成题目失败，请重试');
      },
      onDone: () => {
        /* 流结束 */
      },
    });
  }, []);

  /** 重置回准备阶段，清理本地 session */
  const restart = useCallback(() => {
    abortRef.current?.abort();
    localStorage.removeItem(SESSION_KEY);
    sessionIdRef.current = null;
    questionsRef.current = [];
    scoresRef.current = {};
    currentIndexRef.current = 0;
    setPhase('prep');
    setProgressSteps([]);
    setSessionId(null);
    setQuestions([]);
    setCurrentIndex(0);
    setScores({});
    setCurrentScore(null);
    setReport(null);
    setBusy(false);
  }, []);

  // ===== 挂载时断点恢复 =====
  useEffect(() => {
    const sid = localStorage.getItem(SESSION_KEY);
    if (!sid) return;
    let cancelled = false;
    getQuestions(sid)
      .then((data) => {
        if (cancelled) return;
        const questions = data.questions || [];
        if (questions.length === 0) {
          localStorage.removeItem(SESSION_KEY);
          return;
        }
        sessionIdRef.current = sid;
        questionsRef.current = questions;
        setSessionId(sid);
        setQuestions(questions);

        // 兼容后端两种已答标记：顶层 answered map 或题目内嵌 answered/score
        const rawQuestions = questions as Array<MockQuestion & { answered?: boolean; score?: number }>;
        const answeredMap: Record<number, boolean> = data.answered
          ? { ...data.answered }
          : Object.fromEntries(
              rawQuestions
                .filter((q) => q.answered || typeof q.score === 'number')
                .map((q) => [q.questionNumber, true]),
            );

        const firstUnanswered = questions.findIndex((q) => !answeredMap[q.questionNumber]);
        if (firstUnanswered === -1) {
          // 全部已答 → 直接出报告
          generateReport();
        } else {
          currentIndexRef.current = firstUnanswered;
          setCurrentIndex(firstUnanswered);
          setPhase('answering');
        }
      })
      .catch(() => {
        if (!cancelled) localStorage.removeItem(SESSION_KEY);
      });
    return () => {
      cancelled = true;
    };
  }, [generateReport]);

  // ===== 卸载时中止进行中的 SSE =====
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  return {
    phase,
    progressSteps,
    sessionId,
    questions,
    currentIndex,
    currentScore,
    scores,
    report,
    busy,
    totalCount: questions.length,
    startInterview,
    submitAnswerText,
    skipCurrentQuestion,
    goToNextQuestion,
    generateReport,
    restart,
  };
}
