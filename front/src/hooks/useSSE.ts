import { useState, useCallback, useRef } from 'react';

interface SSECallbacks {
  onProgress?: (data: { step: string; message: string }) => void;
  onChunk?: (data: { content: string }) => void;
  onResult?: (data: { content: string }) => void;
  onDone?: () => void;
  onStopped?: (data: { message: string }) => void;
  onError?: (error: { code: number; message: string }) => void;
}

export function useSSE(callbacks: SSECallbacks) {
  const [loading, setLoading] = useState(false);
  const [content, setContent] = useState('');
  const abortRef = useRef<AbortController | null>(null);
  // 用ref存最新回调
  const cbRef = useRef(callbacks);
  cbRef.current = callbacks;

  const start = useCallback(async (url: string) => {
    // 中止上次请求
    abortRef.current?.abort();

    setLoading(true);
    const token = localStorage.getItem('lingxi_accessToken');
    const ctrl = new AbortController();
    abortRef.current = ctrl;

    console.log('[SSE] Start:', url);

    try {
      const resp = await fetch(url, {
        headers: { 'Authorization': `Bearer ${token}`, 'Accept': 'text/event-stream' },
        signal: ctrl.signal,
      });

      if (!resp.ok) {
        const errText = await resp.text();
        console.error('[SSE] HTTP', resp.status, errText);
        setLoading(false);
        cbRef.current.onError?.({ code: resp.status, message: `HTTP ${resp.status}` });
        return;
      }

      const reader = resp.body?.getReader();
      if (!reader) {
        setLoading(false);
        cbRef.current.onError?.({ code: -1, message: '无法读取响应流' });
        return;
      }

      const decoder = new TextDecoder('utf-8');
      let buffer = '';
      let eventType = '';
      let eventData = '';

      for (;;) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });
        const lines = buffer.split(/\r?\n/);
        buffer = lines.pop() || '';

        for (const line of lines) {
          if (line.startsWith('event:')) {
            eventType = line.slice(6).trim();
          } else if (line.startsWith('data:')) {
            eventData = line.slice(5).trim();
          } else if (line.trim() === '' && eventType && eventData) {
            // 解析到一个完整事件
            const et = eventType;
            const ed = eventData;
            eventType = '';
            eventData = '';

            console.log('[SSE] event=' + et, ed.substring(0, 60));

            try {
              const data = JSON.parse(ed);
              const cb = cbRef.current;

              if (et === 'progress') {
                cb.onProgress?.(data);
              } else if (et === 'chunk') {
                cb.onChunk?.(data);
              } else if (et === 'result') {
                setContent(data.content || '');
                cb.onResult?.(data);
              } else if (et === 'done') {
                setLoading(false);
                cb.onDone?.();
              } else if (et === 'stopped') {
                setLoading(false);
                cb.onStopped?.(data);
              } else if (et === 'error') {
                setLoading(false);
                cb.onError?.(data);
              }
            } catch (parseErr) {
              console.error('[SSE] JSON parse error:', parseErr, ed);
            }
          }
        }
      }

      // 处理流结束后剩余数据
      if (eventType && eventData) {
        try {
          const data = JSON.parse(eventData);
          const cb = cbRef.current;
          if (eventType === 'chunk') cb.onChunk?.(data);
          else if (eventType === 'result') cb.onResult?.(data);
          else if (eventType === 'done') cb.onDone?.();
          else if (eventType === 'stopped') { setLoading(false); cb.onStopped?.(data); }
        } catch {}
      }

      setLoading(false);
    } catch (err: any) {
      if (err.name !== 'AbortError') {
        console.error('[SSE] Error:', err);
        cbRef.current.onError?.({ code: -1, message: err.message || '连接失败' });
      }
      setLoading(false);
    }
  }, []);

  const stop = useCallback(() => {
    abortRef.current?.abort();
    setLoading(false);
  }, []);

  return { loading, content, start, stop };
}
