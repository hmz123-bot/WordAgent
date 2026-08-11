/**
 * AI Gateway API 客户端 v2 — 发送结构化 blocks，由后端 ContextTruncator 做截断决策。
 *
 * 规则：前端绝不直连 OpenAI/DeepSeek，所有 AI 请求过这个 Gateway。
 */
import type { StructuredContext } from '../editor/utils/contextCollector';

const BASE = '/api/v2/ai';

interface GatewayConfig {
  authToken?: string;
}

let config: GatewayConfig = {
  authToken: typeof window !== 'undefined' ? localStorage.getItem('auth_token') || undefined : undefined,
};

export function setGatewayConfig(cfg: Partial<GatewayConfig>) {
  config = { ...config, ...cfg };
  if (cfg.authToken) {
    localStorage.setItem('auth_token', cfg.authToken);
  }
}

// === 流式 AI 调用（结构化截断版） ===

/**
 * @param operation     polish / continue / translate / summarize / rewrite / fix-grammar / qa
 * @param ctx           来自 collectStructuredContext() 的结构化上下文
 * @param onChunk       每收到一个 token
 * @param onDone        流结束
 * @param onError       异常
 */
export async function streamAI(
  operation: string,
  ctx: StructuredContext & Record<string, any>,
  onChunk: (delta: string, fullText: string) => void,
  onDone: (fullText: string, tokensUsed: number) => void,
  onError: (error: string) => void,
): Promise<AbortController> {
  const abort = new AbortController();

  try {
    // 构建结构化请求体：operation + blocks[] + selectedParaId + instruction + question
    const body: Record<string, any> = {
      operation,
      blocks: ctx.blocks,
      selectedParaId: ctx.selectedParaId,
      originalText: ctx.selection || ctx.surroundingText,
    };
    if (ctx.instruction) body.instruction = ctx.instruction;
    if (ctx.question) body.question = ctx.question;
    if (ctx.fullDocument) body.fullDoc = ctx.fullDocument;

    const resp = await fetch(`${BASE}/stream`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        Authorization: `Bearer ${config.authToken || ''}`,
      },
      body: JSON.stringify(body),
      signal: abort.signal,
    });

    if (!resp.ok) {
      onError(`AI 服务错误 ${resp.status}`);
      return abort;
    }

    if (!resp.body) {
      onError('响应体为空');
      return abort;
    }

    const reader = resp.body.getReader();
    const decoder = new TextDecoder();
    let buffer = '';
    let fullText = '';
    let tokensUsed = 0;
    let currentEvent = '';

    while (true) {
      const { done, value } = await reader.read();
      if (done) break;

      buffer += decoder.decode(value, { stream: true });

      // SSE 协议解析：event: <type>\ndata: <payload>\n\n
      while (buffer.includes('\n\n')) {
        const idx = buffer.indexOf('\n\n');
        const frame = buffer.substring(0, idx);
        buffer = buffer.substring(idx + 2);

        let eventType = '';
        let eventData = '';

        for (const line of frame.split('\n')) {
          if (line.startsWith('event:')) {
            eventType = line.substring(6).trim();
          } else if (line.startsWith('data:')) {
            eventData = line.substring(5).trim();
          }
        }

        currentEvent = eventType;

        if (eventType === 'token') {
          // data 是 quoted string，去掉首尾引号
          const token = eventData.replace(/^"/, '').replace(/"$/, '')
            .replace(/\\n/g, '\n')
            .replace(/\\t/g, '\t')
            .replace(/\\"/g, '"')
            .replace(/\\\\/g, '\\');
          fullText += token;
          onChunk(token, fullText);
        } else if (eventType === 'done') {
          try {
            const info = JSON.parse(eventData);
            tokensUsed = info.tokens || 0;
          } catch { /* ignore */ }
        } else if (eventType === 'error') {
          const msg = eventData.replace(/^"/, '').replace(/"$/, '');
          onError(msg);
          return abort;
        }
      }
    }

    onDone(fullText, tokensUsed);
  } catch (err: any) {
    if (err.name !== 'AbortError') {
      onError(err.message || '网络错误');
    }
  }

  return abort;
}

// === 同步 AI 调用 ===

export async function callAISync(
  operation: string,
  ctx: StructuredContext & Record<string, any>,
): Promise<{ operation: string; response: string }> {
  const body: Record<string, any> = {
    operation,
    blocks: ctx.blocks,
    selectedParaId: ctx.selectedParaId,
    originalText: ctx.selection || ctx.surroundingText,
  };
  if (ctx.instruction) body.instruction = ctx.instruction;
  if (ctx.question) body.question = ctx.question;
  if (ctx.fullDocument) body.fullDoc = ctx.fullDocument;

  const resp = await fetch(`${BASE}/sync`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.authToken || ''}`,
    },
    body: JSON.stringify(body),
  });

  if (!resp.ok) throw new Error(`AI 服务错误: ${resp.status}`);
  return resp.json();
}

// === 操作列表 ===

export async function fetchOperations() {
  const resp = await fetch(`${BASE}/operations`);
  return resp.json();
}

// === Agent 模式 ===

export async function startAgentConversation(
  message: string,
  conversationId: string | null,
  documentContext?: Record<string, any>,
) {
  const resp = await fetch('/api/v2/agent/conversation', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.authToken || ''}`,
    },
    body: JSON.stringify({ message, conversationId, documentContext }),
  });
  return resp.json();
}

export async function continueAgentConversation(
  conversationId: string,
  toolResults: Array<{ id: string; result: any }>,
  documentContext?: Record<string, any>,
) {
  const resp = await fetch(`/api/v2/agent/conversation/${conversationId}/continue`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      Authorization: `Bearer ${config.authToken || ''}`,
    },
    body: JSON.stringify({ toolResults, documentContext }),
  });
  return resp.json();
}

export async function fetchAgentTools() {
  const resp = await fetch('/api/v2/agent/tools');
  return resp.json();
}

// === 鉴权 ===

export async function requestToken(userId?: string): Promise<string> {
  const resp = await fetch('/api/v2/auth/token', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ userId: userId || 'anonymous' }),
  });
  const data = await resp.json();
  if (data.token) {
    setGatewayConfig({ authToken: data.token });
  }
  return data.token;
}

// === 兼容旧版非结构化 API（内部桥接） ===

/**
 * @deprecated 使用 streamAI() 替代
 */
export async function streamAILegacy(
  operation: string,
  context: Record<string, any>,
  onChunk: (delta: string, fullText: string) => void,
  onDone: (fullText: string, tokensUsed: number) => void,
  onError: (error: string) => void,
): Promise<AbortController> {
  return streamAI(operation, context as StructuredContext & Record<string, any>, onChunk, onDone, onError);
}
