/**
 * chatService — AI 助手侧边栏的对话内核。
 *
 * 替代原本依赖缺失后端（/api/v2/agent）的 useAgentTools 实现，让侧边栏「AI 助手」
 * 真正可用：
 *   1. 默认走 DeepSeek 真实大模型（经 /ds 代理，与生成/润色同源）。
 *   2. 设置 VITE_AI_MOCK=1 时回退到本地模拟，保证无 Key / 离线也能演示。
 *   3. 把「完整多轮历史 + 当前文档正文」作为上下文喂给模型，回答文档相关问题。
 *   4. 流式输出（onToken / onDone），支持取消（cancel）。
 *
 * 与 agentService / deepseek 的关系：复用 deepseekChat 的 SSE 流式能力，
 * 仅在此处补充「对话场景」的 system prompt 与文档上下文拼接逻辑。
 */

import { deepseekChat, type ChatMsg } from './deepseek';

// 与 agentService 保持一致：VITE_AI_MOCK=1 时回退本地模拟
const USE_MOCK = (import.meta as any).env?.VITE_AI_MOCK === '1';

export interface ChatMessage {
  role: 'user' | 'assistant';
  content: string;
  timestamp: number;
  /** 是否为本地模拟生成（用于在气泡上打标） */
  mock?: boolean;
}

export interface ChatDocContext {
  html?: string;
  title?: string;
  /** 直接给纯文本则优先使用，省去 HTML 解析 */
  plainText?: string;
}

export interface ChatHandlers {
  onToken: (delta: string, full: string) => void;
  onDone: (full: string, mock: boolean) => void;
  onError?: (msg: string) => void;
}

export interface ChatController {
  cancel: () => void;
}

const SYSTEM_PROMPT = `你是一位中文写作助手，正在帮用户处理一篇文档。请用中文回答。

能力边界：
- 若用户要求「总结 / 概括 / 要点」，给出结构化的要点列表。
- 若用户要求「改进 / 建议 / 优化」，给出具体可落地的写法建议，必要时直接给出改写后的片段。
- 若用户要求「续写 / 接着写」，给出自然连贯、承接上文的续写文本。
- 若用户就文档内容提问，基于提供的文档正文作答；不清楚就说不清楚，不要编造事实。
- 若用户要求「翻译 / 润色某句话」，引导其到编辑器内选中文字使用划词浮窗（你在这里拿不到单句选区）。

格式：
- 合理使用 Markdown 排版（列表、加粗、分段），但不要使用 HTML 标签。
- 回答聚焦、克制，不堆砌寒暄，不重复用户的问题。`;

/** 从 HTML 抽取纯文本（用于把文档正文作为上下文） */
export function htmlToText(html?: string): string {
  if (!html) return '';
  if (typeof document === 'undefined') return (html || '').replace(/<[^>]+>/g, ' ');
  const tmp = document.createElement('div');
  tmp.innerHTML = html;
  return (tmp.textContent || '').replace(/\s+/g, ' ').trim();
}

function splitSentences(text: string): string[] {
  return (text || '')
    .split(/(?<=[。！？；\n])/)
    .map((s) => s.replace(/\s+/g, '').trim())
    .filter((s) => s.length > 4);
}

/** 拼接 system + 文档上下文 + 多轮历史 */
export function buildChatMessages(history: ChatMessage[], doc: ChatDocContext): ChatMsg[] {
  const msgs: ChatMsg[] = [{ role: 'system', content: SYSTEM_PROMPT }];
  const docText = (doc.plainText ?? htmlToText(doc.html)).slice(0, 4000);
  if (docText) {
    msgs.push({
      role: 'system',
      content:
        `【当前文档《${doc.title || '未命名文档'}》正文】\n${docText}\n` +
        `（以上是文档内容，请据此回答用户关于该文档的问题；不知道就说不知道。）`,
    });
  }
  for (const m of history) {
    msgs.push({ role: m.role, content: m.content });
  }
  return msgs;
}

// ============ 本地模拟回退 ============

/** 根据意图与文档内容，生成一份本地化的回答（VITE_AI_MOCK=1 或离线时使用） */
function mockReply(history: ChatMessage[], doc: ChatDocContext): string {
  const lastUser = history[history.length - 1]?.content || '';
  const docText = doc.plainText ?? htmlToText(doc.html);
  const title = doc.title || '未命名文档';
  const sentences = splitSentences(docText);
  const t = lastUser;

  if (/(总结|概括|要点|摘要|核心|归纳|提炼)/.test(t)) {
    if (sentences.length === 0) {
      return `当前文档《${title}》还没有正文内容。先在编辑器里写几段，我就能帮你总结要点了。`;
    }
    const picks = sentences.slice(0, Math.min(5, sentences.length));
    return (
      `以下是《${title}》的核心要点：\n\n` +
      picks.map((s, i) => `${i + 1}. ${s}`).join('\n') +
      `\n\n（以上基于当前文档自动提取；需要更聚焦的总结，告诉我你关心的侧重点即可。）`
    );
  }

  if (/(改进|建议|优化|提升|提高|更好|质量|不足)/.test(t)) {
    return (
      `关于《${title}》的改进建议：\n\n` +
      `1. **开头更抓人**：用一句话点明价值或痛点，避免平铺直叙。\n` +
      `2. **结构更清晰**：长文加二、三级小标题，让读者 5 秒抓住骨架。\n` +
      `3. **用事实和数据支撑**：把"很多""重要"换成具体数字或案例。\n` +
      `4. **删冗余**：去掉车轱辘话与自我重复，每段只讲一件事。\n` +
      `5. **收尾给行动**：结尾留一句明确的下一步或结论。\n\n` +
      `需要我直接改写某一段，选中文字用划词浮窗的「润色 / 改写」会更快。`
    );
  }

  if (/(续写|接着写|继续|往下写|展开|补充)/.test(t)) {
    const seed = sentences.length ? sentences[sentences.length - 1] : '本文围绕主题展开';
    return (
      `顺着当前的脉络，可以这样续写：\n\n` +
      `上文提到「${seed.slice(0, 40)}…」，接下来建议从「为什么重要」与「具体怎么做」两个角度展开：` +
      `先给一两个真实场景，再落到可执行的步骤，最后用一句总结收束。` +
      `这样既能承接前文，又自然引出下一节，避免逻辑断层。`
    );
  }

  if (/(润色|改写|通顺|修辞|表达)/.test(t)) {
    return (
      `润色建议：选中正文里想优化的句子，用编辑器内的「润色」浮窗即可逐句改写，` +
      `还能选「自然 / 正式 / 简洁 / 活泼 / 学术」风格。\n\n` +
      `如果是整篇基调调整，也可以告诉我目标读者与语气（例如"写给老板看，更正式"），我给出针对性的改法。`
    );
  }

  if (/(翻译|译)/.test(t)) {
    return (
      `翻译请在正文中**选中文字**，使用划词浮窗的「翻译」功能，可指定目标语言` +
      `（英 / 日 / 韩 / 法 / 德 / 西）。我在这里主要负责文档级问答与总结。`
    );
  }

  // 默认：尝试基于文档回答
  if (docText && sentences.length) {
    const kw = t.replace(/[？?。.，,！!；;：:\s]/g, '').slice(0, 12);
    const related = kw ? sentences.filter((s) => s.includes(kw.slice(0, 4))).slice(0, 3) : [];
    const cite = related.length ? related : sentences.slice(0, 2);
    return (
      `结合《${title}》的内容，可以这么看：\n\n` +
      cite.map((s) => `- ${s}`).join('\n') +
      `\n\n想要更具体的结论，告诉我你关心哪一部分，或选中对应文字让我直接改写。`
    );
  }

  return (
    `我在这里可以帮你做这些事：\n\n` +
    `- **总结全文**：提炼当前文档的核心要点\n` +
    `- **改进建议**：指出可优化的结构与表达\n` +
    `- **续写 / 润色**：给方向与手法（精确改写请用划词浮窗）\n` +
    `- **文档问答**：就当前文档内容回答你的疑问\n\n` +
    `先写一点正文，或直接点上面的快速指令试试吧。`
  );
}

/** 本地模拟的逐字流式输出，营造"正在输入"的体验 */
function streamLocal(full: string, handlers: ChatHandlers, mock: boolean): ChatController {
  let cancelled = false;
  let i = 0;
  let acc = '';
  const step = () => {
    if (cancelled) return;
    if (i >= full.length) {
      handlers.onDone(acc, mock);
      return;
    }
    const chunk = 1 + Math.floor(Math.random() * 3);
    const end = Math.min(full.length, i + chunk);
    const piece = full.slice(i, end);
    acc += piece;
    i = end;
    handlers.onToken(piece, acc);
    const last = piece[piece.length - 1];
    const pause = /[。！？；\n]/.test(last) ? 22 : 8;
    setTimeout(step, pause + Math.random() * 14);
  };
  setTimeout(step, 120);
  return { cancel: () => { cancelled = true; } };
}

/**
 * 发起一轮对话（含完整历史）。
 * @param history 截至本轮的全部消息（含刚加入的用户消息），用于多轮上下文。
 * @param doc 当前文档上下文（html 或 plainText）。
 */
export function chatStream(
  history: ChatMessage[],
  doc: ChatDocContext,
  handlers: ChatHandlers,
): ChatController {
  if (USE_MOCK) {
    try {
      const reply = mockReply(history, doc);
      return streamLocal(reply, handlers, true);
    } catch (e: any) {
      handlers.onError?.(e?.message || '生成失败');
      return { cancel: () => {} };
    }
  }
  // 真实模型：复用 deepseekChat 的 SSE 流式能力，适配 AgentHandlers 回调
  const ctrl = deepseekChat(buildChatMessages(history, doc), {
    onToken: (delta, full) => handlers.onToken(delta, full),
    onDone: (full) => handlers.onDone(full, false),
    onError: (msg) => handlers.onError?.(msg),
  });
  return { cancel: () => ctrl.cancel() };
}
