import React, { useState, useEffect, useRef } from 'react';
import { Upload, Progress, Steps, Button, Tag, Alert, Modal, Spin, message } from 'antd';
import {
  CloudUploadOutlined,
  FileTextOutlined,
  CheckCircleOutlined,
  EditOutlined,
  DeleteOutlined,
  StarOutlined,
  BulbOutlined,
} from '@ant-design/icons';
import type { UploadProps } from 'antd';
import { useNavigate } from 'umi';
import dayjs from 'dayjs';
import { ROUTES } from '@/constants/routes';
import { tokenManager } from '@/utils/token';
import {
  getResumeList,
  getResumeDetail,
  uploadResume,
  deleteResume,
  setDefaultResume,
  getParseStreamUrl,
  getParseTaskStatus,
  PENDING_PARSE_PREFIX,
} from '@/services/resume';
import { resetParseNotified } from '@/utils/parseNotification';
import type {
  CardStructure,
  ParseStreamEventType,
  ParseStreamProgressEvent,
  ResumeListItem,
} from '@/services/resume';
import styles from './index.less';

const { Dragger } = Upload;

/** 后端简历数量上限（对齐上传接口 5 份限制） */
const MAX_RESUMES = 5;

/** 解析任务轮询间隔（毫秒，对齐诊断页轮询节奏） */
const PARSE_POLL_INTERVAL_MS = 2500;

/** 解析阶段（与 Steps 下标对齐） */
const parseSteps = [
  { title: '文件上传完成', description: '简历文件已接收' },
  { title: '文档解析中', description: '识别文档结构和文字' },
  { title: '信息提取', description: '提取教育、工作、技能等信息' },
  { title: '结构化完成', description: '简历信息格式化处理完毕' },
];

/**
 * 流程阶段：列表 → 上传中 → 解析中 → 卡片就绪（评分后台生成）→ 完成/失败
 * 说明：后端两阶段解析——阶段1 只产出 card_structure（SSE:final 即卡片就绪），
 * 阶段2 后台产出能力模型/简历MD（SSE 流关闭才算全部完成）。
 */
type FlowPhase = 'list' | 'uploading' | 'parsing' | 'cardReady' | 'done' | 'error';

/** 解析状态展示映射 */
const PARSE_STATUS_MAP: Record<string, { text: string; color: string }> = {
  PENDING: { text: '待解析', color: 'default' },
  PARSING: { text: '解析中', color: 'processing' },
  COMPLETED: { text: '已解析', color: 'success' },
  FAILED: { text: '解析失败', color: 'error' },
};

const ResumeUploadPage: React.FC = () => {
  const navigate = useNavigate();
  const isMountedRef = useRef(true);

  // 简历列表
  const [resumeList, setResumeList] = useState<ResumeListItem[]>([]);
  const [listLoading, setListLoading] = useState(true);
  const [deletingId, setDeletingId] = useState<string | null>(null);

  // 上传流程（phaseRef 同步阶段值，供 SSE 流闭包内读取最新阶段）
  const [phase, setPhaseState] = useState<FlowPhase>('list');
  const phaseRef = useRef<FlowPhase>('list');
  const setPhase = (p: FlowPhase) => {
    phaseRef.current = p;
    setPhaseState(p);
  };
  const [uploadProgress, setUploadProgress] = useState(0);
  const [parseStep, setParseStep] = useState(0);
  // resumeId 为字符串：后端雪花 ID（19 位）序列化为字符串，防 JS 精度丢失
  const [resumeId, setResumeId] = useState<string | null>(null);
  /** resumeId 的 ref 副本（断连恢复闭包用——state 在异步闭包里读不到最新值） */
  const resumeIdRef = useRef<string | null>(null);
  const [parsedCard, setParsedCard] = useState<CardStructure | null>(null);
  const [errorMsg, setErrorMsg] = useState('');
  /** Agent 实时进度文本（thinking 事件内容） */
  const [agentHint, setAgentHint] = useState('');
  /** 章节级实时进度（progress 事件：已解析章节骨架） */
  const [progressInfo, setProgressInfo] = useState<{
    done: number;
    titles: string[];
  }>({ done: 0, titles: [] });
  // progress 逐条动画展示：后端一次性到达，前端控制节奏逐张"生长"
  const progressInfoRef = useRef({ done: 0, titles: [] as string[] });
  const pendingTitlesRef = useRef<string[]>([]);
  const growTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  /** final 是否已到达（动画播完后据此自动切 cardReady） */
  const parsedCardRef = useRef<CardStructure | null>(null);

  // ==================== 简历列表 ====================

  /** 兜底：已尝试过自动订阅的简历 ID（防止重复订阅死循环） */
  const autoParsedRef = useRef<Set<string>>(new Set());

  /** 轮询定时器（断连后兜底感知后台解析完成） */
  const pollTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);
  /** 正在轮询的简历 ID 集合（防重复轮询；多份 PENDING 可并行轮询） */
  const pollingIdsRef = useRef<Set<string>>(new Set());

  /** 注册活跃解析任务（通知机制：CandidateLayout 扫描该 key → 完成/失败弹桌面通知） */
  const registerParsePending = (rid: string) => {
    resetParseNotified(rid);
    try {
      window.localStorage.setItem(`${PENDING_PARSE_PREFIX}${rid}`, '1');
    } catch {
      // localStorage 不可用：跳过跨页面通知，页内轮询兜底
    }
  };

  /** 加载简历列表 */
  const fetchList = async () => {
    setListLoading(true);
    try {
      const data = await getResumeList({ page: 1, pageSize: 20 });
      if (isMountedRef.current) {
        setResumeList(data.list ?? []);
        const list = data.list ?? [];
        // 通知注册：PENDING/PARSING 简历都写入活跃 key（幂等；本页 mount 清残留后恢复，
        // 保证解析完成/失败能弹桌面通知——不能只依赖 PENDING 订阅路径：PARSING 简历不订阅）
        list.forEach((item) => {
          if (item.parseStatus === 'PENDING' || item.parseStatus === 'PARSING') {
            registerParsePending(item.id);
          }
        });
        // 兜底：自动订阅 PENDING 简历触发后端解析（上传后未及时订阅的场景）
        const pendings = list.filter(
          (item) => item.parseStatus === 'PENDING' && !autoParsedRef.current.has(item.id),
        );
        if (pendings.length > 0) {
          pendings.forEach((item) => autoParsedRef.current.add(item.id));
          // 逐个静默订阅（触发解析），完成后刷新列表；
          // 断连不判死——后台解析继续（后端断连不取消），转轮询感知完成
          Promise.all(
            pendings.map((item) =>
              streamParse(item.id, true).catch(() => {
                startPolling(item.id);
              }),
            ),
          ).then(() => {
            if (isMountedRef.current) {
              fetchList();
            }
          });
        }
      }
    } catch {
      // 错误已由拦截器统一提示
    } finally {
      if (isMountedRef.current) {
        setListLoading(false);
      }
    }
  };

  useEffect(() => {
    isMountedRef.current = true;
    // 清理残留活跃解析 key：用户已在列表页，桌面通知无意义；
    // 列表里仍在 PENDING 的简历由下方 silent 订阅重新注册（fetchList 内）
    try {
      for (let i = 0; i < window.localStorage.length; i++) {
        const key = window.localStorage.key(i);
        if (key?.startsWith(PENDING_PARSE_PREFIX)) {
          window.localStorage.removeItem(key);
        }
      }
    } catch {
      // 忽略
    }
    fetchList();
    return () => {
      isMountedRef.current = false;
      // 卸载清理：停轮询（后台解析不受影响，回页后列表加载会重新感知）
      if (pollTimerRef.current) {
        clearInterval(pollTimerRef.current);
        pollTimerRef.current = null;
      }
    };
  }, []);

  /** 删除简历（二次确认，释放名额） */
  const handleDelete = (item: ResumeListItem) => {
    Modal.confirm({
      title: '确认删除这份简历？',
      content: `「${item.fileName}」删除后不可恢复`,
      okText: '删除',
      okButtonProps: { danger: true },
      cancelText: '取消',
      onOk: async () => {
        setDeletingId(item.id);
        try {
          await deleteResume(item.id);
          message.success('简历已删除');
          fetchList();
        } catch {
          // 错误已由拦截器统一提示
        } finally {
          if (isMountedRef.current) {
            setDeletingId(null);
          }
        }
      },
    });
  };

  /** 设为默认简历 */
  const handleSetDefault = async (item: ResumeListItem) => {
    try {
      await setDefaultResume(item.id);
      message.success('已设为默认简历');
      fetchList();
    } catch {
      // 错误已由拦截器统一提示
    }
  };

  // ==================== 轮询兜底（断连后感知后台解析完成） ====================

  /** 停止对某简历的轮询（无剩余轮询目标时清定时器） */
  const stopPolling = (rid: string) => {
    pollingIdsRef.current.delete(rid);
    if (pollTimerRef.current && pollingIdsRef.current.size === 0) {
      clearInterval(pollTimerRef.current);
      pollTimerRef.current = null;
    }
  };

  /**
   * 断连/跳页兜底：轮询解析任务状态（GET /parse-status），感知后台完成/失败。
   *
   * <p>后端断连不取消解析（落库照常），SSE 断流 ≠ 解析失败；
   * 轮询到终态（COMPLETED/FAILED/NONE）即停，刷新列表以 DB 状态为准展示。
   */
  const startPolling = (rid: string) => {
    if (pollingIdsRef.current.has(rid)) {
      return; // 已在轮询，不重复启动
    }
    pollingIdsRef.current.add(rid);
    if (!pollTimerRef.current) {
      pollTimerRef.current = setInterval(async () => {
        // 快照：轮询集合可能在 await 期间变化（stopPolling 增删）
        for (const id of Array.from(pollingIdsRef.current)) {
          try {
            const status = await getParseTaskStatus(id);
            if (status.state === 'RUNNING') {
              continue; // 后台解析中，继续轮询
            }
            stopPolling(id);
            if (status.state === 'COMPLETED') {
              // 主流程断连恢复：直接拉详情展示卡片（动画已断，跳过生长直接就绪）
              if (
                isMountedRef.current &&
                id === resumeIdRef.current &&
                (phaseRef.current === 'parsing' || phaseRef.current === 'cardReady')
              ) {
                try {
                  const detail = await getResumeDetail(id);
                  parsedCardRef.current = detail.cardStructure ?? null;
                  setParsedCard(detail.cardStructure ?? null);
                  setAgentHint('');
                  setPhase('done');
                } catch {
                  // 详情拉取失败不阻塞：列表刷新兜底展示
                }
              }
              fetchList();
            } else if (status.state === 'FAILED') {
              // 主流程失败：页面转错误态；silent 场景：列表红标 + 提示
              if (id === resumeIdRef.current && phaseRef.current !== 'list') {
                setErrorMsg('简历解析失败');
                setPhase('error');
              } else {
                message.error('简历解析失败，可在列表删除后重新上传');
              }
              fetchList();
            } else {
              // NONE：任务状态已过期/未触发，以 DB 状态兜底（列表刷新反映真实状态）
              fetchList();
            }
          } catch {
            // 网络异常：下个间隔重试
          }
        }
      }, PARSE_POLL_INTERVAL_MS);
    }
  };

  // ==================== SSE 流式解析（页内实现：fetch 携带 Token + 命名事件） ====================

  /** 生长动画播完（或直接无待播）后，进入卡片就绪阶段（展示完整内容） */
  const finishCardReady = (allTitles: string[]) => {
    if (phaseRef.current !== 'parsing') {
      return; // 已切换（如超时/错误路径），避免重复
    }
    const next = { done: allTitles.length, titles: allTitles };
    progressInfoRef.current = next;
    setProgressInfo(next);
    setAgentHint('');
    setPhase('cardReady');
  };

  /** 解析单个 SSE 数据块（event:/data: 行） */
  const handleSSEBlock = (block: string, silent = false) => {
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
    handleParseEvent(eventName as ParseStreamEventType, payload, silent);
  };

  /** 按事件类型驱动 UI（silent=true：兜底订阅场景，不驱动页面状态） */
  const handleParseEvent = (
    eventName: ParseStreamEventType,
    payload: unknown,
    silent = false,
  ) => {
    if (silent) {
      return; // 兜底订阅只消费流（触发后端解析），不改变页面阶段
    }
    switch (eventName) {
      case 'thinking': {
        // 阶段1：步骤推进；阶段2（卡片就绪后）：仅更新进度文本
        const t = payload as { content?: string };
        setAgentHint(t?.content ?? '');
        if (phaseRef.current === 'parsing') {
          setParseStep(1);
        }
        break;
      }
      case 'progress': {
        // 后端一次性到达（百宝箱缓冲生成后整体返回）→ 前端逐条"生长"展示
        // 注意：后端每次推送的是累积全量 titles，去重必须基于"已显示 + 已排队"的并集，
        // 否则动画显示慢于事件到达时，排队中的标题会被重复入队（循环重复 bug）
        const p = payload as ParseStreamProgressEvent;
        const incoming = p.titles ?? [];
        const known = new Set([
          ...progressInfoRef.current.titles,
          ...pendingTitlesRef.current,
        ]);
        const fresh = incoming.filter((t) => !known.has(t));
        if (fresh.length > 0) {
          pendingTitlesRef.current.push(...fresh);
          startGrowTimer();
        }
        // Steps 阶段推进（以到达的章节数为准）
        if (phaseRef.current === 'parsing') {
          const done = p.sections_done ?? progressInfoRef.current.done;
          if (done >= 2) {
            setParseStep(3);
          } else if (done >= 1) {
            setParseStep(2);
          }
        }
        break;
      }
      case 'tool_call':
        setParseStep(2);
        break;
      case 'tool_result':
        setParseStep(3);
        break;
      case 'final': {
        // 两阶段解析：final = 卡片就绪（评分在后台生成）
        // 不打断生长动画：把 final 的完整标题补入队列；只要动画队列未播完就不切 cardReady，
        // 等 timer 播完（耗尽时检测 parsedCardRef）自动衔接——保证"逐条长出"体验完整
        const final = payload as { parse_status: string; card_structure?: CardStructure };
        setParseStep(4);
        setParsedCard(final.card_structure ?? null);
        parsedCardRef.current = final.card_structure ?? null;
        setAgentHint('正在生成能力评估与评分…');
        const allTitles = (final.card_structure?.sections ?? []).map((s) => s.title);
        const known = new Set([
          ...progressInfoRef.current.titles,
          ...pendingTitlesRef.current,
        ]);
        const fresh = allTitles.filter((t) => !known.has(t));
        if (fresh.length > 0) {
          pendingTitlesRef.current.push(...fresh);
        }
        if (pendingTitlesRef.current.length > 0) {
          // 动画队列未播完（chunk progress 可能刚入队）→ 确保 timer 在跑，播完自动衔接
          startGrowTimer();
        } else {
          // 没有待播标题（动画已播完或本来就无骨架）→ 直接进入卡片就绪
          finishCardReady(allTitles);
        }
        break;
      }
      case 'error': {
        const err = payload as { code?: number; message?: string };
        setErrorMsg(err?.message || '简历解析失败');
        setPhase('error');
        break;
      }
      default:
        break;
    }
  };

  /**
   * 章节生长定时器：每 400ms 从待展示队列取一个标题加入清单（前端动画化，
   * 弥补后端一次性到达导致的"无流式感"）。
   * 队列耗尽时若 final 已到达（parsedCardRef 有值）→ 自动衔接切卡片就绪。
   */
  const startGrowTimer = () => {
    if (growTimerRef.current) {
      return;
    }
    growTimerRef.current = setInterval(() => {
      const title = pendingTitlesRef.current.shift();
      if (title === undefined) {
        // 队列耗尽：清定时器，若 final 已到达则自动进入卡片就绪
        if (growTimerRef.current) {
          clearInterval(growTimerRef.current);
          growTimerRef.current = null;
        }
        if (parsedCardRef.current) {
          const allTitles = (parsedCardRef.current.sections ?? []).map((s) => s.title);
          finishCardReady(allTitles);
        }
        return;
      }
      setProgressInfo((prev) => {
        const next = { done: prev.done + 1, titles: [...prev.titles, title] };
        progressInfoRef.current = next;
        return next;
      });
    }, 400);
  };

  /**
   * 建立 SSE 连接（fetch 流式读取，可携带 Authorization 头）
   *
   * @param rid    简历 ID（字符串）
   * @param silent 兜底订阅模式（不驱动页面状态，仅消费流以触发后端解析）
   */
  const streamParse = async (rid: string, silent = false) => {
    const token = tokenManager.getToken();
    const res = await fetch(getParseStreamUrl(rid), {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
    });
    if (!res.ok || !res.body) {
      throw new Error(`解析连接失败（HTTP ${res.status}）`);
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
        handleSSEBlock(block, silent);
      }
    }
    if (silent) {
      return; // 兜底订阅：流结束即结束，状态由列表刷新反映
    }
    // 流关闭 = 后端两阶段解析全部结束（final 后阶段2 完成才 complete）
    if (phaseRef.current === 'cardReady') {
      setAgentHint('');
      setPhase('done');
    } else if (phaseRef.current === 'parsing') {
      // 流在 final 前关闭（连接中断）：后端断连不取消，解析仍在后台跑——
      // 转轮询感知完成，不判死为错误
      setErrorMsg('解析连接已断开，后台继续解析中…');
      if (resumeIdRef.current) {
        startPolling(resumeIdRef.current);
      }
    }
  };

  // ==================== 上传流程 ====================

  const doUpload = async (file: File) => {
    setErrorMsg('');
    setParseStep(0);
    setAgentHint('');
    // 重置章节生长状态（清定时器 + 队列 + 已展示列表 + final 标记）
    if (growTimerRef.current) {
      clearInterval(growTimerRef.current);
      growTimerRef.current = null;
    }
    parsedCardRef.current = null;
    pendingTitlesRef.current = [];
    const emptyProgress = { done: 0, titles: [] as string[] };
    progressInfoRef.current = emptyProgress;
    setProgressInfo(emptyProgress);
    setPhase('uploading');
    setUploadProgress(0);
    try {
      const formData = new FormData();
      formData.append('file', file);
      const result = await uploadResume(formData, (percent) => {
        setUploadProgress(percent);
      });
      setResumeId(result.resumeId);
      resumeIdRef.current = result.resumeId;
      // 注册活跃解析任务：切走页面后由 CandidateLayout 扫描 → 完成弹桌面通知
      registerParsePending(result.resumeId);
      setUploadProgress(100);
      setPhase('parsing');
      await streamParse(result.resumeId);
    } catch (err) {
      // 业务错误（无 resumeId）已由拦截器提示；
      // 流式连接错误（有 resumeId）：断连不判死——后台解析继续，转轮询感知完成
      const rid = resumeIdRef.current;
      if (rid) {
        setErrorMsg('解析连接已断开，后台继续解析中…');
        startPolling(rid);
      } else {
        setErrorMsg(err instanceof Error ? err.message : '上传失败，请重试');
        setPhase('error');
      }
    }
  };

  const uploadProps: UploadProps = {
    name: 'file',
    multiple: false,
    accept: '.pdf,.doc,.docx',
    showUploadList: false,
    beforeUpload: (file) => {
      const isValidType =
        file.type === 'application/pdf' ||
        file.type === 'application/msword' ||
        file.type ===
          'application/vnd.openxmlformats-officedocument.wordprocessingml.document';
      const isLt10M = file.size / 1024 / 1024 < 10;
      if (!isValidType) {
        message.error('仅支持 PDF、DOC、DOCX 格式文件');
        return false;
      }
      if (!isLt10M) {
        message.error('文件大小不能超过 10MB');
        return false;
      }
      if (resumeList.length >= MAX_RESUMES) {
        message.error(`已达 ${MAX_RESUMES} 份上限，请先删除旧简历`);
        return false;
      }
      // 阻止默认上传，改走真实上传流程
      doUpload(file);
      return false;
    },
  };

  /** 回到列表（上传完成后刷新） */
  const backToList = () => {
    setPhase('list');
    setResumeId(null);
    resumeIdRef.current = null;
    setParsedCard(null);
    setErrorMsg('');
    fetchList();
  };

  // ==================== 渲染 ====================

  if (phase === 'list') {
    const isFull = resumeList.length >= MAX_RESUMES;
    return (
      <div className={styles.page}>
        {/* 简历列表 */}
        <div className={styles.listHeader}>
          <div className={styles.listTitle}>我的简历（{resumeList.length}/{MAX_RESUMES}）</div>
          <Tag color={isFull ? 'warning' : 'success'}>
            {isFull ? '已达上限' : '还可上传'}
          </Tag>
        </div>

        {listLoading ? (
          <div className={styles.pageCenter}>
            <Spin size="large" />
          </div>
        ) : resumeList.length > 0 ? (
          <div className={styles.resumeList}>
            {resumeList.map((item) => {
              const status = PARSE_STATUS_MAP[item.parseStatus] ?? {
                text: item.parseStatus,
                color: 'default',
              };
              return (
                <div key={item.id} className={styles.resumeItem}>
                  <div className={styles.resumeItemInfo}>
                    <div className={styles.resumeItemName}>
                      <FileTextOutlined /> {item.fileName}
                      {item.isDefault === 1 && (
                        <span className={styles.defaultBadge}>
                          <StarOutlined /> 默认
                        </span>
                      )}
                    </div>
                    <div className={styles.resumeItemMeta}>
                      <Tag color={status.color}>{status.text}</Tag>
                      <span>{dayjs(item.createdAt).format('YYYY-MM-DD HH:mm')}</span>
                    </div>
                  </div>
                  <div className={styles.resumeItemActions}>
                    <Button
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() =>
                        navigate(`${ROUTES.CANDIDATE_RESUME_PREVIEW}/${item.id}/preview`)
                      }
                    >
                      编辑
                    </Button>
                    <Button
                      size="small"
                      icon={<BulbOutlined />}
                      onClick={() =>
                        navigate(`${ROUTES.CANDIDATE_RESUME_DIAGNOSIS}/${item.id}/diagnosis`)
                      }
                    >
                      诊断
                    </Button>
                    {item.isDefault !== 1 && (
                      <Button
                        size="small"
                        icon={<StarOutlined />}
                        onClick={() => handleSetDefault(item)}
                      >
                        设为默认
                      </Button>
                    )}
                    <Button
                      size="small"
                      danger
                      icon={<DeleteOutlined />}
                      loading={deletingId === item.id}
                      onClick={() => handleDelete(item)}
                    >
                      删除
                    </Button>
                  </div>
                </div>
              );
            })}
          </div>
        ) : (
          <div className={styles.pageCenter}>
            <div className={styles.emptyState}>
              <div className={styles.emptyIcon}>📄</div>
              <div className={styles.emptyTitle}>还没有简历</div>
              <div className={styles.emptyDesc}>上传一份简历，让 AI 帮你解析和优化</div>
            </div>
          </div>
        )}

        {/* 上传区 */}
        {isFull ? (
          <Alert
            type="warning"
            showIcon
            message={`已达 ${MAX_RESUMES} 份简历上限`}
            description="请先删除旧简历后再上传新简历"
          />
        ) : (
          <Dragger {...uploadProps} className={styles.uploadZone}>
            <div className={styles.uploadIcon}>
              <CloudUploadOutlined />
            </div>
            <div className={styles.uploadTitle}>点击或拖拽上传简历</div>
            <div className={styles.uploadHint}>
              支持 PDF、DOC、DOCX 格式，文件大小不超过 10MB
            </div>
          </Dragger>
        )}
      </div>
    );
  }

  return (
    <div className={styles.page}>
      {(phase === 'uploading' || phase === 'parsing') && (
        <>
          <div className={styles.progressCard}>
            <div className={styles.progressTitle}>
              <FileTextOutlined /> 正在上传和解析简历...
            </div>
            {phase === 'uploading' ? (
              <Progress
                percent={uploadProgress}
                status="active"
                strokeColor="var(--accent)"
              />
            ) : (
              // 解析阶段：单个转圈指示（避免多个 Spin 叠加），进度由 Steps/骨架体现
              <div className={styles.parsingIndicator}>
                <Spin size="small" />
                <span>正在解析简历内容，请稍候…</span>
              </div>
            )}
          </div>

          <div className={styles.progressCard}>
            <div className={styles.progressTitle}>解析进度</div>
            {/* 左：Steps 整体阶段指示；右：章节生长清单（progress 驱动的流式展示） */}
            <div className={styles.parseProgressBody}>
              <div className={styles.parseStepsCol}>
                <Steps current={parseStep} items={parseSteps} direction="vertical" />
              </div>
              <div className={styles.parseSectionsCol}>
                {agentHint && (
                  <div className={styles.agentHint}>AI 正在：{agentHint}</div>
                )}
                {progressInfo.done > 0 ? (
                  <>
                    <div className={styles.progressSectionsTitle}>
                      已解析
                      <span className={styles.progressSectionsCount}>
                        {progressInfo.done}
                      </span>
                      个章节
                      {progressInfo.titles.length > 0 &&
                        `：${progressInfo.titles.join('、')}`}
                    </div>
                    <div className={styles.progressSectionList}>
                      {progressInfo.titles.map((title, i) => (
                        <div key={i} className={styles.progressSectionItem}>
                          <div className={styles.progressSectionHead}>
                            <div className={styles.progressSectionTitle}>
                              <span className={styles.progressSectionIcon}>
                                {String(i + 1).padStart(2, '0')}
                              </span>
                              {title}
                            </div>
                            <span className={styles.progressSectionTag}>生成中</span>
                          </div>
                          {/* 内容占位条（shimmer 动画）：final 后替换为真实内容 */}
                          <div
                            className={styles.progressSectionBar}
                            style={{ width: `${88 - (i % 3) * 10}%` }}
                          />
                          <div
                            className={styles.progressSectionBar}
                            style={{ width: `${62 - (i % 2) * 14}%` }}
                          />
                        </div>
                      ))}
                    </div>
                  </>
                ) : (
                  <div className={styles.parseSectionsEmpty}>正在生成章节结构…</div>
                )}
              </div>
            </div>
          </div>
        </>
      )}

      {phase === 'error' && (
        <div className={styles.progressCard}>
          <Alert type="error" showIcon message="操作失败" description={errorMsg} />
          <Button
            type="primary"
            size="large"
            onClick={backToList}
            style={{ marginTop: 16 }}
          >
            返回简历列表
          </Button>
        </div>
      )}

      {(phase === 'cardReady' || phase === 'done') && (
        <div className={styles.previewCard}>
          <div className={styles.previewHeader}>
            <div className={styles.previewTitle}>解析结果预览</div>
            {phase === 'done' ? (
              <Tag icon={<CheckCircleOutlined />} color="success">
                解析完成
              </Tag>
            ) : (
              <Tag icon={<CheckCircleOutlined />} color="processing">
                卡片就绪 · 评分生成中
              </Tag>
            )}
          </div>

          {parsedCard?.sections && parsedCard.sections.length > 0 ? (
            parsedCard.sections.map((section, sectionIdx) => (
              <div key={sectionIdx} className={styles.previewSection}>
                <div className={styles.previewSectionTitle}>{section.title}</div>
                <div className={styles.previewContent}>
                  {section.points.length > 0 ? (
                    section.points.map((point, pointIdx) => (
                      <div key={point.id || pointIdx} className={styles.previewPoint}>
                        {point.text}
                      </div>
                    ))
                  ) : (
                    <div>{section.raw_text || '—'}</div>
                  )}
                </div>
              </div>
            ))
          ) : (
            <div className={styles.previewContent}>暂无可预览内容</div>
          )}

          {/* 阶段2：能力评估后台生成中（不阻塞，卡片已可编辑） */}
          {phase === 'cardReady' && (
            <div className={styles.scoreLoading}>
              <Spin size="small" />
              <span>{agentHint || '正在生成能力评估与评分…'}</span>
            </div>
          )}

          <div style={{ display: 'flex', gap: 12, marginTop: 24 }}>
            <Button
              type="primary"
              size="large"
              onClick={() =>
                navigate(`${ROUTES.CANDIDATE_RESUME_PREVIEW}/${resumeId}/preview`)
              }
              style={{
                height: 44,
                borderRadius: 'var(--radius-md)',
                fontWeight: 650,
                fontFamily: 'var(--font-title)',
              }}
            >
              进入简历编辑
            </Button>
            <Button
              size="large"
              onClick={backToList}
              style={{
                height: 44,
                borderRadius: 'var(--radius-md)',
                fontWeight: 650,
                fontFamily: 'var(--font-title)',
              }}
            >
              {phase === 'cardReady' ? '稍后查看' : '返回简历列表'}
            </Button>
          </div>
        </div>
      )}
    </div>
  );
};

export default ResumeUploadPage;
