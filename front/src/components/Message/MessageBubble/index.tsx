import React, { useCallback } from 'react';
import { FileOutlined, UndoOutlined, DownloadOutlined, UserOutlined } from '@ant-design/icons';
import { Image, Tooltip, Avatar } from 'antd';
import type { Message } from '@/constants/apiTypes';
import { getFullFileUrl, getAvatarUrl } from '@/utils/fileUrl';
import styles from './index.less';

interface MessageBubbleProps {
  message: Message;
  isOwn: boolean;
  /** 当前用户头像 */
  currentUserAvatar?: string | null;
  /** 当前用户名称 */
  currentUserName?: string;
  /** 对方用户头像 */
  targetUserAvatar?: string | null;
  /** 对方用户名称 */
  targetUserName?: string;
  /** 撤回消息回调 */
  onRecall?: (messageId: string) => void;
  /** 点击头像回调 */
  onAvatarClick?: (userId: number) => void;
}

/** 格式化消息时间 */
function formatMessageTime(time: string): string {
  const date = new Date(time);
  const now = new Date();
  const isToday =
    date.getFullYear() === now.getFullYear() &&
    date.getMonth() === now.getMonth() &&
    date.getDate() === now.getDate();

  const hours = date.getHours().toString().padStart(2, '0');
  const minutes = date.getMinutes().toString().padStart(2, '0');

  if (isToday) {
    return `${hours}:${minutes}`;
  }

  const yesterday = new Date(now);
  yesterday.setDate(yesterday.getDate() - 1);
  const isYesterday =
    date.getFullYear() === yesterday.getFullYear() &&
    date.getMonth() === yesterday.getMonth() &&
    date.getDate() === yesterday.getDate();

  if (isYesterday) {
    return `昨天 ${hours}:${minutes}`;
  }

  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${month}月${day}日 ${hours}:${minutes}`;
}

/** 格式化文件大小 */
function formatFileSize(bytes: number | null): string {
  if (!bytes) return '';
  if (bytes < 1024) return `${bytes}B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)}KB`;
  return `${(bytes / (1024 * 1024)).toFixed(1)}MB`;
}

const MessageBubble: React.FC<MessageBubbleProps> = ({
  message,
  isOwn,
  currentUserAvatar,
  currentUserName,
  targetUserAvatar,
  targetUserName,
  onRecall,
  onAvatarClick,
}) => {
  const avatar = isOwn ? currentUserAvatar : targetUserAvatar;
  const name = isOwn ? currentUserName : targetUserName;

  // 检查消息是否可以撤回（2分钟内）
  const canRecall = useCallback(() => {
    if (!isOwn || !onRecall) return false;
    const messageTime = new Date(message.createdAt).getTime();
    const now = Date.now();
    const twoMinutes = 2 * 60 * 1000;
    return now - messageTime < twoMinutes;
  }, [isOwn, onRecall, message.createdAt]);

  // 处理撤回
  const handleRecall = useCallback(() => {
    if (onRecall && canRecall()) {
      onRecall(message.id);
    }
  }, [onRecall, canRecall, message.id]);

  const renderContent = () => {
    switch (message.contentType) {
      case 'IMAGE': {
        const imageUrl = getFullFileUrl(message.mediaUrl);
        return (
          <div className={styles.imageBubble}>
            <Image
              src={imageUrl || ''}
              alt="图片消息"
              className={styles.image}
              preview={{
                mask: <span>预览</span>,
              }}
              fallback="data:image/png;base64,iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mN8/+F/PQAJhAN4kGk5RAAAAABJRU5ErkJggg=="
            />
          </div>
        );
      }

      case 'FILE': {
        const fileUrl = getFullFileUrl(message.mediaUrl);

        /** 下载文件（使用fetch获取blob，避免乱码） */
        const handleDownload = async () => {
          if (!fileUrl) return;
          try {
            const response = await fetch(fileUrl);
            const blob = await response.blob();
            const url = window.URL.createObjectURL(blob);
            const link = document.createElement('a');
            link.href = url;
            link.download = message.fileName || '文件';
            document.body.appendChild(link);
            link.click();
            document.body.removeChild(link);
            window.URL.revokeObjectURL(url);
          } catch (error) {
            console.error('下载文件失败:', error);
            // 降级：直接打开
            window.open(fileUrl, '_blank');
          }
        };

        return (
          <div
            className={styles.fileBubble}
            onClick={handleDownload}
            style={{ cursor: fileUrl ? 'pointer' : 'default' }}
          >
            <FileOutlined className={styles.fileIcon} />
            <div className={styles.fileInfo}>
              <div className={styles.fileName}>{message.fileName || '文件'}</div>
              <div className={styles.fileSize}>{formatFileSize(message.fileSize)}</div>
            </div>
            {fileUrl && (
              <DownloadOutlined className={styles.fileDownload} />
            )}
          </div>
        );
      }

      case 'CARD_RESUME':
        return (
          <div className={styles.cardBubble}>
            <div className={styles.cardTitle}>📄 简历卡片</div>
            <div className={styles.cardContent}>{message.content}</div>
          </div>
        );

      case 'CARD_JOB':
        return (
          <div className={styles.cardBubble}>
            <div className={styles.cardTitle}>💼 岗位卡片</div>
            <div className={styles.cardContent}>{message.content}</div>
          </div>
        );

      case 'SYSTEM':
        return (
          <div className={styles.systemMessage}>
            {message.content}
          </div>
        );

      default:
        return (
          <div className={`${styles.bubble} ${isOwn ? styles.bubbleOwn : styles.bubbleOther}`}>
            {message.content}
          </div>
        );
    }
  };

  /** 渲染消息状态 */
  const renderStatus = () => {
    const status = message.status || (message.isRead ? 'READ' : 'SENT');

    switch (status) {
      case 'SENDING':
        return (
          <span className={styles.statusSending}>
            <span className={styles.sendingDot}></span>
            <span className={styles.sendingDot}></span>
            <span className={styles.sendingDot}></span>
          </span>
        );
      case 'SENT':
      case 'DELIVERED':
        return <span className={styles.statusSent}>已送达</span>;
      case 'READ':
        return <span className={styles.statusRead}>已读</span>;
      case 'FAILED':
        return <span className={styles.statusFailed}>发送失败</span>;
      default:
        return null;
    }
  };

  if (message.contentType === 'SYSTEM') {
    return (
      <div className={styles.systemRow}>
        {renderContent()}
      </div>
    );
  }

  return (
    <div className={`${styles.msgRow} ${isOwn ? styles.msgRowOwn : styles.msgRowOther}`}>
      {/* 头像 */}
      <div
        className={styles.avatar}
        onClick={() => !isOwn && onAvatarClick?.(message.senderId)}
        style={{ cursor: !isOwn && onAvatarClick ? 'pointer' : 'default' }}
      >
        <Avatar
          size={40}
          src={getAvatarUrl(avatar)}
          icon={<UserOutlined />}
        />
      </div>

      {/* 内容 */}
      <div className={styles.msgContent}>
        {/* 名称 + 时间 */}
        <div className={styles.msgHeader}>
          <span className={styles.msgName}>{name || (isOwn ? '我' : '对方')}</span>
          <span className={styles.msgTime}>{formatMessageTime(message.createdAt)}</span>
        </div>

        {/* 气泡 */}
        <div className={styles.bubbleWrapper}>
          {renderContent()}

          {/* 撤回按钮（悬浮显示） */}
          {canRecall() && (
            <Tooltip title="撤回消息">
              <button className={styles.recallBtn} onClick={handleRecall}>
                <UndoOutlined />
              </button>
            </Tooltip>
          )}
        </div>

        {/* 状态 */}
        {isOwn && (
          <div className={styles.msgStatus}>
            {renderStatus()}
          </div>
        )}
      </div>
    </div>
  );
};

export default React.memo(MessageBubble);
