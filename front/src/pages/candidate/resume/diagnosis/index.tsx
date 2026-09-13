import React, { useState, useEffect, useRef } from 'react';
import { Input, Button, Spin, message, Tooltip } from 'antd';
import { SmileOutlined, FormOutlined, SyncOutlined } from '@ant-design/icons';
import { useNavigate, useParams } from 'umi';
import MarkdownRenderer from '@/components/MarkdownRenderer';
import AbilityRadar from './components/AbilityRadar';
import { ROUTES } from '@/constants/routes';
import { tokenManager } from '@/utils/token';
import {
  ensureNotificationPermission,
  markDiagnosisNotified,
  resetDiagnosisNotified,
} from '@/utils/diagnosisNotification';
import {
  getResumeDetail,
  getResumeList,
  getAbilityModel,
  getDiagnosisStreamUrl,
  getDiagnosisHistory,
  getDiagnosisDetail,
  getDiagnosisTaskStatus,
  PENDING_DIAGNOSIS_PREFIX,
} from '@/services/resume';
import type {
  ResumeDetail,
  AbilityModel,
  DiagnosisFinalEvent,
  DiagnosisHistoryItem,
  DiagnosisTaskStatus,
} from '@/services/resume';
import styles from './index.less';

/** 能力模型 5 维配置（对齐后端 AbilityModelVO 字段） */
type AbilityScoreKey =
  | 'professionalSkillScore'
  | 'workExperienceScore'
  | 'industryKnowledgeScore'
  | 'comprehensiveQualityScore'
  | 'learningGrowthScore';

const ABILITY_DIMENSIONS: { key: AbilityScoreKey; label: string; color: string }[] = [
  { key: 'professionalSkillScore', label: '专业技能', color: '#FF6B6B' },
  { key: 'workExperienceScore', label: '工作经验', color: '#FF8E72' },
  { key: 'industryKnowledgeScore', label: '行业认知', color: '#FFA07A' },
  { key: 'comprehensiveQualityScore', label: '综合素质', color: '#52c41a' },
  { key: 'learningGrowthScore', label: '学习成长', color: '#D97706' },
];

/** 诊断状态：待诊断 / 诊断中 / 已出报告 */
type DiagPhase = 'idle' | 'diagnosing' | 'done';

/** 个人信息类章节标题：与左栏"姓名"行重复，渲染时跳过（信息已由独立列展示） */
const PERSONAL_SECTION_TITLES = ['基本信息', '个人信息', '基本资料', '个人资料', '联系方式'];

/** 方案C：任务状态轮询间隔（毫秒） */
const POLL_INTERVAL_MS = 2500;

/** 活跃任务 localStorage key：diagnosis_pending:{resumeId}:{careerBase64} */
const storageKeyOf = (resumeId: string, careerText: string) =>
  `${PENDING_DIAGNOSIS_PREFIX}${resumeId}:${btoa(encodeURIComponent(careerText))}`;

/** 从 localStorage key 反解职业名（解码失败返回 null） */
const careerFromStorageKey = (key: string): string | null => {
  const parts = key.split(':');
  if (parts.length !== 3) return null;
  try {
    return decodeURIComponent(atob(parts[2]));
  } catch {
    return null;
  }
};

const ResumeDiagnosisPage: React.FC = () => {
  const navigate = useNavigate();
  const { id } = useParams<{ id: string }>();
  // 简历 ID 为字符串：雪花 ID（19 位）超出 JS Number 安全整数范围，不可 Number() 转换
  const resumeId = id ?? '';
  const isMountedRef = useRef(true);
  /** 诊断 fetch 取消器：跳页时正确释放网络资源（任务在后台继续跑，不打断） */
  const abortRef = useRef<AbortController | null>(null);
  /** 任务状态轮询定时器 */
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  /** 轮询目标职业（防止多任务交错轮询） */
  const pollingCareerRef = useRef('');

  // 页面基础数据
  const [detail, setDetail] = useState<ResumeDetail | null>(null);
  const [abilityModel, setAbilityModel] = useState<AbilityModel | null>(null);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState(false);

  // 诊断交互
  const [career, setCareer] = useState('');
  const [phase, setPhase] = useState<DiagPhase>('idle');
  const [agentHint, setAgentHint] = useState('');
  const [result, setResult] = useState<DiagnosisFinalEvent | null>(null);
  const [history, setHistory] = useState<DiagnosisHistoryItem[]>([]);
  const [historyLoading, setHistoryLoading] = useState(false);

  /** 加载简历详情 + 能力模型 + 诊断历史（简历不存在时自动跳转默认简历——入口硬编码 id=1 的兜底） */
  const fetchData = async () => {
    setLoading(true);
    setLoadError(false);
    try {
      const [detailData, modelData, historyData] = await Promise.all([
        getResumeDetail(resumeId).catch(() => null),
        getAbilityModel(resumeId).catch(() => null),
        getDiagnosisHistory(resumeId).catch(() => []),
      ]);
      if (!isMountedRef.current) return;
      if (!detailData) {
        // 简历不存在（URL 可能是硬编码 id=1）：尝试自动跳转到默认简历
        const listData = await getResumeList({ page: 1, pageSize: 5 }).catch(() => null);
        const defaultResume =
          listData?.list?.find((i) => i.isDefault === 1) ?? listData?.list?.[0];
        if (defaultResume && defaultResume.id !== resumeId) {
          navigate(`${ROUTES.CANDIDATE_RESUME_DIAGNOSIS}/${defaultResume.id}/diagnosis`, {
            replace: true,
          });
          return; // 等待重定向后的加载
        }
      }
      setDetail(detailData);
      setAbilityModel(modelData);
      setHistory(Array.isArray(historyData) ? historyData : []);
      // 方案C回页检测：本简历存在后台诊断任务时自动接管（轮询 / 直接展示结果）
      checkPendingDiagnosis();
    } catch {
      if (isMountedRef.current) setLoadError(true);
    } finally {
      if (isMountedRef.current) setLoading(false);
    }
  };

  useEffect(() => {
    isMountedRef.current = true;
    fetchData();
    return () => {
      isMountedRef.current = false;
      // 跳页：取消进行中的 fetch（任务在后台继续跑），停止轮询，释放定时器
      abortRef.current?.abort();
      abortRef.current = null;
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
      pollingCareerRef.current = '';
    };
  }, [resumeId]);

  // ==================== SSE 流式诊断（fetch + POST，页内解析命名事件） ====================

  /** 解析单个 SSE 数据块（event:/data: 行） */
  const handleSSEBlock = (block: string) => {
    let eventName = 'message';
    const dataLines: string[] = [];
    for (const line of block.split('\n')) {
      if (line.startsWith('event:')) {
        eventName = line.slice(6).trim();
      } else if (line.startsWith('data:')) {
        dataLines.push(line.slice(5).trim());
      }
    }
    const data = dataLines.join('\n');
    if (eventName === 'heartbeat' || !data) {
      return; // 心跳保活，忽略
    }
    let payload: unknown;
    try {
      payload = JSON.parse(data);
    } catch {
      return; // 非 JSON 数据忽略
    }
    handleDiagEvent(eventName, payload);
  };

  /** 按事件类型驱动 UI */
  const handleDiagEvent = (eventName: string, payload: unknown) => {
    if (eventName === 'thinking') {
      const t = payload as { content?: string };
      setAgentHint(t?.content ?? '');
    } else if (eventName === 'submitted') {
      // 方案C：任务已提交后台执行（连接即刻返回），转轮询感知完成
      const s = payload as { career?: string };
      if (s?.career) startPolling(s.career);
    } else if (eventName === 'final') {
      const f = payload as DiagnosisFinalEvent;
      stopPolling();
      // 用户在诊断页内已看到报告 → 标记已见，抑制系统通知（否则与弹窗重复打扰）
      markDiagnosisNotified(resumeId, f.career);
      // 注意：不清除 localStorage 活跃任务 key——诊断完成提示（导航栏绿点）依赖它。
      // 清理时机：用户点击导航进入诊断页（checkPendingDiagnosis COMPLETED 分支）或
      // 重新诊断覆盖。
      setResult({ ...f, reportMd: normalizeReportMd(f.reportMd) });
      // 诊断回写后的能力模型（snake_case → camelCase）刷新雷达图，与本次报告逐维度一致
      const fresh = f.abilityModel?.scores;
      if (fresh) {
        setAbilityModel({
          resumeId,
          professionalSkillScore: fresh.professional_skill ?? 0,
          workExperienceScore: fresh.work_experience ?? 0,
          industryKnowledgeScore: fresh.industry_knowledge ?? 0,
          comprehensiveQualityScore: fresh.comprehensive_quality ?? 0,
          learningGrowthScore: fresh.learning_growth ?? 0,
          subDimensions: null,
          updatedAt: '',
        });
      }
      setPhase('done');
      setAgentHint('');
      // 诊断完成：刷新历史列表
      fetchHistory();
    } else if (eventName === 'error') {
      const err = payload as { code?: number; message?: string };
      stopPolling();
      clearPendingStorage(career.trim());
      message.error(err?.message || '诊断失败，请重试');
      setPhase('idle');
      setAgentHint('');
    }
  };

  /** 发起诊断（POST + fetch 流式读取）；refresh=true 强制重新生成（跳过缓存） */
  const handleDiagnose = async (refresh = false) => {
    const careerText = career.trim();
    if (!careerText) {
      message.warning('请输入目标职业名');
      return;
    }
    setPhase('diagnosing');
    setResult(null);
    setAgentHint(refresh ? '正在强制重新生成诊断…' : '正在准备诊断…');
    // 方案C：记录活跃任务（CandidateLayout 导航栏提示 + 系统通知依据），跳页后回页自动接管
    // 用户手势内请求通知授权（首次）；重置已见标记，保证本次完成重新提醒
    ensureNotificationPermission();
    resetDiagnosisNotified(resumeId, careerText);
    writePendingStorage(careerText);
    const token = tokenManager.getToken();
    abortRef.current?.abort(); // 防连点：先取消旧请求再发起新诊断
    abortRef.current = new AbortController();
    try {
      const res = await fetch(getDiagnosisStreamUrl(resumeId), {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: JSON.stringify({ career: careerText, refresh }),
        signal: abortRef.current.signal,
      });
      if (!res.ok || !res.body) {
        throw new Error(`诊断连接失败（HTTP ${res.status}）`);
      }
      const reader = res.body.getReader();
      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      // eslint-disable-next-line no-constant-condition
      while (true) {
        const { done, value } = await reader.read();
        if (done) {
          break;
        }
        buffer += decoder.decode(value, { stream: true });
        // SSE 事件以空行分隔
        const blocks = buffer.split('\n\n');
        buffer = blocks.pop() ?? '';
        for (const block of blocks) {
          handleSSEBlock(block);
        }
      }
    } catch (err) {
      if (err instanceof DOMException && err.name === 'AbortError') {
        // 主动取消（跳页/重发）：任务在后台继续跑，回页轮询拿结果，不打扰用户
        return;
      }
      // 网络中断等：连接断了但任务可能已提交后台，转轮询兜底（同跳页语义）
      if (isMountedRef.current) {
        message.warning('诊断连接中断，任务将在后台继续执行');
        startPolling(careerText);
      }
    }
  };

  /** 加载诊断历史 */
  const fetchHistory = async () => {
    setHistoryLoading(true);
    try {
      const data = await getDiagnosisHistory(resumeId);
      if (isMountedRef.current) {
        setHistory(Array.isArray(data) ? data : []);
      }
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      if (isMountedRef.current) {
        setHistoryLoading(false);
      }
    }
  };

  // ==================== 方案C：任务状态轮询（跳页后回页自动接管） ====================

  /** 记录活跃任务（CandidateLayout 导航栏红点读取同一约定；异常静默，仅提示失效） */
  const writePendingStorage = (careerText: string) => {
    try {
      window.localStorage.setItem(storageKeyOf(resumeId, careerText), String(Date.now()));
    } catch {
      // localStorage 不可用（隐私模式/满）：静默
    }
  };

  /** 清除活跃任务记录（完成/失败/用户查看后） */
  const clearPendingStorage = (careerText: string) => {
    try {
      window.localStorage.removeItem(storageKeyOf(resumeId, careerText));
    } catch {
      // 忽略
    }
  };

  /** 停止轮询（完成/失败/过期/跳页） */
  const stopPolling = () => {
    if (pollTimerRef.current) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
    pollingCareerRef.current = '';
  };

  /** 开始轮询任务状态：立即查一次（回页场景）+ 每 2.5s 间隔；同职业重复调用幂等 */
  const startPolling = (careerText: string) => {
    if (pollingCareerRef.current === careerText && pollTimerRef.current) {
      return;
    }
    if (pollingCareerRef.current) {
      stopPolling();
    }
    pollingCareerRef.current = careerText;
    pollOnce(careerText);
    pollTimerRef.current = setInterval(() => pollOnce(careerText), POLL_INTERVAL_MS);
  };

  /** 单次轮询：按状态分发——COMPLETED 取报告 / FAILED 报错 / RUNNING 继续 / NONE 过期 */
  const pollOnce = async (careerText: string) => {
    if (!isMountedRef.current || pollingCareerRef.current !== careerText) {
      return;
    }
    let status: DiagnosisTaskStatus | null = null;
    try {
      status = await getDiagnosisTaskStatus(resumeId, careerText);
    } catch {
      return; // 网络错误沉默，下个间隔重试
    }
    if (!isMountedRef.current || pollingCareerRef.current !== careerText) {
      return;
    }
    if (status.state === 'COMPLETED' && status.reportId) {
      stopPolling();
      clearPendingStorage(careerText);
      // 回页轮询看到报告 → 标记已见（抑制系统通知）
      markDiagnosisNotified(resumeId, careerText);
      // 与在线路径一致：直接取报告详情展示
      const detailData = await getDiagnosisDetail(resumeId, status.reportId).catch(() => null);
      if (detailData && isMountedRef.current) {
        setResult({
          reportId: detailData.id,
          career: detailData.career,
          matchScore: detailData.matchScore,
          reportMd: normalizeReportMd(detailData.reportMd),
        });
        setPhase('done');
        setAgentHint('');
        setCareer(detailData.career);
        fetchHistory();
      }
    } else if (status.state === 'FAILED') {
      stopPolling();
      clearPendingStorage(careerText);
      message.error('诊断失败，请重试');
      if (isMountedRef.current) {
        setPhase('idle');
        setAgentHint('');
      }
    } else if (status.state === 'NONE') {
      // 任务已过期（>10min 或 Redis 异常）：停止轮询，页面仍显示"诊断中"时恢复待诊断
      stopPolling();
      clearPendingStorage(careerText);
      if (isMountedRef.current) {
        setPhase((prev) => (prev === 'diagnosing' ? 'idle' : prev));
        setAgentHint('');
      }
    }
    // RUNNING：继续等下一个间隔
  };

  /** 回页检测：扫描本简历的活跃任务 localStorage，接管后台诊断（轮询或直接展示结果） */
  const checkPendingDiagnosis = async () => {
    const prefix = `${PENDING_DIAGNOSIS_PREFIX}${resumeId}:`;
    const pendingCareers: string[] = [];
    try {
      for (let i = 0; i < window.localStorage.length; i++) {
        const key = window.localStorage.key(i);
        if (key?.startsWith(prefix)) {
          const careerText = careerFromStorageKey(key);
          if (careerText) {
            pendingCareers.push(careerText);
          }
        }
      }
    } catch {
      return; // localStorage 不可用：跳过回页接管
    }
    if (pendingCareers.length === 0) {
      return;
    }
    for (const careerText of pendingCareers) {
      let status: DiagnosisTaskStatus | null = null;
      try {
        status = await getDiagnosisTaskStatus(resumeId, careerText);
      } catch {
        continue; // 单次失败不阻塞其余任务
      }
      if (!isMountedRef.current) {
        return;
      }
      if (status.state === 'RUNNING') {
        // 有任务在跑：进入诊断中状态并开始轮询
        setCareer(careerText);
        setPhase('diagnosing');
        setAgentHint('检测到后台诊断任务，正在获取进度…');
        startPolling(careerText);
        return;
      }
      if (status.state === 'COMPLETED' && status.reportId) {
        clearPendingStorage(careerText);
        // 用户已进入诊断页看到结果 → 标记已见（抑制系统通知）
        markDiagnosisNotified(resumeId, careerText);
        const detailData = await getDiagnosisDetail(resumeId, status.reportId).catch(() => null);
        if (detailData && isMountedRef.current) {
          setCareer(careerText);
          setResult({
            reportId: detailData.id,
            career: detailData.career,
            matchScore: detailData.matchScore,
            reportMd: normalizeReportMd(detailData.reportMd),
          });
          setPhase('done');
          fetchHistory();
        }
        return;
      }
      // FAILED / NONE：无意义残留，清除记录
      clearPendingStorage(careerText);
    }
  };

  /**
   * 诊断报告 Markdown 规范化：
   * 1. JSON 外壳防御——LLM 输出带 {"report_md":...} 外壳且解析失败时（后端修复前
   *    落库的脏数据 / 后端提取兜底前的历史报告），优先尝试提取 report_md 字段；
   * 2. LLM 输出 JSON 时可能把换行转义成字面 `\n`（两个字符），替换为真实换行符，
   *    否则 Markdown 标题/列表无法渲染。
   */
  const normalizeReportMd = (md: string) => {
    const text = (md ?? '').trim();
    if (text.startsWith('{"')) {
      try {
        const parsed = JSON.parse(text) as { report_md?: string };
        if (typeof parsed?.report_md === 'string') {
          return parsed.report_md.replace(/\\n/g, '\n');
        }
      } catch {
        // 解析失败：尝试正则提取（容忍未转义引号导致的不合法 JSON）
      }
      const m = text.match(/"report_md"\s*:\s*"((?:[^"\\]|\\.)*)"/);
      if (m) {
        return m[1].replace(/\\"/g, '"').replace(/\\n/g, '\n');
      }
    }
    return text.replace(/\\n/g, '\n');
  };

  /** 查看历史报告详情 */
  const handleViewHistory = async (reportId: string) => {
    try {
      const detailData = await getDiagnosisDetail(resumeId, reportId);
      if (isMountedRef.current) {
        setResult({
          reportId: detailData.id,
          career: detailData.career,
          matchScore: detailData.matchScore,
          reportMd: normalizeReportMd(detailData.reportMd),
        });
        setPhase('done');
      }
    } catch {
      // 错误已由拦截器统一提示
    }
  };

  // ==================== 渲染 ====================

  if (loading) {
    return (
      <div className={styles.pageCenter}>
        <Spin size="large" />
      </div>
    );
  }

  if (loadError || !detail) {
    return (
      <div className={styles.pageCenter}>
        {loadError ? (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>🌐</div>
            <div className={styles.emptyTitle}>网络开小差了</div>
            <div className={styles.emptyDesc}>请检查网络连接后重试</div>
            <div className={styles.emptyActions}>
              <Button type="primary" onClick={fetchData}>
                重新加载
              </Button>
            </div>
          </div>
        ) : (
          <div className={styles.emptyState}>
            <div className={styles.emptyIcon}>📄</div>
            <div className={styles.emptyTitle}>还没有可诊断的简历</div>
            <div className={styles.emptyDesc}>
              上传简历后，选择目标岗位即可获得 AI 诊断建议
            </div>
            <div className={styles.emptyActions}>
              <Button
                type="primary"
                onClick={() => navigate(ROUTES.CANDIDATE_RESUME_UPLOAD)}
              >
                去上传简历
              </Button>
              <Button onClick={fetchData}>重新加载</Button>
            </div>
          </div>
        )}
      </div>
    );
  }

  const sections = (detail.cardStructure?.sections ?? []).filter(
    (s) => !PERSONAL_SECTION_TITLES.includes(s.title),
  );

  return (
    <div className={styles.page}>
      {/* 控制栏：职业名输入 + 诊断按钮 */}
      <div className={styles.controlBar}>
        <Input
          placeholder="请输入目标职业名，如：高级前端工程师"
          value={career}
          onChange={(e) => setCareer(e.target.value)}
          onPressEnter={() => handleDiagnose()}
          disabled={phase === 'diagnosing'}
          allowClear
          style={{ width: 360 }}
        />
        <Button
          type="primary"
          icon={<SmileOutlined />}
          onClick={() => handleDiagnose()}
          loading={phase === 'diagnosing'}
          size="large"
          style={{
            height: 44,
            borderRadius: 'var(--radius-md)',
            fontWeight: 650,
            fontFamily: 'var(--font-title)',
          }}
        >
          AI诊断
        </Button>
        <Button
          icon={<SyncOutlined />}
          onClick={() => handleDiagnose(true)}
          disabled={phase === 'diagnosing'}
          size="large"
          style={{
            height: 44,
            borderRadius: 'var(--radius-md)',
            fontWeight: 650,
            fontFamily: 'var(--font-title)',
          }}
        >
          重新生成
        </Button>
        <Button
          icon={<FormOutlined />}
          size="large"
          onClick={() => navigate(`${ROUTES.CANDIDATE_RESUME_PREVIEW}/${resumeId}/preview`)}
          style={{
            height: 44,
            borderRadius: 'var(--radius-md)',
            fontWeight: 650,
            fontFamily: 'var(--font-title)',
          }}
        >
          去编辑简历
        </Button>
      </div>

      <div className={styles.content}>
        {/* Left: Resume Info */}
        <div className={styles.card}>
          <div className={styles.cardTitle}>简历信息</div>
          <div className={styles.resumeInfo}>
            <div className={styles.resumeInfoRow}>
              <div className={styles.resumeInfoLabel}>姓名</div>
              <div>{detail.candidateName || '—'}</div>
            </div>
            {sections.length > 0 ? (
              sections.map((section, sectionIdx) => (
                <div key={sectionIdx} className={styles.resumeInfoRow}>
                  <div className={styles.resumeInfoLabel}>{section.title}</div>
                  <div>
                    {section.points.length > 0
                      ? section.points.map((p) => p.text).join('；')
                      : section.raw_text || '—'}
                  </div>
                </div>
              ))
            ) : (
              <div className={styles.resumeInfoRow}>
                <div className={styles.resumeInfoLabel}>简历内容</div>
                <div>暂无可展示内容</div>
              </div>
            )}
          </div>
        </div>

        {/* Center: Diagnosis Result */}
        <div className={styles.card}>
          <div className={styles.cardTitle}>诊断结果</div>

          {phase === 'idle' && (
            <div className={styles.diagnosisEmpty}>
              <div className={styles.diagnosisEmptyIcon}>🔍</div>
              <div>输入目标职业名后点击「AI诊断」</div>
              <div className={styles.diagnosisEmptyHint}>
                将针对该职业分析简历的匹配度与改进建议
              </div>
            </div>
          )}

          {phase === 'diagnosing' && (
            <div className={styles.diagnosisEmpty}>
              <Spin size="large" />
              <div className={styles.diagnosisEmptyHint}>
                {agentHint || 'AI 正在分析简历与目标职业的匹配度…'}
              </div>
            </div>
          )}

          {phase === 'done' && result && (
            <>
              <div className={styles.diagnosisScore}>
                <div className={styles.bigScore}>
                  {Math.round(result.matchScore)}%
                </div>
                <div className={styles.bigScoreLabel}>
                  与「{result.career}」的匹配度
                </div>
              </div>
              <div className={styles.reportBody}>
                <MarkdownRenderer content={result.reportMd} />
              </div>
            </>
          )}
        </div>

        {/* Right: Ability Radar */}
        <div className={styles.card}>
          <div className={styles.cardTitle}>能力维度</div>
          {abilityModel ? (
            <>
              <div className={styles.radarPlaceholder}>
                <AbilityRadar
                  data={ABILITY_DIMENSIONS.map((dim) => ({
                    label: dim.label,
                    value: abilityModel[dim.key] ?? 0,
                  }))}
                />
              </div>
              <div className={styles.radarDimensions}>
                {ABILITY_DIMENSIONS.map((dim) => (
                  <div key={dim.key} className={styles.radarDim}>
                    <span className={styles.radarDimLabel}>{dim.label}</span>
                    <div className={styles.radarDimBar}>
                      <div
                        className={styles.radarDimFill}
                        style={{
                          width: `${abilityModel[dim.key] ?? 0}%`,
                          background: dim.color,
                        }}
                      />
                    </div>
                    <span className={styles.radarDimScore}>
                      {abilityModel[dim.key] ?? 0}
                    </span>
                  </div>
                ))}
              </div>
            </>
          ) : (
            <div className={styles.radarEmpty}>
              <div>暂无能力模型数据</div>
              <div className={styles.radarEmptyHint}>
                简历解析完成后将自动生成 5 维能力评估
              </div>
            </div>
          )}

          {/* 诊断历史：固定位于雷达图下方，不受报告长度影响 */}
          <div className={styles.historySection}>
            <div className={styles.historyTitle}>
              历史诊断
              {historyLoading && <Spin size="small" style={{ marginLeft: 8 }} />}
            </div>
            {history.length > 0 ? (
              <div className={styles.historyList}>
                {history.map((item) => (
                  <Tooltip
                    key={item.id}
                    title={item.career}
                    placement="topLeft"
                  >
                    <div
                      className={styles.historyItem}
                      onClick={() => handleViewHistory(item.id)}
                    >
                      <span className={styles.historyCareer}>{item.career}</span>
                      <span className={styles.historyScore}>
                        {Math.round(item.matchScore)}%
                      </span>
                      <span className={styles.historyTime}>
                        {item.createdAt?.replace('T', ' ').slice(0, 16)}
                      </span>
                    </div>
                  </Tooltip>
                ))}
              </div>
            ) : (
              !historyLoading && (
                <div className={styles.historyEmpty}>暂无诊断记录</div>
              )
            )}
          </div>
        </div>
      </div>
    </div>
  );
};

export default ResumeDiagnosisPage;
