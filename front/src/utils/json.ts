import JSONBig from 'json-bigint';

/**
 * 全局 JSON 解析器：大整数（雪花 ID，> 2^53）自动转为字符串，避免精度丢失。
 *
 * 背景：后端实体 Long 型雪花 ID（19 位）超出 JS Number 安全整数范围，
 * 默认 JSON.parse 会四舍五入（如 2085748784725364736 → 2085748784725364700），
 * 导致回传请求时后端查不到记录。json-bigint storeAsString 仅把超范围整数转字符串，
 * 小整数（jobId/score 等）与已是字符串的字段不受影响。
 *
 * 供 axios（utils/request.ts）与 WebSocket（utils/websocket-singleton.ts）共用，
 * 保证所有来源的 ID 类型一致。
 */
export const JSONBigString = JSONBig({ storeAsString: true });
