/**
 * C端模拟面试（/candidate/mock-interview）
 * 四阶段：选题准备 → SSE 生成题目 → 逐题作答/跳过 → 面试报告；
 * 流程状态机与 SSE 封装在 useMockInterview，本页负责渲染、表单校验（答案≥20字）
 * 与评分展示；支持断点恢复（localStorage 存 sessionId，刷新后定位未答题）。
 */
import React, { useState, useEffect } from 'react';
import { Select, Input, Button, Progress, Tag, Modal, message, Segmented } from 'antd';
import {
  TrophyOutlined,
  RightOutlined,
  ReloadOutlined,
  CheckCircleOutlined,
  CloseCircleOutlined,
  LoadingOutlined,
  CheckOutlined,
} from '@ant-design/icons';
import { JobStatus } from '@/constants/enums';
import { getJobList } from '@/services/job';
import { getResumeList } from '@/services/resume';
import { useMockInterview } from '@/hooks/useMockInterview';
import styles from './index.less';

interface JobOption {
  id: number;
  title: string;
  companyName: string;
}

interface ResumeOption {
  id: number;
  name: string;
}

const questionTypeMap: Record<string, string> = {
  BASIC: '基础验证',
  PROJECT: '项目深挖',
  BOUNDARY: '能力边界',
  COMPREHENSIVE: '综合素养',
};

const difficultyMap: Record<string, string> = {
  EASY: '简单',
  MEDIUM: '中等',
  HARD: '困难',
};

const difficultyColorMap: Record<string, string> = {
  EASY: 'green',
  MEDIUM: 'blue',
  HARD: 'orange',
};

/** 题目数量可选值（后端支持 5/8/10） */
const QUESTION_COUNT_OPTIONS = [
  { label: '5 题', value: 5 },
  { label: '8 题', value: 8 },
  { label: '10 题', value: 10 },
] as const;

type QuestionCount = (typeof QUESTION_COUNT_OPTIONS)[number]['value'];

/** 兼容数组 / { list } / { records } 三种响应结构 */
function normalizeList(data: any): any[] {
  if (Array.isArray(data)) return data;
  if (Array.isArray(data?.list)) return data.list;
  if (Array.isArray(data?.records)) return data.records;
  return [];
}

/** 数字滚动：rAF + cubic ease-out，尊重 prefers-reduced-motion */
function useCountUp(target: number, duration = 1200, delay = 80): number {
  const [value, setValue] = useState(0);
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setValue(target);
      return;
    }
    setValue(0);
    let raf = 0;
    let start: number | null = null;
    const timer = window.setTimeout(() => {
      const ease = (t: number) => 1 - Math.pow(1 - t, 3);
      const tick = (ts: number) => {
        if (start === null) start = ts;
        const p = Math.min((ts - start) / duration, 1);
        setValue(Math.round(ease(p) * target));
        if (p < 1) raf = requestAnimationFrame(tick);
      };
      raf = requestAnimationFrame(tick);
    }, delay);
    return () => {
      clearTimeout(timer);
      cancelAnimationFrame(raf);
    };
  }, [target, duration, delay]);
  return value;
}

/** 圆形评分仪表（SVG 渐变环 + 数字滚动） */
const ScoreGauge: React.FC<{
  value: number;
  size?: number;
  label?: string;
  stroke?: number;
}> = ({ value, size = 120, label, stroke = 10 }) => {
  const animated = useCountUp(value);
  const [mounted, setMounted] = useState(false);
  useEffect(() => {
    if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) {
      setMounted(true);
      return;
    }
    const id = requestAnimationFrame(() => setMounted(true));
    return () => cancelAnimationFrame(id);
  }, []);
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  const pct = Math.min(Math.max(value, 0), 100);
  return (
    <div className={styles.gauge} style={{ width: size, height: size }}>
      <svg width={size} height={size} className={styles.gaugeSvg}>
        <defs>
          <linearGradient id="miGaugeGrad" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stopColor="var(--accent)" />
            <stop offset="100%" stopColor="var(--accent-3)" />
          </linearGradient>
        </defs>
        <circle
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="rgba(0, 0, 0, 0.06)"
          strokeWidth={stroke}
        />
        <circle
          className={styles.gaugeBar}
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          stroke="url(#miGaugeGrad)"
          strokeWidth={stroke}
          strokeLinecap="round"
          strokeDasharray={c}
          strokeDashoffset={mounted ? c * (1 - pct / 100) : c}
          transform={`rotate(-90 ${size / 2} ${size / 2})`}
        />
      </svg>
      <div className={styles.gaugeCenter}>
        <span className={styles.gaugeNum} style={{ fontSize: Math.round(size * 0.3) }}>
          {animated}
        </span>
        {label && <span className={styles.gaugeLabel}>{label}</span>}
      </div>
    </div>
  );
};

const MockInterviewPage: React.FC = () => {
  const {
    phase,
    progressSteps,
    questions,
    currentIndex,
    currentScore,
    report,
    busy,
    totalCount,
    startInterview,
    submitAnswerText,
    skipCurrentQuestion,
    goToNextQuestion,
    generateReport,
    restart,
  } = useMockInterview();

  // ===== 准备阶段本地状态 =====
  const [jobs, setJobs] = useState<JobOption[]>([]);
  const [jobSource, setJobSource] = useState<'remote' | 'mock'>('remote');
  const [resumes, setResumes] = useState<ResumeOption[]>([]);
  const [selectedJobId, setSelectedJobId] = useState<number | undefined>(undefined);
  const [resumeId, setResumeId] = useState<number | undefined>(undefined);
  const [questionCount, setQuestionCount] = useState<QuestionCount>(5);
  const [answerText, setAnswerText] = useState('');

  useEffect(() => {
    getJobList({ status: JobStatus.PUBLISHED, page: 1, size: 50 })
      .then((data) => {
        setJobs(
          normalizeList(data).map((j: any) => ({
            id: j.jobId ?? j.id,
            title: j.title,
            companyName: j.companyName || '',
          })),
        );
        setJobSource('remote');
      })
      .catch(() => {
        // 岗位服务不可用时不再降级演示数据（C 端岗位已联调真实接口），提示重试
        setJobs([]);
        setJobSource('mock');
      });

    getResumeList()
      .then((data) => {
        setResumes(
          normalizeList(data).map((r: any) => ({
            id: r.id,
            name: r.fileName || r.name || `简历${r.id}`,
          })),
        );
      })
      .catch(() => {
        /* 简历可选，拉取失败不阻塞 */
      });
  }, []);

  const handleStart = () => {
    if (!selectedJobId) {
      message.warning('请先选择面试岗位');
      return;
    }
    setAnswerText('');
    startInterview({ jobId: selectedJobId, questionCount, resumeId });
  };

  const handleSubmit = () => {
    if (answerText.trim().length < 20) {
      message.warning('答案需要至少20个字符');
      return;
    }
    submitAnswerText(answerText);
  };

  const handleSkip = () => {
    Modal.confirm({
      title: '跳过此题',
      content: '跳过此题将计 0 分，确定跳过吗？',
      okText: '确定跳过',
      cancelText: '继续作答',
      onOk: () => skipCurrentQuestion(),
    });
  };

  const handleNext = () => {
    setAnswerText('');
    goToNextQuestion();
  };

  const currentQuestion = questions[currentIndex];
  const isLastQuestion = currentIndex + 1 >= totalCount;

  return (
    <div className={styles.page}>
      {phase === 'prep' && (
        <div className={styles.prepHero}>
          <div className={styles.prepAurora} />
          <div className={styles.prepOrb}>
            <div className={styles.orbCore} />
            <div className={`${styles.orbRing} ${styles.orbRing1}`} />
            <div className={`${styles.orbRing} ${styles.orbRing2}`} />
          </div>
          <div className={styles.prepContent}>
            <div className={styles.prepBadge}>
              <TrophyOutlined />
              AI 模拟面试
            </div>
            <div className={styles.phaseTitle}>模拟面试</div>
            <div className={styles.phaseDesc}>
              AI模拟真实面试场景，帮助你提升面试技巧，增强自信心
            </div>

            <div className={styles.featurePills}>
              {['AI 智能评分', '多维能力报告', '真实面试模拟'].map((f) => (
                <span key={f} className={styles.featurePill}>
                  {f}
                </span>
              ))}
            </div>

            <div className={styles.prepGlass}>
              <div className={styles.phaseControls}>
                <Select
                  placeholder="选择面试岗位"
                  value={selectedJobId}
                  onChange={setSelectedJobId}
                  style={{ width: 300 }}
                  showSearch
                  optionFilterProp="label"
                  options={jobs.map((j) => ({
                    value: j.id,
                    label: j.companyName ? `${j.title} - ${j.companyName}` : j.title,
                  }))}
                />
                {resumes.length > 0 && (
                  <Select
                    placeholder="选择简历（选填）"
                    value={resumeId}
                    onChange={setResumeId}
                    allowClear
                    style={{ width: 220 }}
                    options={resumes.map((r) => ({ value: r.id, label: r.name }))}
                  />
                )}
                <div className={styles.countSelector}>
                  <span className={styles.countLabel}>题目数量</span>
                  <Segmented
                    value={questionCount}
                    onChange={(v) => setQuestionCount(v as QuestionCount)}
                    options={[...QUESTION_COUNT_OPTIONS]}
                  />
                </div>
              </div>
            </div>

            {jobSource === 'mock' && (
              <div className={styles.jobSourceTip}>岗位服务暂不可用，请稍后重试</div>
            )}

            <button type="button" className={styles.startBtn} onClick={handleStart} disabled={busy}>
              开始模拟面试 <RightOutlined />
            </button>

            <div className={styles.phaseMeta}>共 {questionCount} 道题，预计用时 {questionCount * 3} 分钟</div>
          </div>
        </div>
      )}

      {phase === 'generating' && (
        <div className={styles.genPanel}>
          <div className={styles.genRadar}>
            <div className={styles.radarRings} />
            <div className={styles.radarSweep} />
            <div className={styles.radarDot} />
          </div>
          <div className={styles.genTitle}>
            <span className={styles.genLiveDot} />
            正在准备面试题目...
          </div>
          {progressSteps.length === 0 ? (
            <div className={styles.genLoading}>
              <LoadingOutlined style={{ fontSize: 40, color: 'var(--accent)' }} />
            </div>
          ) : (
            <div className={styles.genPipeline}>
              {progressSteps.map((step, i) => {
                const isLast = i === progressSteps.length - 1;
                return (
                  <div
                    key={step.sequence}
                    className={`${styles.genStep} ${isLast ? styles.genStepActive : styles.genStepDone}`}
                  >
                    <div className={styles.genStepRail}>
                      <span className={styles.genStepIcon}>
                        {isLast ? <LoadingOutlined /> : <CheckOutlined />}
                      </span>
                    </div>
                    <div className={styles.genStepBody}>
                      <div className={styles.genStepText}>{step.message}</div>
                      <div className={styles.genStepCode}>{step.code}</div>
                    </div>
                  </div>
                );
              })}
            </div>
          )}
        </div>
      )}

      {phase === 'answering' && currentQuestion && (
        <div className={styles.questionCard}>
          <div className={styles.questionHeader}>
            <div className={styles.qHeaderLeft}>
              <span className={styles.qProgressLabel}>QUESTION</span>
              <span className={styles.qProgressNum}>
                第 {currentIndex + 1} / {totalCount} 题
              </span>
            </div>
            <div className={styles.qStatus}>
              <span className={styles.dotLive} />
              作答中
            </div>
          </div>

          <div className={styles.qProgressTrack}>
            <Progress
              percent={Math.round((currentIndex / totalCount) * 100)}
              showInfo={false}
              strokeColor={{ from: 'var(--accent)', to: 'var(--accent-3)' }}
              trailColor="rgba(0, 0, 0, 0.06)"
              size="small"
              className={styles.qProgressBar}
              style={{ width: '100%' }}
            />
          </div>

          <div className={styles.questionMeta}>
            <Tag color="var(--accent)">{questionTypeMap[currentQuestion.questionType] || currentQuestion.questionType}</Tag>
            <Tag color={difficultyColorMap[currentQuestion.difficulty] || 'default'}>
              {difficultyMap[currentQuestion.difficulty] || currentQuestion.difficulty}
            </Tag>
            {currentQuestion.dimension && <Tag>{currentQuestion.dimension}</Tag>}
          </div>

          <div className={styles.questionText}>{currentQuestion.content}</div>

          {currentScore ? (
            <div className={styles.scorePanel}>
              <div className={styles.scorePanelHead}>
                <ScoreGauge value={currentScore.overallScore} size={120} label="综合得分" />
                <div className={styles.scoreDims}>
                  {[
                    { label: '技术准确度', value: currentScore.techAccuracyScore },
                    { label: '表达逻辑', value: currentScore.expressionScore },
                    { label: '知识深度', value: currentScore.knowledgeDepthScore },
                  ].map((d) => (
                    <div key={d.label} className={styles.scoreDim}>
                      <div className={styles.scoreDimHead}>
                        <span className={styles.scoreDimLabel}>{d.label}</span>
                        <span className={styles.scoreDimValue}>{d.value}</span>
                      </div>
                      <div className={styles.dimBar}>
                        <div
                          className={styles.dimBarFill}
                          style={{ width: `${Math.min(Math.max(d.value, 0), 100)}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              </div>
              {currentScore.aiComment && (
                <div className={styles.scoreComment}>{currentScore.aiComment}</div>
              )}
              <div className={styles.questionActions}>
                <Button type="primary" onClick={handleNext} disabled={busy}>
                  {isLastQuestion ? '查看面试报告' : '查看下一题'} <RightOutlined />
                </Button>
              </div>
            </div>
          ) : (
            <>
              <div className={styles.answerArea}>
                <div className={styles.answerHead}>
                  <span className={styles.answerTitle}>你的回答</span>
                  <span className={styles.answerTip}>至少 20 个字符</span>
                </div>
                <Input.TextArea
                  className={styles.answerTextarea}
                  placeholder="请输入你的回答（至少20个字符）..."
                  value={answerText}
                  onChange={(e) => setAnswerText(e.target.value)}
                  rows={6}
                  autoSize={{ minRows: 5, maxRows: 12 }}
                  showCount
                  minLength={20}
                  disabled={busy}
                />
              </div>
              <div className={styles.questionActions}>
                <Button onClick={handleSkip} disabled={busy}>
                  跳过
                </Button>
                <Button type="primary" onClick={handleSubmit} loading={busy}>
                  提交回答
                </Button>
              </div>
            </>
          )}
        </div>
      )}

      {phase === 'report' && report && (
        <div className={styles.reportCard}>
          <div className={styles.reportHead}>
            <div className={styles.reportScore}>
              <ScoreGauge value={report.overallScore} size={168} stroke={12} label="面试综合评分" />
              {report.overallLevel && (
                <Tag
                  color={
                    report.overallScore >= 85 ? 'green' : report.overallScore >= 70 ? 'blue' : 'orange'
                  }
                  className={styles.reportLevel}
                >
                  {report.overallLevel}
                </Tag>
              )}
            </div>
            {report.statistics &&
              (report.statistics.totalCount != null ||
                report.statistics.answeredCount != null ||
                report.statistics.skippedCount != null) && (
                <div className={styles.reportStats}>
                  {[
                    ['题目总数', report.statistics.totalCount],
                    ['已作答', report.statistics.answeredCount],
                    ['跳过', report.statistics.skippedCount],
                  ].map(([label, v]) => (
                    <div key={label} className={styles.reportStat}>
                      <b className={styles.reportStatNum}>{v ?? '-'}</b>
                      <span className={styles.reportStatLabel}>{label}</span>
                    </div>
                  ))}
                </div>
              )}
          </div>

          {report.dimensionScores && report.dimensionScores.length > 0 && (
            <div className={styles.reportSections}>
              {report.dimensionScores.map((d) => (
                <div key={d.name} className={styles.reportSection}>
                  <div className={styles.reportSectionTitle}>{d.name}</div>
                  <div className={styles.reportSectionBar}>
                    <div
                      className={styles.reportSectionFill}
                      style={{ width: `${Math.min(Math.max(d.score, 0), 100)}%` }}
                    />
                  </div>
                  <div className={styles.reportSectionValue}>{d.score}</div>
                </div>
              ))}
            </div>
          )}

          <div className={styles.reportColumns}>
            <div className={styles.reportCol}>
              <div className={styles.reportColTitle}>面试亮点</div>
              {report.highlights?.map((s, i) => (
                <div key={i} className={`${styles.reportListItem} ${styles.reportListGreen}`}>
                  <CheckCircleOutlined style={{ marginRight: 8 }} />
                  {s}
                </div>
              ))}
            </div>
            <div className={styles.reportCol}>
              <div className={styles.reportColTitle}>待提升</div>
              {report.weaknesses?.map((w, i) => (
                <div key={i} className={`${styles.reportListItem} ${styles.reportListRed}`}>
                  <CloseCircleOutlined style={{ marginRight: 8 }} />
                  {w}
                </div>
              ))}
            </div>
          </div>

          {report.improvementPlan && (
            <div className={styles.reportPlan}>
              <div className={styles.reportPlanTitle}>提升方案</div>
              <div className={styles.reportPlanContent} style={{ whiteSpace: 'pre-wrap' }}>
                {report.improvementPlan}
              </div>
            </div>
          )}

          {report.questionReviews && report.questionReviews.length > 0 && (
            <div className={styles.reviewList}>
              <div className={styles.reviewListTitle}>逐题回顾</div>
              {report.questionReviews.map((rv) => (
                <div key={rv.questionNumber} className={styles.reviewItem}>
                  <div className={styles.reviewHeader}>
                    <span className={styles.reviewQn}>第 {rv.questionNumber} 题</span>
                    <span className={styles.reviewScore}>得分 {rv.score ?? '-'}</span>
                  </div>
                  <div className={styles.reviewContent}>{rv.content}</div>
                  {rv.userAnswer && (
                    <div className={styles.reviewAnswer}>我的回答：{rv.userAnswer}</div>
                  )}
                  {rv.aiComment && <div className={styles.reviewComment}>AI点评：{rv.aiComment}</div>}
                </div>
              ))}
            </div>
          )}

          <button type="button" className={styles.endBtn} onClick={restart}>
            <ReloadOutlined /> 重新开始
          </button>
        </div>
      )}

      {phase === 'report' && !report && (
        <div className={styles.genPanel}>
          {busy ? (
            <>
              <div className={styles.genRadar}>
                <div className={styles.radarRings} />
                <div className={styles.radarSweep} />
                <div className={styles.radarDot} />
              </div>
              <div className={styles.genTitle}>
                <span className={styles.genLiveDot} />
                正在生成面试报告...
              </div>
            </>
          ) : (
            <>
              <div className={styles.phaseTitle}>报告生成失败</div>
              <button type="button" className={styles.startBtn} onClick={() => generateReport()}>
                重新生成 <RightOutlined />
              </button>
            </>
          )}
        </div>
      )}
    </div>
  );
};

export default MockInterviewPage;
