import request from '@/utils/request';
import type {
  Conversation,
  CreateConversationRequest,
  Message,
  SendMessageRequest,
  FileUploadResult,
  PageResult,
} from '@/constants/apiTypes';

/** 获取会话列表 */
export async function getConversations(): Promise<Conversation[]> {
  return request.get('/v1/conversations');
}

/** 创建会话 */
export async function createConversation(data: CreateConversationRequest): Promise<string> {
  return request.post('/v1/conversations', data);
}

/** 获取会话详情 */
export async function getConversationDetail(id: string): Promise<Conversation> {
  return request.get(`/v1/conversations/${id}`);
}

/** 获取聊天记录（支持增量加载） */
export async function getMessages(
  conversationId: string,
  page = 1,
  size = 20,
  sinceId?: number,
): Promise<PageResult<Message>> {
  const params: Record<string, any> = { page, size };
  if (sinceId) {
    params.sinceId = sinceId;
  }
  return request.get(`/v1/conversations/${conversationId}/messages`, { params });
}

/** 增量获取新消息（用于轮询或WebSocket断开时补救） */
export async function getNewMessages(
  conversationId: number,
  sinceId: number,
): Promise<Message[]> {
  return request.get(`/v1/conversations/${conversationId}/messages`, {
    params: { sinceId },
  });
}

/** 发送消息 */
export async function sendMessage(
  conversationId: string,
  data: SendMessageRequest,
): Promise<number> {
  return request.post(`/v1/conversations/${conversationId}/messages`, data);
}

/** 删除会话 */
export async function deleteConversation(id: string): Promise<void> {
  return request.delete(`/v1/conversations/${id}`);
}

/** 设置置顶 */
export async function setConversationTop(id: string, isTop: boolean): Promise<void> {
  return request.put(`/v1/conversations/${id}/top`, null, { params: { isTop } });
}

/** 设置免打扰 */
export async function setConversationMuted(id: string, isMuted: boolean): Promise<void> {
  return request.put(`/v1/conversations/${id}/muted`, null, { params: { isMuted } });
}

/** 上传消息文件 */
export async function uploadMessageFile(file: File): Promise<FileUploadResult> {
  const formData = new FormData();
  formData.append('file', file);
  return request.post('/v1/messages/upload', formData, {
    headers: { 'Content-Type': 'multipart/form-data' },
  });
}
