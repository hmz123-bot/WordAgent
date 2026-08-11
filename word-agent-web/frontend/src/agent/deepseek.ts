/**
 * deepseek.ts — 通过 DeepSeek 大模型（OpenAI 兼容接口）流式生成 / 润色文档。
 *
 * 调用方式：浏览器请求相对路径 /ds/chat/completions，由 Vite dev server 的反向代理
 * 转发到 https://api.deepseek.com 并在 server 端注入 API Key（见 vite.config.ts）。
 * 这样 Key 不会落到前端打包产物里（仅适用于 npm run dev 开发态）。
 *
 * 如需在构建产物里直连（不推荐，Key 会暴露给用户）：把 ENDPOINT 改为
 * 'https://api.deepseek.com/chat/completions' 并在请求头注入 Authorization。
 */
import type { GenerateOptions, AgentHandlers, AgentController } from './agentService';
import type { InlineTurn } from '../editor/utils/inlineSession';

const ENDPOINT = '/ds/chat/completions';
const MODEL = 'deepseek-chat';

function estimateTokensLocal(text: string): number {
  return Math.max(1, Math.round((text || '').length / 1.6));
}

/** 去掉模型可能残留的 ```html 围栏，保证写入编辑器的是干净 HTML */
function cleanForRender(s: string): string {
  let t = (s || '').trim();
  t = t.replace(/^```(?:html)?\s*\n?/i, '');
  t = t.replace(/\s*```\s*$/i, '');
  return t;
}

const SYSTEM_PROMPT = `你是一位专业的中文文档写作助手。用户会用中文描述需求，并给出文档类型、篇幅、文体、润色程度。
请据此生成结构清晰、内容具体、可读性强的中文文档。

严格要求：
1. 只输出 HTML 片段，不要输出任何解释性文字，也不要使用 markdown 代码块围栏（禁止出现 \`\`\`）。
2. 章节标题用 <h2>，段落用 <p>，无序列表用 <ul><li>，不要使用 <h1>。
3. 内容要真实、具体、有信息量，避免空话套话与车轱辘话。
4. 根据「篇幅」控制章节数量与详略；根据「文体」调整语气；根据「润色程度」控制精修强度。
5. 若类型为「产品需求文档(PRD)」：必须包含 项目背景、目标用户（给出 3 类角色画像）、功能列表（编号 F1… 每项带一句描述）、验收标准（可量化的 AC）、非功能性需求。
6. 若类型为「邮件」：输出一封完整邮件，保留称呼与落款。
7. 若类型为「提纲」：用 <ul><li> 列出主要章节即可，无需展开。`;

function buildUserPrompt(opts: GenerateOptions): string {
  return `请帮我生成文档。
需求描述：${opts.prompt || '生成一份文档'}
文档类型：${opts.type}
篇幅：${opts.length}
文体：${opts.style}
润色程度：${opts.polish}

请直接输出 HTML 片段（不要代码块围栏）。`;
}

const REFINE_SYSTEM = `你是一位资深中文编辑。用户会给出当前文档的 HTML 与一条修改指令。
请按指令修改文档，输出修改后的【完整 HTML】，保持 <h2>/<p>/<ul><li> 结构。
只输出 HTML，不要解释，不要使用 markdown 代码块围栏。`;

function buildRefinePrompt(currentHtml: string, instruction: string): string {
  return `当前文档 HTML：
${currentHtml}

修改指令：${instruction}

请输出修改后的完整 HTML。`;
}

export type ChatMsg = { role: 'system' | 'user' | 'assistant'; content: string };

/**
 * 通用 SSE 流式对话。
 * @param clean 对模型原始输出做后处理（生成/润色场景需要去掉 ```html 围栏，
 *              聊天场景应保留 Markdown，传恒等函数即可）。
 */
async function streamChat(
  messages: ChatMsg[],
  signal: AbortSignal,
  handlers: AgentHandlers,
  temperature = 0.7,
  clean: (s: string) => string = cleanForRender,
): Promise<void> {
  let res: Response;
  try {
    res = await fetch(ENDPOINT, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ model: MODEL, messages, stream: true, temperature }),
      signal,
    });
  } catch (e: any) {
    if (e?.name === 'AbortError') return;
    handlers.onError?.('网络请求失败：' + (e?.message || '未知错误') + '（请确认开发服务器代理已启动）');
    return;
  }

  if (!res.ok || !res.body) {
    let detail = '';
    try {
      detail = (await res.text()).slice(0, 300);
    } catch {
      /* ignore */
    }
    handlers.onError?.(`DeepSeek 请求失败（${res.status}）${detail ? '：' + detail : ''}`);
    return;
  }

  // 非 SSE 的错误体（如余额不足 / 鉴权失败），直接解析为可读错误
  const ct = res.headers.get('content-type') || '';
  if (ct.includes('application/json')) {
    let parsed: any = null;
    try {
      parsed = await res.json();
    } catch {
      /* ignore */
    }
    if (parsed?.error) {
      handlers.onError?.('DeepSeek 错误：' + (parsed.error.message || JSON.stringify(parsed.error)));
      return;
    }
  }

  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buf = '';
  let full = '';
  let finished = false;

  const flushLine = (line: string) => {
    const trimmed = line.trim();
    if (!trimmed || !trimmed.startsWith('data:')) return;
    const data = trimmed.slice(5).trim();
    if (data === '[DONE]') {
      finished = true;
      return;
    }
    try {
      const json = JSON.parse(data);
      const delta: string = json?.choices?.[0]?.delta?.content || '';
      if (delta) {
        full += delta;
        const render = clean(full);
        if (render) handlers.onToken(delta, render);
      }
    } catch {
      /* 跳过不完整的 JSON 帧 */
    }
  };

  try {
    while (!finished) {
      const { value, done } = await reader.read();
      if (done) break;
      buf += decoder.decode(value, { stream: true });
      let nl: number;
      while ((nl = buf.indexOf('\n')) >= 0) {
        const line = buf.slice(0, nl);
        buf = buf.slice(nl + 1);
        flushLine(line);
        if (finished) break;
      }
    }
    if (buf.trim()) flushLine(buf);
  } catch (e: any) {
    if (e?.name === 'AbortError') return;
    handlers.onError?.('读取流失败：' + (e?.message || '未知错误'));
    return;
  }

  handlers.onDone(clean(full), estimateTokensLocal(full));
}

/** 通过 DeepSeek 流式生成文档 */
export function deepseekGenerate(opts: GenerateOptions, handlers: AgentHandlers): AgentController {
  const ctrl = new AbortController();
  void streamChat(
    [
      { role: 'system', content: SYSTEM_PROMPT },
      { role: 'user', content: buildUserPrompt(opts) },
    ],
    ctrl.signal,
    handlers,
  );
  return { cancel: () => ctrl.abort() };
}

/** 通过 DeepSeek 流式润色当前文档 */
export function deepseekRefine(currentHtml: string, instruction: string, handlers: AgentHandlers): AgentController {
  const ctrl = new AbortController();
  void streamChat(
    [
      { role: 'system', content: REFINE_SYSTEM },
      { role: 'user', content: buildRefinePrompt(currentHtml, instruction) },
    ],
    ctrl.signal,
    handlers,
  );
  return { cancel: () => ctrl.abort() };
}

// ============ 划词浮窗（Inline Assistant）专用编辑 ============

export type InlineOp = 'polish' | 'translate' | 'fix-grammar' | 'continue' | 'rewrite';

/** 划词编辑的可选参数 */
export interface InlineEditOptions {
  /** 翻译目标语言：zh/en/ja/ko/fr/de/es/auto（自动判断源语言） */
  targetLang?: string;
  /** 润色语气：natural/formal/concise/lively/academic */
  tone?: string;
  /** 选中文字之前的正文（用于续写等保持连贯） */
  contextBefore?: string;
  /** 选中文字之后的正文 */
  contextAfter?: string;
  /**
   * 多轮迭代润色的历史（按时间顺序）。传入后会还原成 user/assistant 交替的对话，
   * 让模型"记得"自己上一版写了什么、用户又提了什么要求。
   */
  history?: InlineTurn[];
  /** 换一版：要求给出与上一版明显不同的表达（用于「重试」） */
  variation?: boolean;
  /** 采样温度；不传时普通生成 0.7、换一版 1.1 */
  temperature?: number;
}

const LANG_NAMES: Record<string, string> = {
  zh: '简体中文',
  en: '英语',
  ja: '日语',
  ko: '韩语',
  fr: '法语',
  de: '德语',
  es: '西班牙语',
  auto: '合适的语言（请自动判断源语言）',
};

const TONE_NAMES: Record<string, string> = {
  natural: '自然流畅',
  formal: '正式严谨',
  concise: '简洁明了',
  lively: '活泼生动',
  academic: '学术规范',
};

/**
 * 每种操作对应的系统提示词：输出纯文本，不要解释 / 不要 markdown 代码块 / 不要 HTML 标签。
 * 支持按 targetLang / tone 动态生成。
 */
function buildInlineSystemBase(op: InlineOp, opts: InlineEditOptions): string {
  switch (op) {
    case 'polish': {
      const tone = TONE_NAMES[opts.tone || 'natural'] || TONE_NAMES.natural;
      return (
        '你是专业的中文编辑。请优化下面选中的文字表达，使其更' +
        tone +
        '，并保留原意与核心信息。' +
        '只返回润色后的文本本身，不要任何解释，不要使用 markdown 代码块，不要加前缀或引号。'
      );
    }
    case 'translate': {
      const lang = LANG_NAMES[opts.targetLang || 'auto'] || LANG_NAMES.auto;
      return (
        '你是翻译。请将下面选中的文字翻译为' +
        lang +
        '。只返回译文文本本身，不要解释，不要使用代码块，不要加引号。' +
        '若原文已是目标语言，请返回通顺、得体的对应表达。'
      );
    }
    case 'fix-grammar':
      return (
        '你是严谨的中文校对。请修正下面选中文字中的语法错误、错别字、标点与语病。' +
        '只返回修正后的文本本身，不要解释，不要使用代码块，不要加引号。'
      );
    case 'continue':
      return (
        '下面是一段文字（可能附带其前后文）。请顺着它的主题、语气与风格继续写作，' +
        '生成一段自然连贯的续写内容。只返回续写的文本（不要重复原文、不要解释、不要使用代码块）。'
      );
    case 'rewrite':
      return (
        '请按用户给出的指令改写下面的文字。只返回改写后的文本本身，不要解释，不要使用代码块，不要加引号。'
      );
  }
}

/** 多轮迭代时补充到系统提示词末尾的约束 */
const MULTI_TURN_NOTE =
  '\n\n这是一次多轮迭代：用户会在你上一版结果的基础上继续提要求。' +
  '每次只返回【当前最新一版的完整文本】本身——不要解释、不要罗列修改点、' +
  '不要输出"修改后："之类前缀、不要同时给出多个版本。' +
  '每一版都必须是可以直接替换进文档的成品，且保留原文的关键信息与事实，不得编造。';

/** 系统提示词：单轮用基础版，多轮时追加迭代约束 */
function buildInlineSystem(op: InlineOp, opts: InlineEditOptions): string {
  const base = buildInlineSystemBase(op, opts);
  return opts.history && opts.history.length > 0 ? base + MULTI_TURN_NOTE : base;
}

/** 追加指令的固定前缀 */
const FOLLOW_UP_PREFIX = '请在你上一版结果的基础上继续修改：\n';

/** 「换一版」提示：明确告诉模型上一版被否决，需要换思路 */
const VARIATION_PROMPT =
  '上一版我不满意。请重新给出一版：在句式结构、措辞与展开方式上与上一版有明显区别，' +
  '但必须保持原意与信息完整。只返回新版文本本身。';

/** 构造用户消息：把前后文拼到待处理文字之前，让模型感知语境（尤其续写） */
function buildInlineUser(
  op: InlineOp,
  selectedText: string,
  instruction: string,
  opts: InlineEditOptions,
): string {
  const ctxParts: string[] = [];
  if (opts.contextBefore) ctxParts.push('【前文】\n' + opts.contextBefore);
  if (opts.contextAfter) ctxParts.push('【后文】\n' + opts.contextAfter);
  const ctx = ctxParts.length ? ctxParts.join('\n\n') + '\n\n' : '';

  if (op === 'continue') {
    return ctx + '【当前选中/最后一段】\n' + selectedText + '\n\n请续写：';
  }
  if (op === 'rewrite') {
    return (
      ctx +
      '改写指令：' +
      (instruction || '改写这段文字，使其更通顺') +
      '\n\n待改写的文字：\n' +
      selectedText
    );
  }
  return ctx + selectedText;
}

/**
 * 构造完整的消息序列。
 *
 * - 无历史：system + user（原文）——即传统单轮。
 * - 有历史：system + 首轮 user + assistant(第1版) + user(追加要求) + assistant(第2版) … + 本次要求
 *   这样模型能看到完整迭代脉络，「再简洁一点」才有明确的作用对象。
 */
function buildInlineMessages(
  op: InlineOp,
  sourceText: string,
  instruction: string,
  opts: InlineEditOptions,
): ChatMsg[] {
  const history = opts.history ?? [];
  const msgs: ChatMsg[] = [{ role: 'system', content: buildInlineSystem(op, opts) }];

  if (history.length === 0) {
    msgs.push({ role: 'user', content: buildInlineUser(op, sourceText, instruction, opts) });
    return msgs;
  }

  history.forEach((turn, i) => {
    if (i === 0) {
      // 首轮永远携带原文与上下文
      msgs.push({ role: 'user', content: buildInlineUser(op, sourceText, turn.instruction, opts) });
    } else if (turn.kind === 'retry') {
      msgs.push({ role: 'user', content: VARIATION_PROMPT });
    } else {
      msgs.push({ role: 'user', content: FOLLOW_UP_PREFIX + turn.instruction });
    }
    msgs.push({ role: 'assistant', content: turn.output });
  });

  msgs.push({
    role: 'user',
    content: opts.variation
      ? VARIATION_PROMPT
      : FOLLOW_UP_PREFIX + (instruction || '请再优化一版，使表达更好。'),
  });
  return msgs;
}

/**
 * 划词浮窗的 AI 编辑：针对选中文字做 润色 / 翻译 / 语法修正 / 续写 / 按指令改写。
 * 支持多轮迭代——传入 options.history 即可在上一版结果上继续打磨。
 * 流式返回纯文本（已自动去除 ```html 等残留围栏）。
 */
export function deepseekInlineEdit(
  selectedText: string,
  operation: InlineOp,
  instruction: string,
  handlers: AgentHandlers,
  options: InlineEditOptions = {},
): AgentController {
  const ctrl = new AbortController();
  // 「换一版」需要更高的随机性，否则模型极易复读上一版
  const temperature = options.temperature ?? (options.variation ? 1.1 : 0.7);
  void streamChat(
    buildInlineMessages(operation, selectedText, instruction, options),
    ctrl.signal,
    handlers,
    temperature,
  );
  return { cancel: () => ctrl.abort() };
}

// ============ AI 助手侧边栏：通用对话 ============

/**
 * AI 助手侧边栏的通用对话流式接口。
 * 与生成/润色不同，这里保留模型输出的 Markdown（不去除代码围栏），
 * 由上层负责把 Markdown 安全地渲染成 UI。
 */
export function deepseekChat(
  messages: ChatMsg[],
  handlers: AgentHandlers,
  temperature = 0.7,
): AgentController {
  const ctrl = new AbortController();
  void streamChat(
    messages,
    ctrl.signal,
    handlers,
    temperature,
    (s) => s, // 保留 Markdown，不清洗
  );
  return { cancel: () => ctrl.abort() };
}
