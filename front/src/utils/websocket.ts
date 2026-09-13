/**
 * 统一 re-export singleton 版本
 * 避免存在两套 WebSocket 实例导致消息发送/接收不一致
 */
export { WebSocketSingleton, wsManager } from './websocket-singleton';
export type { WSEventType, WSEvent } from './websocket-singleton';
