import React, { useRef, useEffect, useLayoutEffect, useCallback, useState } from 'react';
import { Spin, Button, Empty, Avatar } from 'antd';
import { MoreOutlined, LoadingOutlined, UserOutlined } from '@ant-design/icons';
import type { Conversation, Message } from '@/constants/apiTypes';
import { UserRole } from '@/constants/enums';
import MessageBubble from '@/components/Message/MessageBubble';
import MessageInput from '@/components/Message/MessageInput';
import EmptyState from '@/components/EmptyState';
import UserProfileCard from '@/components/UserProfileCard';
import type { UserPublicInfo, HRPublicInfo } from '@/components/UserProfileCard';
import { getUserPublicInfo } from '@/services/user';
import { getHRPublicInfo } from '@/services/hr';
import { useWebSocketEvent } from '@/hooks/useWebSocket';
import { getAvatarUrl } from '@/utils/fileUrl';
import styles from './index.less';

interface ChatAreaProps {
  conversation: Conversation | null;
  messages: Message[];
  currentUserId: number;
  /** 当前用户头像 */
  currentUserAvatar?: string | null;
  /** 当前用户名称 */
  currentUserName?: string;
  loading?: boolean;
  sending?: boolean;
  hasMore?: boolean;
  onLoadMore?: () => void;
  onSend: (content: string, contentType?: string, extra?: {
    mediaUrl?: string;
    fileName?: string;
    fileSize?: number;
  }) => void;
  onTyping?: () => void;
  onRecall?: (messageId: string) => void;
  onMoreActions?: () => void;
}

const ChatArea: React.FC<ChatAreaProps> = ({
  conversation,
  messages,
  currentUserId,
  currentUserAvatar,
  currentUserName,
  loading = false,
  sending = false,
  hasMore = false,
  onLoadMore,
  onSend,
  onTyping,
  onRecall,
  onMoreActions,
}) => {
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const messagesContainerRef = useRef<HTMLDivElement>(null);
  const [isLoadingMore, setIsLoadingMore] = useState(false);
  const prevScrollHeightRef = useRef(0);
  const [isTyping, setIsTyping] = useState(false);
  const typingTimerRef = useRef<ReturnType<typeof setTimeout> | null>(null);
  // 标记是否已初始化滚动位置（用于隐藏滚动过程）
  const [isScrollReady, setIsScrollReady] = useState(false);

  // 用户资料卡片状态
  const [profileCardUser, setProfileCardUser] = useState<UserPublicInfo | HRPublicInfo | null>(null);
  const [profileCardLoading, setProfileCardLoading] = useState(false);
  const [profileCardVisible, setProfileCardVisible] = useState(false);
  const [profileCardIsHR, setProfileCardIsHR] = useState(false);

  // 点击头像显示用户资料卡片
  const handleAvatarClick = useCallback(async (userId: number) => {
    setProfileCardVisible(true);
    setProfileCardLoading(true);

    // 判断是否是HR（通过会话中的目标用户角色）
    const isTargetHR = conversation?.targetUser?.role === UserRole.HR;
    setProfileCardIsHR(isTargetHR);

    try {
      if (isTargetHR) {
        const hrInfo = await getHRPublicInfo(userId);
        setProfileCardUser(hrInfo);
      } else {
        const userInfo = await getUserPublicInfo(userId);
        setProfileCardUser(userInfo);
      }
    } catch (error) {
      console.error('获取用户信息失败:', error);
      setProfileCardUser(null);
    } finally {
      setProfileCardLoading(false);
    }
  }, [conversation?.targetUser?.role]);

  // 监听对方正在输入事件
  useWebSocketEvent(
    'TYPING',
    useCallback((data: any) => {
      if (data.conversationId === conversation?.id) {
        setIsTyping(true);
        // 3秒后自动隐藏
        if (typingTimerRef.current) {
          clearTimeout(typingTimerRef.current);
        }
        typingTimerRef.current = setTimeout(() => {
          setIsTyping(false);
        }, 3000);
      }
    }, [conversation?.id]),
  );

  /** 滚动到底部 */
  const scrollToBottom = useCallback((behavior: ScrollBehavior = 'smooth') => {
    // 使用requestAnimationFrame确保DOM已更新
    requestAnimationFrame(() => {
      if (messagesEndRef.current) {
        messagesEndRef.current.scrollIntoView({ behavior, block: 'end' });
      }
    });
  }, []);

  /** 待定位到底部的会话ID(切换会话后等消息加载完成再滚) */
  const pendingScrollConvRef = useRef<string | null>(null);

  /** 切换会话时标记需要定位到底部，并隐藏内容直到滚动就绪 */
  useEffect(() => {
    pendingScrollConvRef.current = conversation?.id ?? null;
    setIsScrollReady(false);
  }, [conversation?.id]);

  /** 消息变化时处理滚动（用 useLayoutEffect 在绘制前定位，避免看到滚动过程） */
  useLayoutEffect(() => {
    if (messages.length === 0) return;

    const container = messagesContainerRef.current;
    if (!container) return;

    // 刚切换的会话:消息加载完成后直接定位到底部（无动画）
    if (pendingScrollConvRef.current === conversation?.id) {
      pendingScrollConvRef.current = null;
      // 直接设置scrollTop，在浏览器绘制前生效，用户看不到滚动过程
      container.scrollTop = container.scrollHeight;
      // 标记滚动就绪，显示内容
      setIsScrollReady(true);
      return;
    }
  }, [messages, conversation?.id]);

  /** 新消息滚动处理（用 useEffect，允许平滑动画） */
  useEffect(() => {
    if (messages.length === 0) return;

    const container = messagesContainerRef.current;
    if (!container) return;

    // 刚切换的会话已在 useLayoutEffect 中处理
    if (pendingScrollConvRef.current === conversation?.id) return;

    // 新消息:自己发的或已在底部附近
    const { scrollTop, scrollHeight, clientHeight } = container;
    const isNearBottom = scrollHeight - scrollTop - clientHeight < 200;
    const lastMessage = messages[messages.length - 1];
    const isOwnMessage = lastMessage?.senderId === currentUserId;

    if (isOwnMessage || isNearBottom) {
      scrollToBottom();
    }
  }, [messages, conversation?.id, currentUserId, scrollToBottom]);

  /** 加载更多历史消息 */
  const handleScroll = useCallback(async () => {
    const container = messagesContainerRef.current;
    if (!container || isLoadingMore || !hasMore || !onLoadMore) return;

    if (container.scrollTop < 50) {
      setIsLoadingMore(true);
      prevScrollHeightRef.current = container.scrollHeight;
      await onLoadMore();
      // 保持滚动位置
      requestAnimationFrame(() => {
        if (container) {
          const newScrollHeight = container.scrollHeight;
          container.scrollTop = newScrollHeight - prevScrollHeightRef.current;
        }
        setIsLoadingMore(false);
      });
    }
  }, [isLoadingMore, hasMore, onLoadMore]);

  /** 监听滚动事件 */
  useEffect(() => {
    const container = messagesContainerRef.current;
    if (!container) return;
    container.addEventListener('scroll', handleScroll);
    return () => container.removeEventListener('scroll', handleScroll);
  }, [handleScroll]);

  /** 未选择会话 */
  if (!conversation) {
    return (
      <div className={styles.container}>
        <EmptyState description="选择一个会话开始聊天" />
      </div>
    );
  }

  // 对方用户信息
  const targetUserAvatar = getAvatarUrl(conversation.targetUser.avatar);
  const targetUserName = conversation.targetUser.name;

  return (
    <div className={styles.container}>
      {/* 头部 */}
      <div className={styles.header}>
        <div className={styles.headerInfo}>
          <div className={styles.headerName}>{targetUserName}</div>
          {conversation.targetUser.company && (
            <div className={styles.headerCompany}>{conversation.targetUser.company}</div>
          )}
        </div>
        {onMoreActions && (
          <Button type="text" icon={<MoreOutlined />} onClick={onMoreActions} />
        )}
      </div>

      {/* 消息列表（切换会话时隐藏内容，等滚动就绪后再显示，避免看到滚动过程） */}
      <div
        className={styles.messages}
        ref={messagesContainerRef}
        style={{ visibility: isScrollReady ? 'visible' : 'hidden' }}
      >
        {isLoadingMore && (
          <div className={styles.loadingMore}>
            <Spin indicator={<LoadingOutlined />} size="small" />
            <span>加载中...</span>
          </div>
        )}

        {loading ? (
          <div className={styles.loading}>
            <Spin />
          </div>
        ) : messages.length === 0 ? (
          <div className={styles.empty}>
            <Empty description="开始和TA聊聊吧" />
          </div>
        ) : (
          messages.map((msg) => (
            <MessageBubble
              key={msg.id}
              message={msg}
              isOwn={msg.senderId === currentUserId}
              currentUserAvatar={currentUserAvatar}
              currentUserName={currentUserName}
              targetUserAvatar={targetUserAvatar}
              targetUserName={targetUserName}
              onRecall={onRecall}
              onAvatarClick={handleAvatarClick}
            />
          ))
        )}
        <div ref={messagesEndRef} />
      </div>

      {/* 正在输入提示 */}
      {isTyping && (
        <div className={styles.typingIndicator}>
          <span className={styles.typingDot}></span>
          <span className={styles.typingDot}></span>
          <span className={styles.typingDot}></span>
          <span className={styles.typingText}>对方正在输入...</span>
        </div>
      )}

      {/* 输入框 */}
      <MessageInput
        onSend={onSend}
        onTyping={onTyping}
        disabled={!conversation}
        loading={sending}
      />

      {/* 用户资料卡片弹窗 */}
      {profileCardVisible && (
        <div className={styles.profileCardOverlay} onClick={() => setProfileCardVisible(false)}>
          <div className={styles.profileCardWrapper} onClick={(e) => e.stopPropagation()}>
            <UserProfileCard
              user={profileCardUser}
              loading={profileCardLoading}
              visible={profileCardVisible}
              isHR={profileCardIsHR}
            />
          </div>
        </div>
      )}
    </div>
  );
};

export default React.memo(ChatArea);
