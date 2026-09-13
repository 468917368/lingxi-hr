/**
 * AI求职助手页面
 *
 * 功能：
 * 1. 与AI进行实时对话（SSE流式输出）
 * 2. 多会话管理（创建、切换、删除）
 * 3. 思考步骤展示（显示AI思考过程）
 * 4. LLM深度思考展示（可折叠）
 * 5. 快捷操作和快捷问题
 *
 * 技术栈：
 * - React + TypeScript
 * - Ant Design UI组件
 * - SSE (Server-Sent Events) 流式请求
 * - localStorage 持久化会话ID
 */
import React, { useState, useRef, useEffect, useLayoutEffect, useCallback, useMemo } from 'react';
import { Input, Button, Spin, message } from 'antd';
import {
  RobotOutlined,    // AI机器人图标
  SendOutlined,     // 发送按钮图标
  UserOutlined,     // 用户头像图标
  PlusOutlined,     // 新建会话图标
  DeleteOutlined,   // 删除会话图标
  LoadingOutlined,  // 加载中图标
  CopyOutlined,     // 复制按钮图标
  CheckOutlined,    // 复制成功图标
} from '@ant-design/icons';
import { useSSE } from '@/hooks/useSSE';  // SSE流式请求Hook
import MarkdownRenderer from '@/components/MarkdownRenderer';  // Markdown渲染组件
import { createSession, deleteSession, getSessions, getSessionHistory, buildChatSSEUrl, stopChat } from '@/services/agent';
import type { AiSession } from '@/services/agent';
import { cachedRequest } from '@/utils/requestCache';  // 请求缓存工具
import styles from './index.less';

/**
 * 聊天消息接口
 * 定义了消息的数据结构
 */
interface ChatMessage {
  id: number;                    // 消息唯一ID（使用时间戳）
  role: 'user' | 'assistant';   // 角色：用户 或 AI助手
  content: string;               // 消息正文内容
  thinkingSteps?: string[];      // AI思考步骤列表（显示进度）
  llmThinking?: string;          // LLM深度思考内容（可折叠显示）
  isStreaming?: boolean;         // 是否正在流式输出中
}

/**
 * 欢迎消息
 * 页面加载时显示的默认消息
 */
const WELCOME_MSG: ChatMessage = {
  id: 0,
  role: 'assistant',
  content: '你好！我是灵犀互聘的AI求职助手，可以帮你：\n\n- 🔍 **推荐岗位** - 根据你的简历智能推荐匹配岗位\n- 🏢 **查询公司** - 了解公司信息和在招岗位\n- 📊 **匹配分析** - 分析你的简历与岗位的匹配度\n\n有什么想了解的，随时问我！',
};

const AIAssistantPage: React.FC = () => {
  // ==================== 状态定义 ====================

  /** 聊天消息列表，初始包含欢迎消息 */
  const [messages, setMessages] = useState<ChatMessage[]>([WELCOME_MSG]);

  /** 输入框的值 */
  const [inputValue, setInputValue] = useState('');

  /**
   * 当前会话ID
   * 从localStorage恢复，实现页面刷新后继续对话
   */
  const [sessionId, setSessionId] = useState<string | null>(() => {
    try { return localStorage.getItem('ai_sessionId'); } catch { return null; }
  });

  /** 会话列表（侧边栏显示） */
  const [sessions, setSessions] = useState<AiSession[]>([]);

  /** 会话列表加载状态 */
  const [loadingSessions, setLoadingSessions] = useState(false);

  /** 页面初始化状态（加载会话列表+恢复历史） */
  const [initializing, setInitializing] = useState(true);

  /** 当前复制成功的消息ID（用于显示✓图标） */
  const [copiedId, setCopiedId] = useState<number | null>(null);

  // ==================== Refs ====================

  /** 消息列表底部DOM引用（用于自动滚动） */
  const messagesEndRef = useRef<HTMLDivElement>(null);

  /** 消息ID计数器（每条消息+1） */
  const msgIdRef = useRef(1);

  /** AI生成计时器（显示"生成中...Xs"） */
  const [generatingSeconds, setGeneratingSeconds] = useState(0);
  const generatingTimerRef = useRef<ReturnType<typeof setInterval> | null>(null);

  /**
   * 当前流式消息的ID
   * 用ref缓存，避免每次chunk都遍历全部消息查找
   */
  const streamingMsgIdRef = useRef<number | null>(null);

  /** 滚动节流定时器（避免频繁滚动） */
  const scrollTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);

  // ==================== 滚动控制 ====================

  /**
   * 滚动到底部（带节流）
   * @param immediate 是否立即滚动（无动画）
   *
   * 节流原理：
   * - 每次调用先清除之前的定时器
   * - 延迟100ms后执行滚动
   * - 避免流式输出时频繁滚动导致卡顿
   */
  const scrollToBottom = useCallback((immediate = false) => {
    if (scrollTimerRef.current) clearTimeout(scrollTimerRef.current);
    if (immediate) {
      // 立即滚动（无动画），用于首次加载
      messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
      return;
    }
    // 延迟100ms平滑滚动
    scrollTimerRef.current = setTimeout(() => {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, 100);
  }, []);

  /**
   * 首次加载消息时，用 useLayoutEffect 在浏览器绘制前定位到底部
   * 这样用户看不到滚动过程，体验更好
   */
  const prevMessagesLenRef = useRef(0);
  useLayoutEffect(() => {
    // 首次加载消息时，直接定位到底部（无动画）
    if (messages.length > 0 && prevMessagesLenRef.current === 0) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'auto' });
    }
    prevMessagesLenRef.current = messages.length;
  }, [messages]);

  /**
   * 新消息到来时，平滑滚动到底部
   * 依赖 messages.length，只有消息数量变化才触发
   */
  useEffect(() => {
    if (messages.length > 1) {
      messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }
  }, [messages.length]);

  // ==================== 数据加载 ====================

  /**
   * 加载会话列表（清除缓存后重新加载）
   * 用于：新建会话、删除会话、流式完成后刷新
   */
  const fetchSessions = useCallback(async () => {
    try {
      // 动态导入 + 清除缓存，确保获取最新数据
      const { requestCache } = await import('@/utils/requestCache');
      requestCache.delete('ai-sessions');

      const data = await getSessions();
      setSessions(data.list || []);
    } catch (error) {
      console.error('加载会话列表失败:', error);
    }
  }, []);

  /**
   * 加载指定会话的历史消息
   * @param sid 会话ID
   *
   * 流程：
   * 1. 设置当前会话ID
   * 2. 调用API获取历史消息
   * 3. 转换为ChatMessage格式
   * 4. 更新消息列表
   */
  const loadSession = useCallback(async (sid: string) => {
    setSessionId(sid);
    try {
      const history = await getSessionHistory(sid);
      if (history && history.length > 0) {
        // 将后端返回的历史消息转换为前端格式
        const loaded: ChatMessage[] = history.map((item, idx) => ({
          id: idx + 1,
          role: item.role === 'user' ? ('user' as const) : ('assistant' as const),
          content: item.content,
        }));
        setMessages(loaded);
        msgIdRef.current = history.length + 1;  // 更新消息ID计数器
      } else {
        // 没有历史消息，显示欢迎页
        setMessages([WELCOME_MSG]);
        msgIdRef.current = 1;
      }
    } catch (error) {
      console.error('加载历史失败:', error);
      setMessages([WELCOME_MSG]);
    }
  }, []);

  // ==================== 初始化 ====================

  /**
   * 页面初始化
   * 加载会话列表 + 恢复上次会话
   *
   * 优先级：
   * 1. 有保存的会话ID且存在于列表中 → 恢复该会话
   * 2. 没有保存的会话但有历史会话 → 使用最近的会话
   * 3. 完全没有会话 → 显示欢迎页（不创建会话）
   */
  useEffect(() => {
    const init = async () => {
      try {
        // 使用缓存加载会话列表（30秒内不重复请求）
        const sessionsData = await cachedRequest(
          'ai-sessions',
          () => getSessions(),
          30 * 1000,  // 缓存30秒
        );
        const sessionsList = sessionsData?.list || [];
        setSessions(sessionsList);

        // 从localStorage获取上次保存的会话ID
        const savedId = localStorage.getItem('ai_sessionId');

        // 情况1：有保存的会话且存在于列表中 → 恢复
        if (savedId && sessionsList.some((s) => s.sessionId === savedId)) {
          await loadSession(savedId);
        }
        // 情况2：没有保存的会话但有历史会话 → 使用最近的
        else if (sessionsList.length > 0) {
          const latest = sessionsList[0].sessionId;
          localStorage.setItem('ai_sessionId', latest);
          await loadSession(latest);
        }
        // 情况3：完全没有会话 → 显示欢迎页（不创建会话）
        else {
          setMessages([WELCOME_MSG]);
        }
      } catch (error) {
        console.error('[AI] 初始化失败:', error);
        setMessages([WELCOME_MSG]);
      } finally {
        setInitializing(false);  // 无论成功失败，都结束初始化状态
      }
    };
    init();
  }, []);  // 空依赖数组，只在组件挂载时执行一次

  // ==================== SSE回调配置 ====================

  /**
   * SSE回调配置
   * 处理流式输出的各个阶段
   *
   * SSE事件顺序：onProgress → onChunk* → onDone
   * 特殊情况：onStopped（用户停止）、onError（出错）
   */
  const sseOptions = useMemo(() => ({
    /**
     * 收到思考步骤（如"正在分析简历..."）
     * 显示思考进度，带计时器
     */
    onProgress: (data: { step: string; message: string }) => {
      // 如果是"生成中"步骤，启动计时器
      if (data.step === 'generating') {
        setGeneratingSeconds(0);
        if (generatingTimerRef.current) clearInterval(generatingTimerRef.current);
        generatingTimerRef.current = setInterval(() => {
          setGeneratingSeconds((s) => s + 1);
        }, 1000);
      }

      // 更新消息，添加思考步骤
      setMessages((prev) => {
        // 用ref直接定位流式消息，避免遍历
        const streamingId = streamingMsgIdRef.current;
        const streamingMsg = streamingId ? prev.find((m) => m.id === streamingId) : null;

        if (streamingMsg) {
          // 已有流式消息，追加思考步骤
          streamingMsgIdRef.current = streamingMsg.id;
          return prev.map((m) =>
            m.id === streamingMsg.id
              ? { ...m, thinkingSteps: [...(m.thinkingSteps || []), data.message] }
              : m,
          );
        }

        // 没有流式消息，创建新的
        const newId = Date.now();
        streamingMsgIdRef.current = newId;
        return [...prev, {
          id: newId,
          role: 'assistant' as const,
          content: '',
          thinkingSteps: [data.message],
          isStreaming: true,
        }];
      });
    },

    /**
     * 收到流式文本块
     * 区分LLM思考（[THINKING]前缀）和正文
     */
    onChunk: (data: { content: string }) => {
      // 收到chunk，停止计时器
      if (generatingTimerRef.current) {
        clearInterval(generatingTimerRef.current);
        generatingTimerRef.current = null;
      }

      const text = data.content || '';
      // 判断是否是LLM思考内容（以[THINKING]开头）
      const isLLMThinking = text.startsWith('[THINKING]');
      // 去掉[THINKING]前缀，获取实际内容
      const chunkText = isLLMThinking ? text.substring(10) : text;

      setMessages((prev) => {
        // 用ref直接定位流式消息，避免find遍历
        const streamingId = streamingMsgIdRef.current;

        if (streamingId) {
          // 已有流式消息，追加内容
          return prev.map((m) => {
            if (m.id !== streamingId) return m;
            // LLM思考追加到llmThinking，正文追加到content
            return isLLMThinking
              ? { ...m, llmThinking: (m.llmThinking || '') + chunkText }
              : { ...m, content: m.content + chunkText };
          });
        }

        // 没有流式消息，创建新的
        const newId = Date.now();
        streamingMsgIdRef.current = newId;
        return [...prev, {
          id: newId,
          role: 'assistant' as const,
          content: isLLMThinking ? '' : chunkText,
          llmThinking: isLLMThinking ? chunkText : undefined,
          isStreaming: true,
        }];
      });
    },

    /**
     * 收到完整结果（非流式场景）
     * 删除所有流式消息，添加完整消息
     */
    onResult: (data: { content: string }) => {
      // 停止计时器，清除流式消息ID
      if (generatingTimerRef.current) {
        clearInterval(generatingTimerRef.current);
        generatingTimerRef.current = null;
      }
      streamingMsgIdRef.current = null;

      setMessages((prev) => {
        // 过滤掉所有流式消息（半成品）
        const withoutStreaming = prev.filter((m) => !m.isStreaming);
        // 添加完整消息（成品）
        return [...withoutStreaming, {
          id: Date.now(),
          role: 'assistant' as const,
          content: data.content,
        }];
      });
    },

    /**
     * 流式输出完成
     * 标记消息为非流式，刷新会话列表
     */
    onDone: () => {
      // 停止计时器，清除流式消息ID
      if (generatingTimerRef.current) {
        clearInterval(generatingTimerRef.current);
        generatingTimerRef.current = null;
      }
      streamingMsgIdRef.current = null;

      // 把所有流式消息标记为"已完成"
      setMessages((prev) => prev.map((m) =>
        m.isStreaming ? { ...m, isStreaming: false } : m
      ));

      // 刷新会话列表（更新最后一条消息）
      fetchSessions();
    },

    /**
     * 用户主动停止生成
     * 保留已生成的内容，标记为非流式
     */
    onStopped: (data: { message: string }) => {
      // 停止计时器，清除流式消息ID
      if (generatingTimerRef.current) {
        clearInterval(generatingTimerRef.current);
        generatingTimerRef.current = null;
      }
      streamingMsgIdRef.current = null;

      // 保留已生成的内容，标记为非流式
      setMessages((prev) => prev.map((m) =>
        m.isStreaming ? { ...m, isStreaming: false } : m
      ));

      // 显示提示
      message.info(data.message || '已停止生成');
    },

    /**
     * 发生错误
     * 删除流式消息（半成品），显示错误提示
     */
    onError: (error: { code: number; message: string }) => {
      // 停止计时器，清除流式消息ID
      if (generatingTimerRef.current) {
        clearInterval(generatingTimerRef.current);
        generatingTimerRef.current = null;
      }
      streamingMsgIdRef.current = null;

      // 删除所有流式消息（出错了，半成品没用）
      setMessages((prev) => prev.filter((m) => !m.isStreaming));

      // 显示错误提示
      message.error(error.message || 'AI响应失败');
    },
  }), [fetchSessions]);  // 依赖fetchSessions，确保回调中能调用最新版本

  // ==================== SSE Hook ====================

  /**
   * useSSE Hook
   * loading: 是否正在流式输出
   * start: 启动SSE请求的函数
   */
  const { loading: sseLoading, start: startSSE } = useSSE(sseOptions);

  // ==================== 清理定时器 ====================

  /**
   * 组件卸载时清理所有定时器
   * 防止内存泄漏
   */
  useEffect(() => {
    return () => {
      if (generatingTimerRef.current) clearInterval(generatingTimerRef.current);
      if (scrollTimerRef.current) clearTimeout(scrollTimerRef.current);
    };
  }, []);

  // ==================== 会话管理 ====================

  /**
   * 新建会话
   * 创建新的AI会话，重置消息为欢迎页
   */
  const handleNewSession = useCallback(async () => {
    try {
      const newSid = await createSession();
      setSessionId(newSid);
      localStorage.setItem('ai_sessionId', newSid);
      setMessages([WELCOME_MSG]);  // 重置为欢迎消息
      msgIdRef.current = 1;
      fetchSessions();  // 刷新会话列表
    } catch (error) {
      message.error('创建会话失败');
    }
  }, [fetchSessions]);

  /**
   * 切换会话
   * 加载指定会话的历史消息
   */
  const handleSelectSession = useCallback((sid: string) => {
    if (sid === sessionId) return;  // 点击当前会话，忽略
    localStorage.setItem('ai_sessionId', sid);
    loadSession(sid);
  }, [sessionId, loadSession]);

  /**
   * 删除会话
   * 删除指定会话，如果是当前会话则重置为欢迎页
   */
  const handleDeleteSession = useCallback(async (sid: string) => {
    try {
      await deleteSession(sid);
      // 如果删除的是当前会话，重置状态
      if (sessionId === sid) {
        localStorage.removeItem('ai_sessionId');
        setSessionId(null);
        setMessages([WELCOME_MSG]);
      }
      fetchSessions();  // 刷新会话列表
    } catch (error) {
      message.error('删除会话失败');
    }
  }, [sessionId, fetchSessions]);

  // ==================== 消息操作 ====================

  /**
   * 发送消息
   * 流程：检查输入 → 确保有会话 → 添加用户消息 → 发送SSE请求
   */
  const handleSend = useCallback(async () => {
    const msg = inputValue.trim();
    // 消息为空或正在生成中，忽略
    if (!msg || sseLoading) return;

    let currentSid = sessionId;

    // 没有会话则先创建
    if (!currentSid) {
      try {
        currentSid = await createSession();
        setSessionId(currentSid);
        localStorage.setItem('ai_sessionId', currentSid);
        fetchSessions();
      } catch (error) {
        message.error('创建会话失败');
        return;
      }
    }

    // 添加用户消息到界面（乐观更新）
    setMessages((prev) => [...prev, { id: msgIdRef.current++, role: 'user', content: msg }]);
    setInputValue('');  // 清空输入框

    // 构建SSE URL并发送请求
    const url = buildChatSSEUrl(currentSid, msg);
    startSSE(url);
  }, [inputValue, sessionId, sseLoading, fetchSessions, startSSE]);

  /**
   * 停止生成
   * 调用后端API停止AI生成
   */
  const handleStop = useCallback(async () => {
    if (!sessionId) return;
    try {
      await stopChat(sessionId);
    } catch (error) {
      console.error('停止失败:', error);
    }
  }, [sessionId]);

  /**
   * 快捷操作
   * 直接发送预设的问题（如"推荐岗位"）
   */
  const handleQuickAction = useCallback(async (action: string) => {
    if (sseLoading) return;

    let currentSid = sessionId;
    // 没有会话则先创建
    if (!currentSid) {
      try {
        currentSid = await createSession();
        setSessionId(currentSid);
        localStorage.setItem('ai_sessionId', currentSid);
        fetchSessions();
      } catch {
        message.error('创建会话失败');
        return;
      }
    }

    // 添加用户消息并发送
    setMessages((prev) => [...prev, { id: msgIdRef.current++, role: 'user', content: action }]);
    setInputValue('');
    const url = buildChatSSEUrl(currentSid, action);
    startSSE(url);
  }, [sessionId, sseLoading, fetchSessions, startSSE]);

  // ==================== 工具函数 ====================

  /**
   * 复制消息内容到剪贴板
   * @param msgId 消息ID（用于显示复制成功图标）
   * @param content 要复制的内容
   *
   * 优先使用Clipboard API，降级使用execCommand
   */
  const handleCopy = useCallback(async (msgId: number, content: string) => {
    try {
      // 优先使用现代Clipboard API
      await navigator.clipboard.writeText(content);
      setCopiedId(msgId);
      setTimeout(() => setCopiedId(null), 2000);  // 2秒后恢复图标
    } catch (error) {
      // 降级方案：使用execCommand
      const textarea = document.createElement('textarea');
      textarea.value = content;
      document.body.appendChild(textarea);
      textarea.select();
      document.execCommand('copy');
      document.body.removeChild(textarea);
      setCopiedId(msgId);
      setTimeout(() => setCopiedId(null), 2000);
    }
  }, []);

  /**
   * 获取会话标题
   * 返回会话的第一条消息作为标题，超过25字符截断
   */
  const getSessionTitle = (session: AiSession) => {
    if (session.lastMessage) {
      return session.lastMessage.length > 25
        ? session.lastMessage.substring(0, 25) + '...'
        : session.lastMessage;
    }
    return '新会话';
  };

  /**
   * 格式化时间
   * 后端存UTC时间，前端+8小时显示北京时间
   *
   * 今天：显示 "HH:mm"
   * 其他：显示 "M月d日"
   */
  const formatTime = (dateStr: string) => {
    if (!dateStr) return '';
    // 补上时区偏移（UTC → 北京时间）
    const d = new Date(dateStr.replace(' ', 'T') + 'Z');
    const now = new Date();
    const isToday = d.toLocaleDateString('zh-CN') === now.toLocaleDateString('zh-CN');
    if (isToday) {
      return d.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' });
    }
    return d.toLocaleDateString('zh-CN', { month: 'numeric', day: 'numeric' });
  };

  // ==================== 渲染 ====================

  /**
   * 初始化加载中
   * 显示全屏加载动画
   */
  if (initializing) {
    return (
      <div className={styles.page} style={{ justifyContent: 'center', alignItems: 'center' }}>
        <Spin size="large" />
      </div>
    );
  }

  /**
   * 主页面结构
   * 左侧：会话列表侧边栏
   * 右侧：聊天区域
   */
  return (
    <div className={styles.page}>
      {/* ========== 侧边栏：会话列表 ========== */}
      <div className={styles.sidebar}>
        {/* 侧边栏头部：标题 + 新建按钮 */}
        <div className={styles.sidebarHeader}>
          <span>历史会话</span>
          <Button type="text" icon={<PlusOutlined />} size="small" onClick={handleNewSession} />
        </div>

        {/* 会话列表 */}
        <div className={styles.sessionList}>
          {loadingSessions ? (
            // 加载中
            <div className={styles.loadingSessions}><Spin size="small" /></div>
          ) : sessions.length === 0 ? (
            // 空状态
            <div className={styles.emptySessions}>暂无会话</div>
          ) : (
            // 渲染会话列表
            sessions.map((session) => (
              <div
                key={session.sessionId}
                className={`${styles.sessionItem} ${sessionId === session.sessionId ? styles.sessionActive : ''}`}
                onClick={() => handleSelectSession(session.sessionId)}
              >
                {/* 会话标题（第一条消息） */}
                <div className={styles.sessionTitle}>{getSessionTitle(session)}</div>
                {/* 会话时间 */}
                <div className={styles.sessionTime}>{formatTime(session.updatedAt)}</div>
                {/* 删除按钮（阻止冒泡，避免触发切换） */}
                <Button
                  type="text" icon={<DeleteOutlined />} size="small"
                  className={styles.sessionDelete}
                  onClick={(e) => { e.stopPropagation(); handleDeleteSession(session.sessionId); }}
                />
              </div>
            ))
          )}
        </div>
      </div>

      {/* ========== 聊天区域 ========== */}
      <div className={styles.chatArea}>
        {/* 头部：AI图标 + 标题 + 描述 */}
        <div className={styles.header}>
          <div className={styles.headerIcon}>
            <RobotOutlined style={{ color: 'var(--accent)', fontSize: 22 }} />
          </div>
          <div>
            <div className={styles.headerTitle}>AI 求职助手</div>
            <div className={styles.headerDesc}>智能推荐岗位 · 查询公司 · 匹配分析</div>
          </div>
        </div>

        {/* 消息列表 */}
        <div className={styles.messages}>
          {messages.map((msg) => (
            <div key={msg.id} className={`${styles.messageRow} ${msg.role === 'user' ? styles.messageUser : styles.messageBot}`}>
              {/* 头像 */}
              <div className={`${styles.messageAvatar} ${msg.role === 'user' ? styles.messageAvatarUser : styles.messageAvatarBot}`}>
                {msg.role === 'user' ? <UserOutlined /> : <RobotOutlined style={{ color: 'var(--accent)' }} />}
              </div>

              {/* 消息气泡 */}
              <div className={`${styles.messageBubble} ${msg.role === 'user' ? styles.bubbleUser : styles.bubbleBot}`}>
                {/* 复制按钮（仅AI消息，且不在流式中） */}
                {msg.role === 'assistant' && msg.content && !msg.isStreaming && (
                  <button
                    className={styles.copyBtn}
                    onClick={() => handleCopy(msg.id, msg.content)}
                    title="复制"
                  >
                    {copiedId === msg.id ? <CheckOutlined /> : <CopyOutlined />}
                  </button>
                )}

                {/* 思考步骤（显示AI思考进度） */}
                {msg.thinkingSteps && msg.thinkingSteps.length > 0 && (
                  <div className={styles.progressSteps}>
                    {msg.thinkingSteps.map((step, i) => {
                      const isLast = i === msg.thinkingSteps!.length - 1;
                      const isDone = !msg.isStreaming || !isLast;  // 最后一步且正在流式 = 未完成
                      return (
                        <div key={i} className={styles.progressStep}>
                          <span className={`${styles.progressIcon} ${!isDone ? styles.iconActive : ''}`}>
                            {isDone ? '✅' : '⏳'}
                          </span>
                          <span className={isDone ? styles.stepDone : styles.stepActive}>
                            {step}
                            {/* 显示生成计时器 */}
                            {!isDone && step.includes('生成') && generatingSeconds > 0 && (
                              <span className={styles.timerBadge}>{generatingSeconds}s</span>
                            )}
                          </span>
                        </div>
                      );
                    })}
                  </div>
                )}

                {/* LLM深度思考（可折叠） */}
                {msg.llmThinking && (
                  <div className={styles.llmThinkingBox}>
                    {/* 折叠/展开按钮 */}
                    <div className={styles.llmThinkingToggle} onClick={(e) => {
                      const el = e.currentTarget.nextElementSibling as HTMLElement;
                      if (el) el.style.display = el.style.display === 'none' ? 'block' : 'none';
                      const icon = e.currentTarget.querySelector('.toggle-icon');
                      if (icon) icon.textContent = icon.textContent === '▶' ? '▼' : '▶';
                    }}>
                      <span className="toggle-icon">▶</span>
                      <span>🤔 小灵正在思考...</span>
                      <span className={styles.toggleHint}>点击展开</span>
                    </div>
                    {/* 思考内容（默认隐藏） */}
                    <div className={styles.llmThinkingContent} style={{ display: 'none' }}>
                      {msg.llmThinking}
                    </div>
                  </div>
                )}

                {/* 正文内容 */}
                {msg.content ? (
                  <div className={styles.messageContent}>
                    {/* 流式阶段用纯文本，完成后用Markdown渲染 */}
                    {msg.role === 'assistant' && !msg.isStreaming ? (
                      <MarkdownRenderer content={msg.content} />
                    ) : (
                      <span style={{ whiteSpace: 'pre-wrap' }}>{msg.content}</span>
                    )}
                    {/* 流式光标（闪烁的 | ） */}
                    {msg.isStreaming && <span className={styles.cursor}>|</span>}
                  </div>
                ) : msg.isStreaming && (!msg.thinkingSteps || msg.thinkingSteps.length === 0) && !msg.llmThinking ? (
                  // 思考中动画（没有内容、没有思考步骤、没有LLM思考时显示）
                  <div className={styles.thinkingAnimation}>
                    <div className={styles.thinkingDots}><span></span><span></span><span></span></div>
                    <span className={styles.thinkingText}>小灵正在思考...</span>
                  </div>
                ) : null}
              </div>
            </div>
          ))}
          {/* 消息列表底部锚点（用于自动滚动） */}
          <div ref={messagesEndRef} />
        </div>

        {/* 快捷操作按钮 */}
        <div className={styles.quickActions}>
          {['推荐岗位', '查询公司', '匹配分析'].map((action) => (
            <button key={action} className={styles.quickBtn} onClick={() => handleQuickAction(action)} disabled={sseLoading}>
              {action}
            </button>
          ))}
        </div>

        {/* 快捷问题（仅在欢迎页面显示） */}
        {messages.length === 1 && messages[0].id === 0 && (
          <div className={styles.quickQuestions}>
            <div className={styles.quickQuestionsTitle}>💡 试试问我：</div>
            <div className={styles.quickQuestionsList}>
              {[
                '帮我找北京的Java岗位',
                '推荐适合我的前端岗位',
                '字节跳动在招什么岗位',
                '我和这个岗位匹配度多少',
              ].map((q) => (
                <button key={q} className={styles.quickQuestionBtn} onClick={() => handleQuickAction(q)} disabled={sseLoading}>
                  {q}
                </button>
              ))}
            </div>
          </div>
        )}

        {/* 输入区域 */}
        <div className={styles.inputBar}>
          <Input.TextArea
            placeholder="输入你的问题..."
            value={inputValue}
            onChange={(e) => setInputValue(e.target.value)}
            onPressEnter={(e) => { if (!e.shiftKey) { e.preventDefault(); handleSend(); } }}
            autoSize={{ minRows: 1, maxRows: 4 }}
            disabled={sseLoading}
            style={{ flex: 1, borderRadius: 'var(--radius-md)' }}
          />
          {/* 生成中显示停止按钮，否则显示发送按钮 */}
          {sseLoading ? (
            <button className={styles.stopBtn} onClick={handleStop} title="停止生成">
              <span className={styles.stopIcon}>■</span>
            </button>
          ) : (
            <button className={styles.sendBtn} onClick={handleSend} disabled={!inputValue.trim()}>
              <SendOutlined />
            </button>
          )}
        </div>
      </div>
    </div>
  );
};

export default AIAssistantPage;
