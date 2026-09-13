/**
 * 求职者消息沟通页面
 *
 * 功能：
 * 1. 会话列表展示（左侧）
 * 2. 聊天区域（右侧）
 * 3. WebSocket实时消息收发
 * 4. 消息通知（声音、桌面通知、标题闪烁、favicon角标）
 * 5. 会话管理（置顶、免打扰、删除）
 *
 * 技术栈：
 * - React + TypeScript
 * - Ant Design UI组件
 * - WebSocket实时通信
 * - Zustand状态管理
 */
import React, { useEffect, useCallback, useState } from 'react';
import { Modal, message } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import type { Conversation, Message } from '@/constants/apiTypes';
import ConversationList from '@/components/Message/ConversationList';  // 会话列表组件
import ChatArea from '@/components/Message/ChatArea';  // 聊天区域组件
import MessageToast, { showMessageToast } from '@/components/MessageToast';  // 浮动通知组件
import useMessageStore from '@/stores/messageStore';  // 消息状态管理
import useUserStore from '@/stores/userStore';  // 用户状态管理
import { useWebSocketConnect, useWebSocketEvent, useWebSocketSend } from '@/hooks/useWebSocket-singleton';
import { wsManager } from '@/utils/websocket-singleton';
import * as messageService from '@/services/message';  // 消息API服务
import { playMessageSound, sendDesktopNotification, requestNotificationPermission, flashTitle, isNotificationSupported, setFaviconBadge } from '@/utils/sound';
import { ROUTES } from '@/constants/routes';
import styles from './index.less';

const MessagePage: React.FC = () => {
  // ==================== 状态定义 ====================

  /** 当前用户信息 */
  const { userInfo } = useUserStore();

  /**
   * 从消息Store解构出状态和方法
   * conversations: 会话列表
   * activeConversationId: 当前选中的会话ID
   * messages: 所有会话的消息Map（key=会话ID，value=消息数组）
   */
  const {
    conversations,
    activeConversationId,
    messages,
    setConversations,          // 设置会话列表
    setActiveConversation,     // 设置当前会话
    addMessage,                // 添加单条消息
    setMessages,               // 设置某个会话的全部消息
    prependMessages,           // 在消息列表前面追加（加载更早消息）
    markConversationRead,      // 标记会话已读
    updateConversationLastMessage,  // 更新会话的最后一条消息
    updateConversationTop,     // 更新会话置顶状态
    updateConversationMuted,   // 更新会话免打扰状态
    removeConversation,        // 删除会话
    markMessagesAsRead,        // 标记消息已读
  } = useMessageStore();

  /** 页面加载状态 */
  const [loading, setLoading] = useState(false);

  /** 消息发送中状态 */
  const [sending, setSending] = useState(false);

  /** 是否还有更早的消息可加载 */
  const [hasMore, setHasMore] = useState(true);

  /** 当前加载的页码 */
  const [page, setPage] = useState(1);

  /** 待确认的消息（等待SEND_SUCCESS后更新状态） */
  const [pendingMessage, setPendingMessage] = useState<any>(null);

  // ==================== WebSocket ====================

  /** WebSocket发送消息相关函数 */
  const { sendMessage, markRead, sendTyping, recallMessage } = useWebSocketSend();

  /** 连接WebSocket（全局只连接一次） */
  useWebSocketConnect();

  /** 请求桌面通知权限（页面加载时） */
  useEffect(() => {
    requestNotificationPermission();
  }, []);

  // ==================== 数据加载 ====================

  /**
   * 获取会话列表
   * 页面加载时调用，获取所有会话
   */
  const fetchConversations = useCallback(async () => {
    setLoading(true);
    try {
      const data = await messageService.getConversations();
      console.log('[Message] Conversations loaded:', data);
      setConversations(Array.isArray(data) ? data : []);
    } catch (error) {
      console.error('[Message] 获取会话列表失败:', error);
      message.error('获取会话列表失败');
      setConversations([]);
    } finally {
      setLoading(false);
    }
  }, [setConversations]);

  /**
   * 获取消息列表（优化：只加载最新消息）
   *
   * 优化策略：
   * 1. 先请求第1页，获取total总数
   * 2. 计算最后一页页码
   * 3. 直接加载最后一页（最新消息）
   * 4. 向上滚动时再加载更早的消息
   *
   * 这样避免加载全部消息，提升性能
   */
  const fetchMessages = useCallback(
    async (conversationId: string) => {
      try {
        const pageSize = 50; // 后端最大支持100，用50平衡加载速度和数量
        // 先请求第1页，获取total总数
        const data = await messageService.getMessages(conversationId, 1, pageSize);

        // 没有消息
        if (data.total === 0) {
          setMessages(conversationId, []);
          setPage(1);
          setHasMore(false);
          return;
        }

        // 计算最后一页页码
        const lastPageNum = Math.ceil(data.total / pageSize);

        // 如果只有一页，直接使用
        if (lastPageNum === 1) {
          const sorted = data.list.sort(
            (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          );
          setMessages(conversationId, sorted);
          setPage(1);
          setHasMore(false);
          return;
        }

        // 多页情况：直接加载最后一页（最新消息）
        const lastPage = await messageService.getMessages(conversationId, lastPageNum, pageSize);
        const latestMessages = lastPage.list.sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );

        console.log('[Message] Loaded latest', latestMessages.length, 'messages for conversation', conversationId);
        setMessages(conversationId, latestMessages);
        setPage(lastPageNum);
        setHasMore(lastPageNum > 1); // 还有更早的消息可以加载
      } catch (error) {
        console.error('[Message] 获取消息失败:', error);
        message.error('获取消息失败');
      }
    },
    [setMessages],
  );

  /**
   * 加载更早的消息（向上滚动时触发）
   * 加载上一页消息，插入到消息列表前面
   */
  const handleLoadMore = useCallback(async () => {
    if (!activeConversationId || !hasMore || loading) return;

    try {
      const pageSize = 50;
      const prevPage = page - 1;
      if (prevPage < 1) {
        setHasMore(false);
        return;
      }

      // 加载上一页消息
      const data = await messageService.getMessages(activeConversationId, prevPage, pageSize);
      const olderMessages = data.list.sort(
        (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
      );

      // 插入到消息列表前面
      prependMessages(activeConversationId, olderMessages);
      setPage(prevPage);
      setHasMore(prevPage > 1); // 还有更早的消息
    } catch (error) {
      console.error('[Message] 加载更多消息失败:', error);
    }
  }, [activeConversationId, page, hasMore, loading, prependMessages]);

  // ==================== 初始化 ====================

  /** 页面加载时获取会话列表 */
  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  /**
   * 会话列表加载后，自动选中第一个
   * 按最后消息时间排序，选最新的会话
   */
  useEffect(() => {
    if (!activeConversationId && conversations.length > 0) {
      const sorted = [...conversations].sort((a, b) => {
        const timeA = a.lastMessageTime ? new Date(a.lastMessageTime).getTime() : 0;
        const timeB = b.lastMessageTime ? new Date(b.lastMessageTime).getTime() : 0;
        return timeB - timeA;
      });
      setActiveConversation(sorted[0].id);
    }
  }, [conversations, activeConversationId, setActiveConversation]);

  /**
   * 切换会话时加载消息
   * 同时标记会话已读
   */
  useEffect(() => {
    if (activeConversationId) {
      fetchMessages(activeConversationId);       // 加载消息
      markConversationRead(activeConversationId); // 标记本地已读
      markRead(activeConversationId);             // 通知后端已读
    }
  }, [activeConversationId, fetchMessages, markConversationRead, markRead]);

  // ==================== WebSocket事件处理 ====================

  /**
   * 接收新消息
   *
   * 流程：
   * 1. 判断是自己发的还是对方发的
   * 2. 构建消息对象
   * 3. 添加到消息列表
   * 4. 更新会话的最后一条消息
   * 5. 如果是对方消息，播放提示音+发送通知
   * 6. 如果是当前会话，标记已读
   */
  useWebSocketEvent(
    'NEW_MESSAGE',
    useCallback(
      (data: any) => {
        console.log('[Message] Received NEW_MESSAGE:', data);

        // 判断是否是自己发的消息
        const isOwn = data.senderId === userInfo?.id;
        console.log('[Message] Is own message:', isOwn);

        // 获取会话信息（用于获取对方头像和名称）
        const conversation = conversations.find(c => c.id === data.conversationId);
        const targetUser = conversation?.targetUser;

        // 根据发送者判断使用谁的信息
        let senderName = '';
        let senderAvatar: string | null = null;

        if (isOwn) {
          // 自己发的消息
          senderName = userInfo?.name || '我';
          senderAvatar = userInfo?.avatar || null;
        } else {
          // 对方发的消息
          senderName = targetUser?.name || '';
          senderAvatar = targetUser?.avatar || null;
        }

        // 构建消息对象
        const newMessage: Message = {
          id: data.messageId,
          senderId: data.senderId,
          senderName,
          senderAvatar,
          msgType: data.msgType || 'TEXT',
          contentType: data.contentType || 'TEXT',
          content: data.content,
          mediaUrl: data.mediaUrl || null,
          fileName: data.fileName || null,
          fileSize: data.fileSize || null,
          isRead: false,
          createdAt: data.createdAt || new Date().toISOString(),
        };

        // 添加消息到列表
        console.log('[Message] Adding message to conversation:', data.conversationId);
        addMessage(data.conversationId, newMessage);
        // 更新会话的最后一条消息（侧边栏显示）
        updateConversationLastMessage(data.conversationId, newMessage);

        // 播放提示音和桌面通知（仅对方消息）
        if (!isOwn) {
          playMessageSound();  // 播放提示音

          // 更新favicon角标（显示未读数）
          const totalUnread = useMessageStore.getState().conversations.reduce(
            (sum, c) => sum + (c.unreadCount || 0), 0
          ) + 1; // +1 当前新消息
          setFaviconBadge(totalUnread);

          // 发送桌面通知（仅页面不可见时）
          if (document.hidden) {
            if (isNotificationSupported()) {
              // 支持桌面通知（HTTPS环境）
              sendDesktopNotification(`新消息来自 ${senderName}`, {
                body: data.content,
                tag: `message-${data.conversationId}`,
              });
            } else {
              // 降级方案（HTTP环境）
              flashTitle(senderName);  // 标题闪烁
              showMessageToast({       // 页面内浮动通知
                senderName,
                senderAvatar,
                content: data.content,
                conversationId: data.conversationId,
              });
            }
          }
        }

        // 如果是当前会话且是对方消息，标记已读
        if (data.conversationId === activeConversationId && !isOwn) {
          markRead(data.conversationId);
        }
      },
      [userInfo, conversations, addMessage, updateConversationLastMessage, activeConversationId, markRead],
    ),
  );

  /**
   * 发送成功确认
   * 将临时消息的状态从 SENDING 改为 SENT
   */
  useWebSocketEvent(
    'SEND_SUCCESS',
    useCallback(
      (data: any) => {
        console.log('[Message] Send success:', data);
        setSending(false);

        if (pendingMessage) {
          const messages = useMessageStore.getState().messages.get(data.conversationId) || [];
          const updatedMessages = messages.map(msg => {
            // 找到发送中的临时消息（通过内容匹配）
            if (msg.status === 'SENDING' && msg.content === pendingMessage.content) {
              return { ...msg, id: data.messageId, status: 'SENT' as const };
            }
            return msg;
          });
          useMessageStore.getState().setMessages(data.conversationId, updatedMessages);
          setPendingMessage(null);
        }
      },
      [pendingMessage],
    ),
  );

  /**
   * 发送失败
   * 将临时消息的状态从 SENDING 改为 FAILED
   */
  useWebSocketEvent(
    'SEND_FAIL',
    useCallback(
      (data: any) => {
        console.error('[Message] Send failed:', data);
        setSending(false);

        if (pendingMessage) {
          const messages = useMessageStore.getState().messages.get(data.conversationId) || [];
          const updatedMessages = messages.map(msg => {
            if (msg.status === 'SENDING' && msg.content === pendingMessage.content) {
              return { ...msg, status: 'FAILED' as const };
            }
            return msg;
          });
          useMessageStore.getState().setMessages(data.conversationId, updatedMessages);
        }

        message.error(data.errorMessage || '消息发送失败');
        setPendingMessage(null);
      },
      [pendingMessage],
    ),
  );

  /**
   * 对方已读
   * 更新当前会话的消息状态为已读
   */
  useWebSocketEvent(
    'MESSAGES_READ',
    useCallback(
      (data: any) => {
        console.log('[Message] Messages read by other:', data);
        if (data.conversationId) {
          markMessagesAsRead(data.conversationId);
        }
      },
      [markMessagesAsRead],
    ),
  );

  /**
   * 消息撤回
   * 将撤回的消息内容改为"对方撤回了一条消息"
   */
  useWebSocketEvent(
    'MESSAGE_RECALLED',
    useCallback(
      (data: any) => {
        console.log('[Message] Message recalled:', data);
        if (data.conversationId && data.messageId) {
          const messages = useMessageStore.getState().messages.get(data.conversationId) || [];
          const updatedMessages = messages.map(msg => {
            if (msg.id === data.messageId) {
              return {
                ...msg,
                content: '对方撤回了一条消息',
                contentType: 'SYSTEM' as const,
                mediaUrl: null,
                fileName: null,
                fileSize: null,
              };
            }
            return msg;
          });
          useMessageStore.getState().setMessages(data.conversationId, updatedMessages);
        }
      },
      [],
    ),
  );

  // ==================== 用户操作 ====================

  /**
   * 选择会话
   * 点击侧边栏的会话项时触发
   */
  const handleSelectConversation = useCallback(
    (conv: Conversation) => {
      setActiveConversation(conv.id);
    },
    [setActiveConversation],
  );

  /**
   * 发送消息
   *
   * 流程：
   * 1. 创建临时消息（状态SENDING）立即显示
   * 2. 通过WebSocket发送消息
   * 3. 等待SEND_SUCCESS确认后更新状态为SENT
   *
   * 这叫"乐观更新"：先显示，后确认
   */
  const handleSend = useCallback(
    (content: string, contentType = 'TEXT', extra?: {
      mediaUrl?: string;
      fileName?: string;
      fileSize?: number;
    }) => {
      if (!activeConversationId) {
        console.warn('[Message] No active conversation');
        return;
      }

      const msgType = contentType === 'IMAGE' ? 'IMAGE' : contentType === 'FILE' ? 'FILE' : 'TEXT';
      console.log('[Message] Sending:', { activeConversationId, content, contentType, msgType, extra });
      setSending(true);

      // 创建临时消息（状态SENDING）
      const tempMessage: Message = {
        id: String(Date.now()), // 临时ID（Message.id 为字符串）
        senderId: userInfo?.id || 0,
        senderName: userInfo?.name || '我',
        senderAvatar: userInfo?.avatar || null,
        msgType: msgType as any,
        contentType: contentType as any,
        content,
        mediaUrl: extra?.mediaUrl || null,
        fileName: extra?.fileName || null,
        fileSize: extra?.fileSize || null,
        isRead: false,
        status: 'SENDING',  // 发送中状态
        createdAt: new Date().toISOString(),
      };

      // 立即添加到消息列表（乐观更新）
      addMessage(activeConversationId, tempMessage);

      // 保存待发送消息，等待SEND_SUCCESS后更新状态
      setPendingMessage({
        content,
        contentType,
        msgType,
        tempId: tempMessage.id,
        ...extra,
      });

      // 通过WebSocket发送消息
      sendMessage(activeConversationId, content, contentType, msgType, extra);
    },
    [activeConversationId, userInfo, sendMessage, addMessage],
  );

  /**
   * 置顶/取消置顶会话
   */
  const handleTop = useCallback(
    async (id: string, isTop: boolean) => {
      try {
        await messageService.setConversationTop(id, isTop);
        updateConversationTop(id, isTop);
        message.success(isTop ? '已置顶' : '已取消置顶');
      } catch (error) {
        message.error('操作失败');
      }
    },
    [updateConversationTop],
  );

  /**
   * 开启/关闭免打扰
   */
  const handleMuted = useCallback(
    async (id: string, isMuted: boolean) => {
      try {
        await messageService.setConversationMuted(id, isMuted);
        updateConversationMuted(id, isMuted);
        message.success(isMuted ? '已开启免打扰' : '已关闭免打扰');
      } catch (error) {
        message.error('操作失败');
      }
    },
    [updateConversationMuted],
  );

  /**
   * 删除会话
   * 弹出确认对话框，确认后删除
   */
  const handleDelete = useCallback(
    (id: string) => {
      Modal.confirm({
        title: '确认删除',
        icon: <ExclamationCircleOutlined />,
        content: '删除后将不再显示该会话',
        okText: '删除',
        cancelText: '取消',
        okButtonProps: { danger: true },
        onOk: async () => {
          try {
            await messageService.deleteConversation(id);
            removeConversation(id);
            // 如果删除的是当前会话，清空选中状态
            if (activeConversationId === id) {
              setActiveConversation(null);
            }
            message.success('已删除');
          } catch (error) {
            message.error('删除失败');
          }
        },
      });
    },
    [removeConversation, activeConversationId, setActiveConversation],
  );

  // ==================== 计算属性 ====================

  /** 当前选中的会话对象 */
  const activeConversation = conversations.find((c) => c.id === activeConversationId) || null;

  /** 当前会话的消息列表 */
  const currentMessages = activeConversationId ? messages.get(activeConversationId) || [] : [];

  // ==================== 渲染 ====================

  /**
   * 页面结构：
   * ┌──────────────┬──────────────────┐
   * │   会话列表    │    聊天区域       │
   * │  (左侧)      │    (右侧)        │
   * └──────────────┴──────────────────┘
   */
  return (
    <div className={styles.page}>
      {/* 左侧：会话列表 */}
      <ConversationList
        conversations={conversations}
        activeId={activeConversationId}
        loading={loading}
        currentUserId={userInfo?.id || 0}
        onSelect={handleSelectConversation}
        onTop={handleTop}
        onMuted={handleMuted}
        onDelete={handleDelete}
      />

      {/* 右侧：聊天区域 */}
      <ChatArea
        conversation={activeConversation}
        messages={currentMessages}
        currentUserId={userInfo?.id || 0}
        currentUserAvatar={userInfo?.avatar}
        currentUserName={userInfo?.name || '我'}
        loading={loading}
        sending={sending}
        hasMore={hasMore}
        onLoadMore={handleLoadMore}
        onSend={handleSend}
        onTyping={() => activeConversationId && sendTyping(activeConversationId)}
        onRecall={(messageId) => activeConversationId && recallMessage(activeConversationId, messageId)}
      />

      {/* 消息浮动通知（HTTP环境降级方案） */}
      <MessageToast messagePath={ROUTES.CANDIDATE_MESSAGE} />
    </div>
  );
};

export default MessagePage;
