import { useState, useRef, useCallback } from 'react';
import {
  chatStream,
  type ChatMessage,
  type ChatDocContext,
  type ChatController,
} from '../../agent/chatService';

export type { ChatMessage };

/**
 * useAgentTools — AI 助手侧边栏的对话状态管理。
 *
 * 工作流：
 * 1. 用户在侧边聊天面板发消息（或点快速指令）。
 * 2. 把「当前文档正文 + 完整多轮历史」交给 chatService。
 * 3. chatService 走 DeepSeek 真实模型（或本地模拟），流式把回答写回最后一条 assistant 消息。
 * 4. 用户可随时「停止」当前生成，或「清空」整段对话。
 *
 * 已移除原版对 /api/v2/agent 后端的依赖（该后端在本纯前端原型中不存在，会导致 AI 助手永远报错）。
 */

export function useAgentTools() {
  const [messages, setMessages] = useState<ChatMessage[]>([]);
  const [isProcessing, setProcessing] = useState(false);
  const ctrlRef = useRef<ChatController | null>(null);

  /** 把对话数组里最后一条（正在流式生成的 assistant）消息内容替换为 full */
  const replaceLast = (prev: ChatMessage[], content: string, mock?: boolean): ChatMessage[] => {
    if (prev.length === 0) return prev;
    const copy = prev.slice();
    const last = copy[copy.length - 1];
    copy[copy.length - 1] = { ...last, content, mock: mock ?? last.mock };
    return copy;
  };

  const sendMessage = useCallback(
    (text: string, doc?: ChatDocContext) => {
      const userMsg: ChatMessage = { role: 'user', content: text, timestamp: Date.now() };
      const history = [...messages, userMsg];
      // 立即把用户消息与一条空的 assistant 占位推上去，后续由流式填充
      setMessages([...history, { role: 'assistant', content: '', timestamp: Date.now() }]);
      setProcessing(true);
      ctrlRef.current = chatStream(history, doc ?? {}, {
        onToken: (_delta, full) => setMessages((prev) => replaceLast(prev, full, false)),
        onDone: (full, mock) => {
          setMessages((prev) => replaceLast(prev, full, mock));
          setProcessing(false);
          ctrlRef.current = null;
        },
        onError: (msg) => {
          setMessages((prev) => replaceLast(prev, `⚠️ ${msg}`, false));
          setProcessing(false);
          ctrlRef.current = null;
        },
      });
    },
    [messages],
  );

  /** 停止当前生成（已产出的部分会保留为一条完整 assistant 消息） */
  const stop = useCallback(() => {
    ctrlRef.current?.cancel();
    ctrlRef.current = null;
    setProcessing(false);
  }, []);

  /** 清空整段对话 */
  const clearConversation = useCallback(() => {
    ctrlRef.current?.cancel();
    ctrlRef.current = null;
    setMessages([]);
    setProcessing(false);
  }, []);

  return { messages, isProcessing, sendMessage, stop, clearConversation };
}
