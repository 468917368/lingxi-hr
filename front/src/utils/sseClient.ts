import { tokenManager } from './token';

/** 单个 SSE 事件块（event: 与 data: 行） */
interface SSEEventBlock {
  event: string;
  data: string;
}

/** SSE 事件回调 */
interface SSEEventHandlers<TResult = unknown> {
  onProgress?: (data: unknown) => void;
  onResult?: (data: TResult) => void;
  onError?: (data: {
    code?: number;
    errorCode?: number;
    message?: string;
    retryable?: boolean;
    requestId?: string;
    timestamp?: string;
  }) => void;
  onDone?: () => void;
}

export interface SSEFetchOptions<TResult = unknown> extends SSEEventHandlers<TResult> {
  method?: 'GET' | 'POST';
  /** 形如 /v1/mock-interview/generate，客户端自动拼接 /api 前缀走网关 */
  url: string;
  body?: object;
}

/** 逐块解析 SSE 事件块：提取 event: 与 data: 行 */
function parseSSEBlock(block: string): SSEEventBlock | null {
  const lines = block.split(/\r?\n/);
  let event = '';
  let data = '';
  for (const line of lines) {
    if (line.startsWith('event:')) {
      event = line.slice(6).trim();
    } else if (line.startsWith('data:')) {
      data = line.slice(5).trim();
    }
  }
  if (!data) return null;
  return { event: event || 'message', data };
}

/**
 * 基于 fetch + ReadableStream 的 SSE 客户端。
 *
 * 与 EventSource 的区别（模拟面试后端所需）：
 * - 支持 POST（携带 JSON body）
 * - 支持自定义事件名：progress / result / done / error
 * - 支持携带 Authorization: Bearer <token>
 *
 * 返回 { abort }，用于组件卸载 / 重试时取消连接。
 */
export function sseFetch<TResult = unknown>(
  options: SSEFetchOptions<TResult>,
): { abort: () => void } {
  const controller = new AbortController();
  const token = tokenManager.getToken();
  const { method = 'POST', url, body, onProgress, onResult, onError, onDone } = options;

  (async () => {
    let res: Response;
    try {
      res = await fetch(`/api${url}`, {
        method,
        headers: {
          'Content-Type': 'application/json',
          ...(token ? { Authorization: `Bearer ${token}` } : {}),
        },
        body: method === 'POST' ? JSON.stringify(body ?? {}) : undefined,
        signal: controller.signal,
      });
    } catch (err) {
      if ((err as Error)?.name !== 'AbortError') {
        onError?.({ message: (err as Error)?.message || 'SSE连接失败' });
      }
      return;
    }

    // 建流前错误兼容（Codex P0）：后端校验失败（2305/2101/401/403 等）返回普通 Result JSON，
    // 而非 SSE 帧（HTTP 200 或 409/503 + application/json）。按 content-type 区分：
    // - text/event-stream → 正常 SSE 流，走下方逐块解析
    // - 其他（application/json 等）→ 读取 body 解析 code/message 透传给 onError，避免弹窗永久「生成中」
    const contentType = res.headers.get('content-type') || '';
    if (res.ok && res.body && !contentType.includes('text/event-stream')) {
      try {
        const text = await res.text();
        const json = JSON.parse(text);
        if (json && typeof json.code === 'number') {
          onError?.({ code: json.code, message: json.message });
        } else {
          onError?.({ message: json?.message || text || '请求失败' });
        }
      } catch {
        onError?.({ message: '请求失败' });
      }
      return;
    }

    if (!res.ok || !res.body) {
      // 与 utils/request.ts 行为对齐：401 清理登录态并跳转
      if (res.status === 401) {
        tokenManager.clearAll();
        window.location.href = '/login';
        return;
      }
      // 非 200 且非 SSE 的错误响应：尝试读取 body 解析 Result JSON 的业务码
      if (res.body) {
        try {
          const text = await res.text();
          const json = JSON.parse(text);
          if (json && typeof json.code === 'number') {
            onError?.({ code: json.code, message: json.message || `请求失败（HTTP ${res.status}）` });
            return;
          }
        } catch {
          /* 非 JSON 落兜底提示 */
        }
      }
      onError?.({ message: `请求失败（HTTP ${res.status}）` });
      return;
    }

    const reader = res.body.getReader();
    const decoder = new TextDecoder('utf-8');
    let buffer = '';

    try {
      while (true) {
        const { done, value } = await reader.read();
        if (done) break;
        buffer += decoder.decode(value, { stream: true });

        // SSE 事件以空行分隔
        const blocks = buffer.split(/\r?\n\r?\n/);
        buffer = blocks.pop() ?? '';
        for (const block of blocks) {
          const parsed = parseSSEBlock(block);
          if (!parsed) continue;

          let data: unknown = parsed.data;
          try {
            data = JSON.parse(parsed.data);
          } catch {
            /* 保持原始字符串 */
          }

          switch (parsed.event) {
            case 'progress':
              onProgress?.(data);
              break;
            case 'result':
              onResult?.(data as TResult);
              break;
            case 'error':
              onError?.(data as {
                errorCode?: number;
                code?: number;
                message?: string;
                retryable?: boolean;
                requestId?: string;
                timestamp?: string;
              });
              break;
            case 'done':
              onDone?.();
              break;
            default:
              break;
          }
        }
      }
    } catch (err) {
      if ((err as Error)?.name !== 'AbortError') {
        onError?.({ message: (err as Error)?.message || 'SSE连接失败' });
      }
    }
  })();

  return { abort: () => controller.abort() };
}
