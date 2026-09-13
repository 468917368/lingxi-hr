import React, { useMemo } from 'react';
import { Input, Dropdown, Spin, Avatar } from 'antd';
import {
  SearchOutlined,
  PushpinOutlined,
  BellOutlined,
  DeleteOutlined,
  StopOutlined,
  UserOutlined,
} from '@ant-design/icons';
import type { Conversation } from '@/constants/apiTypes';
import EmptyState from '@/components/EmptyState';
import { getAvatarUrl } from '@/utils/fileUrl';
import styles from './index.less';

interface ConversationListProps {
  conversations: Conversation[];
  activeId: string | null;
  loading?: boolean;
  searchValue?: string;
  onSelect: (conversation: Conversation) => void;
  onSearch?: (value: string) => void;
  onTop?: (id: string, isTop: boolean) => void;
  onMuted?: (id: string, isMuted: boolean) => void;
  onDelete?: (id: string) => void;
  /** 当前用户ID，用于判断消息方向 */
  currentUserId: number;
}

/** 格式化相对时间 */
function formatRelativeTime(time: string | null): string {
  if (!time) return '';
  const now = new Date();
  const date = new Date(time);
  const diffMs = now.getTime() - date.getTime();
  const diffMin = Math.floor(diffMs / 60000);
  const diffHour = Math.floor(diffMs / 3600000);
  const diffDay = Math.floor(diffMs / 86400000);

  if (diffMin < 1) return '刚刚';
  if (diffMin < 60) return `${diffMin}分钟前`;
  if (diffHour < 24) return `${diffHour}小时前`;
  if (diffDay === 1) return '昨天';
  if (diffDay < 7) return `${diffDay}天前`;

  const month = date.getMonth() + 1;
  const day = date.getDate();
  return `${month}月${day}日`;
}

/** 截取消息摘要 */
function truncateMessage(msg: string | null, maxLen = 20): string {
  if (!msg) return '暂无消息';
  return msg.length > maxLen ? msg.slice(0, maxLen) + '...' : msg;
}

const ConversationList: React.FC<ConversationListProps> = ({
  conversations,
  activeId,
  loading = false,
  searchValue = '',
  onSelect,
  onSearch,
  onTop,
  onMuted,
  onDelete,
}) => {
  const [searchText, setSearchText] = React.useState(searchValue);

  /** 搜索过滤 */
  const filteredConversations = useMemo(() => {
    if (!searchText) return conversations;
    const keyword = searchText.toLowerCase();
    return conversations.filter(
      (conv) =>
        conv.targetUser.name.toLowerCase().includes(keyword) ||
        conv.lastMessage?.toLowerCase().includes(keyword),
    );
  }, [conversations, searchText]);

  /** 排序：置顶优先，然后按时间倒序 */
  const sortedConversations = useMemo(() => {
    return [...filteredConversations].sort((a, b) => {
      if (a.isTop && !b.isTop) return -1;
      if (!a.isTop && b.isTop) return 1;
      const timeA = a.lastMessageTime ? new Date(a.lastMessageTime).getTime() : 0;
      const timeB = b.lastMessageTime ? new Date(b.lastMessageTime).getTime() : 0;
      return timeB - timeA;
    });
  }, [filteredConversations]);

  const handleSearch = (value: string) => {
    setSearchText(value);
    onSearch?.(value);
  };

  const getContextMenu = (conv: Conversation) => ({
    items: [
      {
        key: 'top',
        icon: <PushpinOutlined />,
        label: conv.isTop ? '取消置顶' : '置顶',
        onClick: () => onTop?.(conv.id, !conv.isTop),
      },
      {
        key: 'muted',
        icon: conv.isMuted ? <BellOutlined /> : <StopOutlined />,
        label: conv.isMuted ? '取消免打扰' : '免打扰',
        onClick: () => onMuted?.(conv.id, !conv.isMuted),
      },
      { type: 'divider' as const },
      {
        key: 'delete',
        icon: <DeleteOutlined />,
        label: '删除会话',
        danger: true,
        onClick: () => onDelete?.(conv.id),
      },
    ],
  });

  return (
    <div className={styles.container}>
      <div className={styles.header}>
        <div className={styles.title}>消息沟通</div>
        <Input
          prefix={<SearchOutlined />}
          placeholder="搜索会话"
          value={searchText}
          onChange={(e) => handleSearch(e.target.value)}
          allowClear
          size="small"
          className={styles.searchInput}
        />
      </div>

      <div className={styles.list}>
        {loading ? (
          <div className={styles.loading}>
            <Spin />
          </div>
        ) : sortedConversations.length === 0 ? (
          <EmptyState description="暂无消息" />
        ) : (
          sortedConversations.map((conv) => (
            <Dropdown
              key={conv.id}
              menu={getContextMenu(conv)}
              trigger={['contextMenu']}
            >
              <div
                className={`${styles.item} ${activeId === conv.id ? styles.itemActive : ''} ${conv.isTop ? styles.itemTop : ''}`}
                onClick={() => onSelect(conv)}
              >
                <div className={styles.avatarWrapper}>
                  <Avatar
                    size={44}
                    src={getAvatarUrl(conv.targetUser.avatar)}
                    icon={<UserOutlined />}
                  />
                  {conv.targetUser.isOnline && (
                    <div className={styles.onlineDot} />
                  )}
                </div>
                <div className={styles.info}>
                  <div className={styles.nameRow}>
                    <span className={styles.name}>{conv.targetUser.name}</span>
                    {conv.targetUser.company && (
                      <span className={styles.company}>{conv.targetUser.company}</span>
                    )}
                  </div>
                  <div className={styles.preview}>
                    {truncateMessage(conv.lastMessage)}
                  </div>
                </div>
                <div className={styles.meta}>
                  <span className={styles.time}>
                    {formatRelativeTime(conv.lastMessageTime)}
                  </span>
                  {conv.unreadCount > 0 && (
                    <span className={styles.unread}>
                      {conv.unreadCount > 99 ? '99+' : conv.unreadCount}
                    </span>
                  )}
                  {conv.isMuted && (
                    <StopOutlined className={styles.mutedIcon} />
                  )}
                </div>
              </div>
            </Dropdown>
          ))
        )}
      </div>
    </div>
  );
};

export default ConversationList;
