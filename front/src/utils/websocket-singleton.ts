import { tokenManager } from './token';
import { JSONBigString } from './json';

/** WebSocket事件类型 */
export type WSEventType =
  | 'NEW_MESSAGE'
  | 'SEND_SUCCESS'
  | 'SEND_FAIL'
  | 'MESSAGES_READ'
  | 'UNREAD_COUNT'
  | 'NEW_NOTIFICATION'
  | 'TYPING'
  | 'MESSAGE_RECALLED'
  | 'PONG'
  | 'ERROR'
  | 'FORCE_LOGOUT'; // 强制退出（管理员禁用账号）

/** WebSocket事件 */
export interface WSEvent {
  type: WSEventType;
  data: any;
}

/** 事件处理器 */
type WSEventHandler = (event: WSEvent) => void;

/**
 * WebSocket全局单例管理类
 *
 * 特点：
 * 1. 全局只有一个连接，页面切换不会断开
 * 2. 自动重连（指数退避）
 * 3. 心跳保活（30秒）
 * 4. 离线消息队列
 * 5. 消息确认机制
 */
export class WebSocketSingleton {
  private static instance: WebSocketSingleton;

  private ws: WebSocket | null = null;
  private handlers: Map<WSEventType, WSEventHandler[]> = new Map();
  private reconnectAttempts = 0;
  private maxReconnectAttempts = 10;
  private reconnectDelay = 1000;
  private heartbeatTimer: ReturnType<typeof setInterval> | null = null;
  private isConnecting = false;
  private isManualClose = false;
  private token: string | null = null;
  private messageQueue: any[] = []; // 离线消息队列

  /** 私有构造函数，防止外部实例化 */
  private constructor() {}

  /** 获取单例实例 */
  static getInstance(): WebSocketSingleton {
    if (!WebSocketSingleton.instance) {
      WebSocketSingleton.instance = new WebSocketSingleton();
    }
    return WebSocketSingleton.instance;
  }

  /** 连接WebSocket */
  connect() {
    // 如果已经连接或正在连接，跳过
    if (this.ws?.readyState === WebSocket.OPEN || this.isConnecting) {
      console.log('[WebSocket] Already connected or connecting, skipping');
      return;
    }

    this.isConnecting = true;
    this.isManualClose = false;
    this.token = tokenManager.getToken();

    if (!this.token) {
      console.warn('[WebSocket] No token available');
      this.isConnecting = false;
      return;
    }

    // 通过网关连接，使用当前host
    const protocol = window.location.protocol === 'https:' ? 'wss:' : 'ws:';
    const host = window.location.host;
    const url = `${protocol}//${host}/ws/message?token=${this.token}`;
    console.log('[WebSocket] Connecting to:', url);

    try {
      this.ws = new WebSocket(url);
      this.setupEventHandlers();
    } catch (error) {
      console.error('[WebSocket] Failed to create WebSocket:', error);
      this.isConnecting = false;
      this.attemptReconnect();
    }
  }

  /** 设置事件处理器 */
  private setupEventHandlers() {
    if (!this.ws) return;

    this.ws.onopen = () => {
      console.log('[WebSocket] Connected successfully!');
      this.isConnecting = false;
      this.reconnectAttempts = 0;
      this.startHeartbeat();
      this.flushMessageQueue(); // 发送离线消息
    };

    this.ws.onmessage = (event) => {
      try {
        // 与 axios 解析一致：雪花 ID 转字符串，避免与 API 返回的 ID 类型不一致
        const data = JSONBigString.parse(event.data);
        this.handleMessage(data);
      } catch (error) {
        console.error('[WebSocket] Failed to parse message:', error);
      }
    };

    this.ws.onclose = (event) => {
      console.log('[WebSocket] Connection closed:', event.code, event.reason);
      this.isConnecting = false;
      this.stopHeartbeat();
      this.ws = null;

      // 非主动关闭时尝试重连
      if (!this.isManualClose && event.code !== 1000) {
        this.attemptReconnect();
      }
    };

    this.ws.onerror = (error) => {
      console.error('[WebSocket] Connection error:', error);
      this.isConnecting = false;
    };
  }

  /** 断开连接 */
  disconnect() {
    this.isManualClose = true;
    this.stopHeartbeat();
    this.reconnectAttempts = this.maxReconnectAttempts;
    if (this.ws) {
      this.ws.close(1000, 'User disconnected');
      this.ws = null;
    }
  }

  /** 发送消息 */
  send(data: Record<string, unknown>) {
    if (this.ws?.readyState === WebSocket.OPEN) {
      console.log('[WebSocket] Sending:', data);
      this.ws.send(JSON.stringify(data));
    } else {
      console.warn('[WebSocket] Not connected, queuing message');
      this.messageQueue.push(data);
    }
  }

  /** 发送聊天消息 */
  sendMessage(
    conversationId: string,
    content: string,
    contentType = 'TEXT',
    msgType = 'TEXT',
    extra?: { mediaUrl?: string; fileName?: string; fileSize?: number },
  ) {
    this.send({
      type: 'SEND_MESSAGE',
      data: {
        conversationId,
        msgType,
        contentType,
        content,
        ...extra,
      },
    });
  }

  /** 标记已读 */
  markRead(conversationId: string) {
    this.send({
      type: 'MARK_READ',
      data: { conversationId },
    });
  }

  /** 发送正在输入状态 */
  sendTyping(conversationId: string) {
    this.send({
      type: 'TYPING',
      data: { conversationId },
    });
  }

  /** 撤回消息 */
  recallMessage(conversationId: string, messageId: string) {
    this.send({
      type: 'RECALL_MESSAGE',
      data: { conversationId, messageId },
    });
  }

  /** 注册事件处理器 */
  on(eventType: WSEventType, handler: WSEventHandler) {
    if (!this.handlers.has(eventType)) {
      this.handlers.set(eventType, []);
    }
    this.handlers.get(eventType)!.push(handler);
  }

  /** 移除事件处理器 */
  off(eventType: WSEventType, handler: WSEventHandler) {
    const handlers = this.handlers.get(eventType);
    if (handlers) {
      const index = handlers.indexOf(handler);
      if (index > -1) {
        handlers.splice(index, 1);
      }
    }
  }

  /** 获取连接状态 */
  get isConnected(): boolean {
    return this.ws?.readyState === WebSocket.OPEN;
  }

  /** 处理消息 */
  private handleMessage(event: WSEvent) {
    const handlers = this.handlers.get(event.type);
    if (handlers) {
      handlers.forEach((handler) => handler(event));
    }
  }

  /** 开始心跳 */
  private startHeartbeat() {
    this.stopHeartbeat();
    this.heartbeatTimer = setInterval(() => {
      this.send({ type: 'PING' });
    }, 30000); // 30秒
  }

  /** 停止心跳 */
  private stopHeartbeat() {
    if (this.heartbeatTimer) {
      clearInterval(this.heartbeatTimer);
      this.heartbeatTimer = null;
    }
  }

  /** 尝试重连 */
  private attemptReconnect() {
    if (this.reconnectAttempts >= this.maxReconnectAttempts) {
      console.log('[WebSocket] Max reconnect attempts reached');
      return;
    }

    this.reconnectAttempts++;
    const delay = this.reconnectDelay * Math.pow(2, this.reconnectAttempts - 1);
    console.log(`[WebSocket] Reconnecting in ${delay}ms (attempt ${this.reconnectAttempts})`);

    setTimeout(() => {
      this.connect();
    }, delay);
  }

  /** 发送离线消息队列 */
  private flushMessageQueue() {
    while (this.messageQueue.length > 0) {
      const data = this.messageQueue.shift();
      if (this.ws?.readyState === WebSocket.OPEN) {
        this.ws.send(JSON.stringify(data));
      }
    }
  }
}

// 导出单例
export const wsManager = WebSocketSingleton.getInstance();
