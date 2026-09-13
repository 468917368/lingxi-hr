/**
 * 面试出题弹窗（HR 人才库 / 面试官候选人页共用）
 * SSE 流式 AI 出题：progress 逐步点亮 → result 展示题目列表；
 * 支持面试官将 AI 生成题单题申请入库（PENDING_REVIEW）。
 */
import React, { useState, useEffect, useRef } from 'react';
import { Modal, Select, Button, Tag, Collapse, Spin, Empty, Space, message } from 'antd';
import { ThunderboltOutlined, ReloadOutlined, SendOutlined } from '@ant-design/icons';
import type { QuestionDifficulty } from '@/constants/enums';
import { QuestionType, QuestionDifficulty as QD } from '@/constants/enums';
import {
  generateInterviewQuestions,
  submitQuestionToLibrary,
} from '@/services/interview';
import type {
  HrQuestionProgress,
  HrQuestionResult,
  HrInterviewQuestion,
  AgentQuestionSubmitRequest,
} from '@/services/interview';
import useUserStore from '@/stores/userStore';
import styles from './QuestionGenerateModal.less';

/** 出题目标最小入参（HR 候选人 / 面试官候选人通用） */
export interface QuestionTarget {
  jobId: number;
  applicationId: string; // 投递记录 ID，雪花 ID 用字符串
  candidateName: string;
  jobTitle: string;
}

interface Props {
  open: boolean;
  record: QuestionTarget | null;
  onClose: () => void;
}

/** 出题阶段 */
type GenPhase = 'prep' | 'generating' | 'success' | 'failed';

/** generationMode 文案映射 */
const MODE_MAP: Record<
  HrQuestionResult['generationMode'],
  { label: string; color: string }
> = {
  COMPANY_LIBRARY: { label: '全部来自企业题库', color: 'green' },
  MIXED: { label: '企业题库 + AI 补题', color: 'blue' },
  AI_GENERATED: { label: 'AI 生成', color: 'default' },
};

/** 错误码差异化文案（failed 态） */
const ERROR_TEXT: Record<number, string> = {
  2302: 'AI 出题结果校验未通过，请重试',
  2303: 'AI 出题失败，请重试',
  2304: '出题服务繁忙，请稍后重试',
  2305: '当前投递状态不允许出题（需已筛选/面试中）',
};

/** 建流前普通 JSON 未携带 retryable 时，明确不可通过重试解决的错误码 */
const NON_RETRYABLE_ERROR_CODES = new Set([400, 401, 403, 2101, 2305]);

/** 题型中文 */
const TYPE_TEXT: Record<QuestionType, string> = {
  [QuestionType.BASIC]: '基础验证',
  [QuestionType.PROJECT]: '项目深挖',
  [QuestionType.BOUNDARY]: '能力边界',
  [QuestionType.COMPREHENSIVE]: '综合素养',
};

/** 难度中文 */
const DIFF_TEXT: Record<QuestionDifficulty, string> = {
  [QD.EASY]: '简单',
  [QD.MEDIUM]: '中等',
  [QD.HARD]: '困难',
};

/** progress 事件按 sequence 去重置顶 */
function upsertProgress(prev: HrQuestionProgress[], p: HrQuestionProgress): HrQuestionProgress[] {
  const exists = prev.some((item) => item.sequence === p.sequence);
  const next = exists
    ? prev.map((item) => (item.sequence === p.sequence ? p : item))
    : [...prev, p];
  return next.sort((a, b) => a.sequence - b.sequence);
}

const QuestionGenerateModal: React.FC<Props> = ({ open, record, onClose }) => {
  const [phase, setPhase] = useState<GenPhase>('prep');
  const [difficulty, setDifficulty] = useState<QuestionDifficulty>(QD.MEDIUM);
  const [progressSteps, setProgressSteps] = useState<HrQuestionProgress[]>([]);
  const [result, setResult] = useState<HrQuestionResult | null>(null);
  const [errorMsg, setErrorMsg] = useState<string>('');
  const [errorRetryable, setErrorRetryable] = useState(true);
  const abortRef = useRef<{ abort: () => void } | null>(null);
  // 阶段6.3：仅面试官可申请入库（CANDIDATE 不显示、HR 不获得该入口）
  const isInterviewer = useUserStore((s) => s.userInfo?.role) === 'INTERVIEWER';
  // 已提交「审核中」的题目类型集合（不可重复提交）
  const [submittedKeys, setSubmittedKeys] = useState<Set<QuestionType>>(new Set());
  // 当前提交中的单题类型（同一时刻仅一题提交，防并发）
  const [submittingType, setSubmittingType] = useState<QuestionType | null>(null);
  // 是否已收到 result（onDone 兜底判断用；须在重置与每次出题前置 false，否则重试时旧值绕过兜底）
  const resultReceived = useRef(false);

  // 重置（Codex P1-1 + resultReceived）：Modal 关闭仅 open=false 不卸载，
  // 切换候选人会残留上一位结果 → open 变 true 或 record.applicationId 变化时重置为 prep
  useEffect(() => {
    if (open) {
      abortRef.current?.abort();
      resultReceived.current = false;
      setPhase('prep');
      setProgressSteps([]);
      setResult(null);
      setErrorMsg('');
      setErrorRetryable(true);
      setDifficulty(QD.MEDIUM);
      // 阶段6.3：弹窗打开/切换候选人时复位「申请入库」提交态，避免残留上一轮结果
      setSubmittedKeys(new Set());
      setSubmittingType(null);
      abortRef.current = null;
    }
  }, [open, record?.applicationId]);

  // 卸载时中止进行中的 SSE
  useEffect(() => {
    return () => {
      abortRef.current?.abort();
    };
  }, []);

  const handleStart = () => {
    if (!record) return;
    abortRef.current?.abort();
    // 前置 false：重试/新请求时不被旧值绕过 done 兜底
    resultReceived.current = false;
    setPhase('generating');
    setProgressSteps([]);
    setErrorMsg('');
    setErrorRetryable(true);
    abortRef.current = generateInterviewQuestions(
      record.jobId,
      { applicationId: record.applicationId, difficulty },
      {
        onProgress: (p) => setProgressSteps((prev) => upsertProgress(prev, p)),
        onResult: (r) => {
          resultReceived.current = true;
          setResult(r);
          setPhase('success');
        },
        onError: (e) => {
          setErrorMsg(ERROR_TEXT[e.code ?? -1] ?? e?.message ?? '出题失败，请重试');
          setErrorRetryable(e.retryable ?? !NON_RETRYABLE_ERROR_CODES.has(e.code ?? -1));
          setPhase('failed');
        },
        onDone: () => {
          // 兜底：未收到 result（流异常/协议缺结果）→ 失败态，避免永久转圈
          if (!resultReceived.current) {
            setErrorMsg('出题结果不完整，请重试');
            setErrorRetryable(true);
            setPhase('failed');
          }
        },
      },
    );
  };

  const handleCancel = () => {
    abortRef.current?.abort();
    onClose();
  };

  /** 阶段6.3：单题申请入库（仅 AI_GENERATED 题；type→questionType 映射、透传 evaluationDimensions，不传控制字段） */
  const handleSubmitToLibrary = async (q: HrInterviewQuestion) => {
    if (!record || submittingType) return;
    setSubmittingType(q.type);
    try {
      const dto: AgentQuestionSubmitRequest = {
        questionType: q.type,
        difficulty: q.difficulty,
        content: q.content,
        keyPoints: q.keyPoints || undefined,
        referenceAnswer: q.referenceAnswer || undefined,
        evaluationDimensions: q.evaluationDimensions?.length ? q.evaluationDimensions : undefined,
      };
      await submitQuestionToLibrary(record.jobId, dto);
      setSubmittedKeys((prev) => new Set(prev).add(q.type));
      message.success('已提交题库审核');
    } catch {
      // 拦截器已提示（2404/400/2101/403），不置「已提交审核」——不误报成功
    } finally {
      setSubmittingType(null);
    }
  };

  const renderBody = () => {
    if (phase === 'prep') {
      return (
        <div className={styles.prepWrap}>
          <div className={styles.recordInfo}>
            <div className={styles.recordName}>{record?.candidateName}</div>
            <div className={styles.recordJob}>{record?.jobTitle}</div>
          </div>
          <div className={styles.difficultyRow}>
            <span className={styles.difficultyLabel}>出题难度</span>
            <Select
              value={difficulty}
              onChange={(v) => setDifficulty(v)}
              className={styles.difficultySelect}
              options={[
                { value: QD.EASY, label: '简单' },
                { value: QD.MEDIUM, label: '中等' },
                { value: QD.HARD, label: '困难' },
              ]}
            />
          </div>
          <Button
            type="primary"
            className={styles.startBtn}
            icon={<ThunderboltOutlined />}
            onClick={handleStart}
          >
            开始出题
          </Button>
          <div className={styles.prepTip}>
            将按 4 类题型（基础/项目/边界/综合）生成题目：优先命中企业题库，缺失题型由 AI 补题
          </div>
        </div>
      );
    }

    if (phase === 'generating') {
      return (
        <div className={styles.genWrap}>
          <Spin />
          <div className={styles.genTitle}>正在生成面试题...</div>
          {progressSteps.length > 0 && (
            <div className={styles.genSteps}>
              {progressSteps.map((step) => (
                <div key={step.sequence} className={styles.genStep}>
                  <span className={styles.genStepIcon}>✓</span>
                  <span className={styles.genStepText}>{step.message}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      );
    }

    if (phase === 'failed') {
      return (
        <div className={styles.failWrap}>
          <div className={styles.failText}>{errorMsg}</div>
          <Space>
            {errorRetryable && (
              <Button
                type="primary"
                icon={<ReloadOutlined />}
                onClick={handleStart}
              >
                重试
              </Button>
            )}
            <Button onClick={handleCancel}>关闭</Button>
          </Space>
        </div>
      );
    }

    // success
    const mode = result ? MODE_MAP[result.generationMode] : null;
    const questions = result?.questions ?? [];
    return (
      <div className={styles.successWrap}>
        {mode && (
          <div className={styles.modeBar}>
            <Tag color={mode.color}>{mode.label}</Tag>
            <span className={styles.modeHint}>
              {result?.isPersonalized ? '已按候选人简历个性化' : '简历信息不足，未个性化'}
            </span>
          </div>
        )}
        {questions.length === 0 ? (
          <Empty description="未生成题目" />
        ) : (
          <div className={styles.questionList}>
            {questions.map((q, idx) => (
              <div key={q.type} className={styles.questionCard}>
                <div className={styles.questionHeader}>
                  <span className={styles.questionIndex}>{idx + 1}</span>
                  <Tag color="blue">{TYPE_TEXT[q.type] || q.type}</Tag>
                  <Tag>{DIFF_TEXT[q.difficulty] || q.difficulty}</Tag>
                  <Tag
                    color={q.sourceType === 'COMPANY_LIBRARY' ? 'green' : 'default'}
                  >
                    {q.sourceType === 'COMPANY_LIBRARY' ? '企业题库' : 'AI 生成'}
                  </Tag>
                  {/* 阶段6.3：仅面试官 + 仅 AI 生成题可申请入库（企业题库命中题天然已入库，不显示） */}
                  {isInterviewer && q.sourceType === 'AI_GENERATED' && (
                    <Button
                      size="small"
                      type="link"
                      className={styles.submitBtn}
                      icon={<SendOutlined />}
                      loading={submittingType === q.type}
                      disabled={submittedKeys.has(q.type) || submittingType !== null}
                      onClick={() => handleSubmitToLibrary(q)}
                    >
                      {submittedKeys.has(q.type) ? '已提交审核' : '申请入库'}
                    </Button>
                  )}
                </div>
                <div className={styles.questionContent}>{q.content}</div>
                {(q.keyPoints || q.referenceAnswer) && (
                  <Collapse
                    ghost
                    size="small"
                    items={[
                      ...(q.keyPoints
                        ? [
                            {
                              key: 'points',
                              label: '考察要点',
                              children: <div className={styles.collapseText}>{q.keyPoints}</div>,
                            },
                          ]
                        : []),
                      ...(q.referenceAnswer
                        ? [
                            {
                              key: 'answer',
                              label: '参考答案',
                              children: <div className={styles.collapseText}>{q.referenceAnswer}</div>,
                            },
                          ]
                        : []),
                    ]}
                  />
                )}
              </div>
            ))}
          </div>
        )}
      </div>
    );
  };

  return (
    <Modal
      title="面试出题"
      open={open}
      onCancel={handleCancel}
      footer={null}
      width={640}
      destroyOnClose
      className={styles.modal}
    >
      {open ? renderBody() : null}
    </Modal>
  );
};

export default QuestionGenerateModal;
