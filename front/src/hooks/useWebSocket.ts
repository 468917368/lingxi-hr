/**
 * 统一 re-export singleton 版本
 * 避免存在两套 WebSocket 实例导致消息发送/接收不一致
 */
export { useWebSocketEvent, useWebSocketConnect, useWebSocketSend } from './useWebSocket-singleton';
