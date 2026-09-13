import { create } from 'zustand';
import type { Conversation, Message } from '@/constants/apiTypes';

interface MessageState {
  /** 会话列表 */
  conversations: Conversation[];
  /** 当前活跃会话ID */
  activeConversationId: string | null;
  /** 消息缓存 Map<conversationId, Message[]> */
  messages: Map<string, Message[]>;
  /** 消息未读总数 */
  unreadMessageCount: number;

  /** 设置会话列表 */
  setConversations: (conversations: Conversation[]) => void;
  /** 设置当前活跃会话 */
  setActiveConversation: (id: string | null) => void;
  /** 添加消息 */
  addMessage: (conversationId: string, message: Message) => void;
  /** 设置会话消息 */
  setMessages: (conversationId: string, messages: Message[]) => void;
  /** 追加历史消息（前置） */
  prependMessages: (conversationId: string, messages: Message[]) => void;
  /** 更新会话最后消息 */
  updateConversationLastMessage: (conversationId: string, message: Message) => void;
  /** 标记会话已读 */
  markConversationRead: (conversationId: string) => void;
  /** 设置消息未读数 */
  setUnreadMessageCount: (count: number) => void;
  /** 更新会话置顶状态 */
  updateConversationTop: (conversationId: string, isTop: boolean) => void;
  /** 更新会话免打扰状态 */
  updateConversationMuted: (conversationId: string, isMuted: boolean) => void;
  /** 删除会话 */
  removeConversation: (conversationId: string) => void;
  /** 更新消息状态（对方已读） */
  markMessagesAsRead: (conversationId: string) => void;
}

const useMessageStore = create<MessageState>()((set) => ({
  conversations: [],
  activeConversationId: null,
  messages: new Map(),
  unreadMessageCount: 0,

  setConversations: (conversations) => set({ conversations }),

  setActiveConversation: (id) => set({ activeConversationId: id }),

  addMessage: (conversationId, message) =>
    set((state) => {
      const newMessages = new Map(state.messages);
      const existing = newMessages.get(conversationId) || [];

      // 检查是否已存在相同ID的消息，避免重复
      const isDuplicate = existing.some((msg) => msg.id === message.id);
      if (isDuplicate) {
        return state; // 不添加重复消息
      }

      newMessages.set(conversationId, [...existing, message]);
      return { messages: newMessages };
    }),

  setMessages: (conversationId, messages) =>
    set((state) => {
      const newMessages = new Map(state.messages);
      newMessages.set(conversationId, messages);
      return { messages: newMessages };
    }),

  prependMessages: (conversationId, messages) =>
    set((state) => {
      const newMessages = new Map(state.messages);
      const existing = newMessages.get(conversationId) || [];
      newMessages.set(conversationId, [...messages, ...existing]);
      return { messages: newMessages };
    }),

  updateConversationLastMessage: (conversationId, message) =>
    set((state) => ({
      conversations: state.conversations.map((conv) =>
        conv.id === conversationId
          ? {
              ...conv,
              lastMessage: message.content,
              lastMessageTime: message.createdAt,
              unreadCount: conv.id === state.activeConversationId ? 0 : conv.unreadCount + 1,
            }
          : conv,
      ),
    })),

  markConversationRead: (conversationId) =>
    set((state) => ({
      conversations: state.conversations.map((conv) =>
        conv.id === conversationId ? { ...conv, unreadCount: 0 } : conv,
      ),
    })),

  setUnreadMessageCount: (count) => set({ unreadMessageCount: count }),

  updateConversationTop: (conversationId, isTop) =>
    set((state) => ({
      conversations: state.conversations.map((conv) =>
        conv.id === conversationId ? { ...conv, isTop } : conv,
      ),
    })),

  updateConversationMuted: (conversationId, isMuted) =>
    set((state) => ({
      conversations: state.conversations.map((conv) =>
        conv.id === conversationId ? { ...conv, isMuted } : conv,
      ),
    })),

  removeConversation: (conversationId) =>
    set((state) => ({
      conversations: state.conversations.filter((conv) => conv.id !== conversationId),
    })),

  markMessagesAsRead: (conversationId) =>
    set((state) => {
      const newMessages = new Map(state.messages);
      const existing = newMessages.get(conversationId) || [];

      // 将自己发送的消息状态更新为已读
      const updated = existing.map((msg) => {
        if (msg.status === 'SENT' || msg.status === 'DELIVERED') {
          return { ...msg, status: 'READ' as const, isRead: true };
        }
        return msg;
      });

      newMessages.set(conversationId, updated);
      return { messages: newMessages };
    }),
}));

export default useMessageStore;
