import { useEffect, useCallback, useRef } from 'react';
import { wsManager, type WSEventType, type WSEvent } from '@/utils/websocket-singleton';

/**
 * WebSocket事件监听Hook
 * @param eventType 事件类型
 * @param handler 事件处理器
 */
export function useWebSocketEvent(eventType: WSEventType, handler: (data: any) => void) {
  const handlerRef = useRef(handler);
  handlerRef.current = handler;

  useEffect(() => {
    const wrappedHandler = (event: WSEvent) => {
      handlerRef.current(event.data);
    };
    wsManager.on(eventType, wrappedHandler);
    return () => {
      wsManager.off(eventType, wrappedHandler);
    };
  }, [eventType]);
}

/**
 * WebSocket连接管理Hook
 * 全局连接，组件卸载时不断开
 */
export function useWebSocketConnect() {
  useEffect(() => {
    // 全局只连接一次
    wsManager.connect();

    // 组件卸载时不断开连接
    // 只有在用户登出时才断开
  }, []);
}

/**
 * 发送消息Hook
 */
export function useWebSocketSend() {
  const sendMessage = useCallback(
    (
      conversationId: string,
      content: string,
      contentType = 'TEXT',
      msgType = 'TEXT',
      extra?: { mediaUrl?: string; fileName?: string; fileSize?: number },
    ) => {
      wsManager.sendMessage(conversationId, content, contentType, msgType, extra);
    },
    [],
  );

  const markRead = useCallback((conversationId: string) => {
    wsManager.markRead(conversationId);
  }, []);

  const sendTyping = useCallback((conversationId: string) => {
    wsManager.sendTyping(conversationId);
  }, []);

  const recallMessage = useCallback((conversationId: string, messageId: string) => {
    wsManager.recallMessage(conversationId, messageId);
  }, []);

  return { sendMessage, markRead, sendTyping, recallMessage };
}
