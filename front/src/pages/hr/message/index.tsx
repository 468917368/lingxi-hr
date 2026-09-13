import React, { useEffect, useCallback, useState, useRef } from 'react';
import { useSearchParams } from 'umi';
import { Modal, message } from 'antd';
import { ExclamationCircleOutlined } from '@ant-design/icons';
import PageHero from '@/components/PageHero';
import ConversationList from '@/components/Message/ConversationList';
import ChatArea from '@/components/Message/ChatArea';
import MessageToast, { showMessageToast } from '@/components/MessageToast';
import useMessageStore from '@/stores/messageStore';
import useUserStore from '@/stores/userStore';
import { useWebSocketConnect, useWebSocketEvent, useWebSocketSend } from '@/hooks/useWebSocket-singleton';
import { wsManager } from '@/utils/websocket-singleton';
import * as messageService from '@/services/message';
import { playMessageSound, sendDesktopNotification, requestNotificationPermission, flashTitle, isNotificationSupported, setFaviconBadge } from '@/utils/sound';
import { ROUTES } from '@/constants/routes';
import type { Conversation, Message } from '@/constants/apiTypes';
import styles from './index.less';

const HRMessagePage: React.FC = () => {
  const { userInfo } = useUserStore();
  const {
    conversations,
    activeConversationId,
    messages,
    setConversations,
    setActiveConversation,
    addMessage,
    setMessages,
    prependMessages,
    markConversationRead,
    updateConversationLastMessage,
    updateConversationTop,
    updateConversationMuted,
    removeConversation,
    markMessagesAsRead,
  } = useMessageStore();

  const [loading, setLoading] = React.useState(false);
  const [sending, setSending] = React.useState(false);
  const [hasMore, setHasMore] = React.useState(true);
  const [page, setPage] = React.useState(1);
  const [pendingMessage, setPendingMessage] = React.useState<any>(null);

  const { sendMessage, markRead, sendTyping, recallMessage } = useWebSocketSend();

  // 从 URL query 读取目标会话（人才库「对话」入口跳转）
  const [searchParams] = useSearchParams();
  const targetConversationId = searchParams.get('conversationId');
  const handledQueryRef = useRef<string | null>(null); // 防重复处理

  // 连接WebSocket
  useWebSocketConnect();

  // 请求桌面通知权限
  useEffect(() => {
    requestNotificationPermission();
  }, []);

  // 获取会话列表
  const fetchConversations = useCallback(async () => {
    setLoading(true);
    try {
      const data = await messageService.getConversations();
      setConversations(data);
    } catch (error) {
      message.error('获取会话列表失败');
    } finally {
      setLoading(false);
    }
  }, [setConversations]);

  // 获取消息列表（优化：只加载最新一页，更快显示最新消息）
  const fetchMessages = useCallback(
    async (conversationId: string) => {
      try {
        // 先加载第一页获取总数
        const pageSize = 50;
        const firstPage = await messageService.getMessages(conversationId, 1, pageSize);

        if (firstPage.total === 0) {
          setMessages(conversationId, []);
          setPage(1);
          setHasMore(false);
          return;
        }

        // 计算最后一页
        const lastPageNum = Math.ceil(firstPage.total / pageSize);

        // 如果只有一页，直接使用
        if (lastPageNum === 1) {
          const sorted = firstPage.list.sort(
            (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
          );
          setMessages(conversationId, sorted);
          setPage(1);
          setHasMore(false);
          return;
        }

        // 加载最后一页（最新消息）
        const lastPage = await messageService.getMessages(conversationId, lastPageNum, pageSize);
        const latestMessages = lastPage.list.sort(
          (a, b) => new Date(a.createdAt).getTime() - new Date(b.createdAt).getTime()
        );

        console.log('[Message] Loaded latest', latestMessages.length, 'messages for conversation', conversationId);
        setMessages(conversationId, latestMessages);
        setPage(lastPageNum);
        setHasMore(lastPageNum > 1);
      } catch (error) {
        console.error('[Message] 获取消息失败:', error);
        message.error('获取消息失败');
      }
    },
    [setMessages],
  );

  // 初始加载会话列表
  useEffect(() => {
    fetchConversations();
  }, [fetchConversations]);

  // 会话列表加载后，自动选中最新的会话（如果没有URL指定的目标会话）
  useEffect(() => {
    if (!targetConversationId && !activeConversationId && conversations.length > 0) {
      const sorted = [...conversations].sort((a, b) => {
        const timeA = a.lastMessageTime ? new Date(a.lastMessageTime).getTime() : 0;
        const timeB = b.lastMessageTime ? new Date(b.lastMessageTime).getTime() : 0;
        return timeB - timeA;
      });
      setActiveConversation(sorted[0].id);
    }
  }, [conversations, activeConversationId, targetConversationId, setActiveConversation]);

  // 从 URL query 自动打开目标会话（人才库「对话」入口）
  useEffect(() => {
    if (!targetConversationId) return;
    const id = String(targetConversationId); // 统一转字符串，避免类型不匹配
    if (!id || handledQueryRef.current === id) return;

    // 如果还在加载，等待加载完成后再处理
    if (loading) return;

    const existing = conversations.find((c) => String(c.id) === id);
    if (existing) {
      handledQueryRef.current = id;
      setActiveConversation(existing.id);
      return;
    }
    // 新创建的会话不在列表中时（兜底），拉详情插入顶部再打开
    messageService
      .getConversationDetail(id)
      .then((conv) => {
        handledQueryRef.current = id;
        // zustand setConversations 仅接受数组，从 store 取当前值合并并去重
        const prev = useMessageStore.getState().conversations;
        setConversations([conv, ...prev.filter((c) => String(c.id) !== String(conv.id))]);
        setActiveConversation(conv.id);
      })
      .catch(() => message.error('会话加载失败'));
  }, [targetConversationId, loading, conversations, setActiveConversation, setConversations]);

  // 切换会话时加载消息
  useEffect(() => {
    if (activeConversationId) {
      fetchMessages(activeConversationId);
      markConversationRead(activeConversationId);
      markRead(activeConversationId);
    }
  }, [activeConversationId, fetchMessages, markConversationRead, markRead]);

  // WebSocket: 接收新消息
  useWebSocketEvent(
    'NEW_MESSAGE',
    useCallback(
      (data: any) => {
        console.log('[HR Message] Received NEW_MESSAGE:', data);

        const isOwn = data.senderId === userInfo?.id;
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

        console.log('[HR Message] Adding message to conversation:', data.conversationId);
        addMessage(data.conversationId, newMessage);
        updateConversationLastMessage(data.conversationId, newMessage);

        // 播放提示音和桌面通知（仅对方消息）
        if (!isOwn) {
          playMessageSound();

          // 更新favicon角标
          const totalUnread = useMessageStore.getState().conversations.reduce(
            (sum, c) => sum + (c.unreadCount || 0), 0
          ) + 1; // +1 当前新消息
          setFaviconBadge(totalUnread);

          // 发送桌面通知（页面不可见时）
          if (document.hidden) {
            if (isNotificationSupported()) {
              sendDesktopNotification(`新消息来自 ${senderName}`, {
                body: data.content,
                tag: `message-${data.conversationId}`,
              });
            } else {
              // 降级方案1：页面标题闪烁
              flashTitle(senderName);
              // 降级方案2：页面内浮动通知
              showMessageToast({
                senderName,
                senderAvatar,
                content: data.content,
                conversationId: data.conversationId,
              });
            }
          }
        }

        // 如果当前会话且是对方消息，标记已读
        if (data.conversationId === activeConversationId && !isOwn) {
          markRead(data.conversationId);
        }
      },
      [userInfo, conversations, addMessage, updateConversationLastMessage, activeConversationId, markRead],
    ),
  );

  // WebSocket: 发送成功确认
  useWebSocketEvent(
    'SEND_SUCCESS',
    useCallback(
      (data: any) => {
        console.log('[Message] Send success:', data);
        setSending(false);

        // 更新临时消息的状态为已送达
        if (pendingMessage) {
          const messages = useMessageStore.getState().messages.get(data.conversationId) || [];
          const updatedMessages = messages.map(msg => {
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

  // WebSocket: 发送失败
  useWebSocketEvent(
    'SEND_FAIL',
    useCallback(
      (data: any) => {
        console.error('[Message] Send failed:', data);
        setSending(false);

        // 更新临时消息的状态为失败
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

  // WebSocket: 对方已读
  useWebSocketEvent(
    'MESSAGES_READ',
    useCallback(
      (data: any) => {
        console.log('[Message] Messages read by other:', data);
        // 更新当前会话的消息状态为已读
        if (data.conversationId) {
          markMessagesAsRead(data.conversationId);
        }
      },
      [markMessagesAsRead],
    ),
  );

  // WebSocket: 消息撤回
  useWebSocketEvent(
    'MESSAGE_RECALLED',
    useCallback(
      (data: any) => {
        console.log('[Message] Message recalled:', data);
        if (data.conversationId && data.messageId) {
          // 更新消息内容为"对方撤回了一条消息"
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

  // 选择会话
  const handleSelectConversation = useCallback(
    (conv: Conversation) => {
      setActiveConversation(conv.id);
    },
    [setActiveConversation],
  );

  // 发送消息
  const handleSend = useCallback(
    (content: string, contentType = 'TEXT', extra?: {
      mediaUrl?: string;
      fileName?: string;
      fileSize?: number;
    }) => {
      if (!activeConversationId) return;

      const msgType = contentType === 'IMAGE' ? 'IMAGE' : contentType === 'FILE' ? 'FILE' : 'TEXT';
      console.log('[Message] Sending:', { activeConversationId, content, contentType, msgType, extra });
      setSending(true);

      // 立即添加一个"发送中"的消息到列表（乐观更新）
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
        status: 'SENDING',
        createdAt: new Date().toISOString(),
      };

      addMessage(activeConversationId, tempMessage);

      // 保存待发送消息，等待SEND_SUCCESS后更新状态
      setPendingMessage({
        content,
        contentType,
        msgType,
        tempId: tempMessage.id,
        ...extra,
      });

      sendMessage(activeConversationId, content, contentType, msgType, extra);
    },
    [activeConversationId, userInfo, sendMessage, addMessage],
  );

  // 加载更多消息（已全部加载，无需实现）
  const handleLoadMore = useCallback(async () => {
    // 已一次性加载全部消息，无需分页
  }, []);

  // 置顶
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

  // 免打扰
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

  // 删除会话
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

  // 当前会话
  const activeConversation = conversations.find((c) => c.id === activeConversationId) || null;
  const currentMessages = activeConversationId ? messages.get(activeConversationId) || [] : [];

  return (
    <div className={`${styles.page} hr-page`}>
      <PageHero title="消息沟通" desc="与候选人实时沟通，提升招聘效率" />

      <div className={styles.chatLayout}>
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
      </div>

      {/* 消息浮动通知（HTTP环境降级方案） */}
      <MessageToast messagePath={ROUTES.HR_MESSAGE} />
    </div>
  );
};

export default HRMessagePage;
