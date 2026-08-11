import { useState, useRef, useCallback } from 'react';
import {
  generateStream,
  refineStream,
  type GenerateOptions,
  type AgentController,
} from './agentService';

/**
 * useAgentWriter — 把 agentService 的流式能力封装成 React 状态。
 * 与现有 useStreamingAI 风格一致，方便上层页面直接消费。
 */
export function useAgentWriter() {
  const [isStreaming, setIsStreaming] = useState(false);
  const [text, setText] = useState('');
  const [tokens, setTokens] = useState(0);
  const [error, setError] = useState<string | null>(null);
  const ctrlRef = useRef<AgentController | null>(null);

  const stop = useCallback(() => {
    ctrlRef.current?.cancel();
    setIsStreaming(false);
  }, []);

  const generate = useCallback(
    (opts: GenerateOptions) => {
      stop();
      setText('');
      setTokens(0);
      setError(null);
      setIsStreaming(true);
      ctrlRef.current = generateStream(opts, {
        onToken: (_delta, full) => setText(full),
        onDone: (full, tk) => {
          setText(full);
          setTokens(tk);
          setIsStreaming(false);
        },
        onError: (m) => {
          setError(m);
          setIsStreaming(false);
        },
      });
    },
    [stop],
  );

  const refine = useCallback(
    (currentHtml: string, instruction: string) => {
      stop();
      setError(null);
      setIsStreaming(true);
      ctrlRef.current = refineStream(currentHtml, instruction, {
        onToken: (_delta, full) => setText(full),
        onDone: (full, tk) => {
          setText(full);
          setTokens(tk);
          setIsStreaming(false);
        },
        onError: (m) => {
          setError(m);
          setIsStreaming(false);
        },
      });
    },
    [stop],
  );

  return { isStreaming, text, tokens, error, generate, refine, stop };
}
