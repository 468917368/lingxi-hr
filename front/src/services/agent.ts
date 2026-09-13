import request from '@/utils/request';

/** AI会话信息 */
export interface AiSession {
  sessionId: string;
  lastMessage: string;
  lastRole: string;
  updatedAt: string;
}

/** 创建AI会话 */
export async function createSession(): Promise<string> {
  return request.post('/v1/agent/sessions');
}

/** 删除AI会话 */
export async function deleteSession(sessionId: string): Promise<void> {
  return request.delete(`/v1/agent/sessions/${sessionId}`);
}

/** 停止AI生成 */
export async function stopChat(sessionId: string): Promise<void> {
  return request.post(`/v1/agent/chat/stop?sessionId=${sessionId}`);
}

/** 获取会话历史消息 */
export async function getSessionHistory(sessionId: string): Promise<Array<{ role: string; content: string; createdAt: string }>> {
  return request.get(`/v1/agent/sessions/${sessionId}/history`);
}

/** 获取AI会话列表 */
export async function getSessions(page = 1, size = 20): Promise<{ list: AiSession[]; total: number }> {
  return request.get('/v1/agent/sessions', { params: { page, size } });
}

/**
 * 构建SSE聊天URL
 * 开发环境直接连后端（绕过代理缓冲），生产环境走相对路径
 * token通过Authorization请求头传递，不放在URL中
 */
export function buildChatSSEUrl(sessionId: string, message: string): string {
  const params = new URLSearchParams({ sessionId, message });
  const isDev = window.location.hostname === 'localhost';
  const base = isDev ? 'http://localhost:8080' : '';
  return `${base}/api/v1/agent/chat?${params.toString()}`;
}
