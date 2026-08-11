import { useState, useRef, useCallback } from 'react';

/**
 * useStreamingAI — SSE 流式 AI 调用 Hook（结构化截断版）。
 *
 * 后端 SSE 协议：
 *   event: start       → 连接建立
 *   event: token       → 逐 token 到达
 *   event: done        → 流结束，含 token 数
 *   event: error       → 异常
 *
 * 前端通过 collectStructuredContext() 收集 blocks[]，
 * 后端 ContextTruncator 做结构化截断后逐 token 推送。
 */

const API_BASE = '/api/v2/ai';

interface StreamingState {
  isStreaming: boolean;
  streamedText: string;
  tokensSoFar: number;
  finished: boolean;
  error: string | null;
}

export function useStreamingAI() {
  const [state, setState] = useState<StreamingState>({
    isStreaming: false,
    streamedText: '',
    tokensSoFar: 0,
    finished: false,
    error: null,
  });

  const abortRef = useRef<AbortController | null>(null);
  const textRef = useRef<string>('');

  const onChunkRef = useRef<((delta: string, fullText: string) => void) | null>(null);
  const onDoneRef = useRef<((fullText: string, tokensUsed: number) => void) | null>(null);

  /**
   * 发起流式 AI 调用
   * @param operation  polish/continue/translate/summarize/rewrite/fix-grammar/qa
   * @param body       结构化请求体 (operation + blocks[] + selectedParaId + instruction 等)
   */
  const startStreaming = useCallback(async (
    operation: string,
    body: Record<string, any>,
  ) => {
    abortRef.current?.abort();
    abortRef.current = new AbortController();
    textRef.current = '';

    setState({
      isStreaming: true,
      streamedText: '',
      tokensSoFar: 0,
      finished: false,
      error: null,
    });

    try {
      const resp = await fetch(`${API_BASE}/stream`, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('auth_token') || ''}`,
        },
        body: JSON.stringify({ ...body, operation }),
        signal: abortRef.current.signal,
      });

      if (!resp.ok) {
        throw new Error(`AI 服务错误: ${resp.status}`);
      }

      if (!resp.body) throw new Error('响应体为空');

      const reader = resp.body.getReader();
      const decoder = new TextDecoder();
      let buffer = '';

      while (true) {
        const { done, value } = await reader.read();
        if (done) break;

        buffer += decoder.decode(value, { stream: true });

        // SSE 帧解析: event: X\ndata: Y\n\n
        while (buffer.includes('\n\n')) {
          const idx = buffer.indexOf('\n\n');
          const frame = buffer.substring(0, idx);
          buffer = buffer.substring(idx + 2);

          let eventType = '';
          let eventData = '';

          for (const line of frame.split('\n')) {
            if (line.startsWith('event:')) eventType = line.substring(6).trim();
            else if (line.startsWith('data:')) eventData = line.substring(5).trim();
          }

          if (eventType === 'token') {
            const token = decodeSSEString(eventData);
            textRef.current += token;
            setState((prev) => ({
              ...prev,
              streamedText: textRef.current,
            }));
            onChunkRef.current?.(token, textRef.current);
          } else if (eventType === 'done') {
            try {
              const info = JSON.parse(eventData);
              setState((prev) => ({
                ...prev,
                finished: true,
                isStreaming: false,
                tokensSoFar: info.tokens || 0,
              }));
              onDoneRef.current?.(textRef.current, info.tokens || 0);
            } catch {
              setState((prev) => ({ ...prev, finished: true, isStreaming: false }));
              onDoneRef.current?.(textRef.current, 0);
            }
          } else if (eventType === 'error') {
            const msg = eventData.replace(/^"/, '').replace(/"$/, '');
            setState((prev) => ({
              ...prev,
              isStreaming: false,
              error: msg,
            }));
          }
        }
      }
    } catch (err: any) {
      if (err.name === 'AbortError') return;
      setState((prev) => ({
        ...prev,
        isStreaming: false,
        error: err.message || 'AI 调用失败',
      }));
    }
  }, []);

  /** 同步 AI 调用（非流式） */
  const callSync = useCallback(async (
    operation: string,
    body: Record<string, any>,
  ): Promise<{ operation: string; response: string }> => {
    const resp = await fetch(`${API_BASE}/sync`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${localStorage.getItem('auth_token') || ''}`,
      },
      body: JSON.stringify({ ...body, operation }),
    });

    if (!resp.ok) throw new Error(`AI 服务错误: ${resp.status}`);
    return resp.json();
  }, []);

  const cancelStream = useCallback(() => {
    abortRef.current?.abort();
    setState((prev) => ({
      ...prev,
      isStreaming: false,
      error: null,
    }));
  }, []);

  const onChunk = useCallback((cb: (delta: string, fullText: string) => void) => {
    onChunkRef.current = cb;
  }, []);

  const onDone = useCallback((cb: (fullText: string, tokensUsed: number) => void) => {
    onDoneRef.current = cb;
  }, []);

  return { ...state, startStreaming, callSync, cancelStream, onChunk, onDone };
}

/** 解码 SSE quoted string: "hello\\nworld" → "hello\nworld" */
function decodeSSEString(s: string): string {
  return s
    .replace(/^"/, '').replace(/"$/, '')
    .replace(/\\n/g, '\n')
    .replace(/\\t/g, '\t')
    .replace(/\\"/g, '"')
    .replace(/\\\\/g, '\\');
}
