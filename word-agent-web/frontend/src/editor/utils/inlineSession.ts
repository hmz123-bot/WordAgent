/**
 * inlineSession — 划词浮窗「多轮迭代润色」的会话与版本模型。
 *
 * 单轮润色的问题：每次点「重试」都用完全相同的参数重新请求，模型不知道上一版
 * 哪里不好，容易产出雷同结果；也无法在上一版基础上继续追加要求。
 *
 * 这里把一次划词操作抽象成一个「会话」：
 *   原文 → 第 1 版 → （追加指令）第 2 版 → （换一版）第 3 版 …
 * 每一版都是一个 PolishVersion，可来回切换、对比原文、任选一版接受。
 * 送给模型时会还原成 user / assistant 交替的多轮消息，使其真正"记得"迭代过程。
 */

export type AiOperation = 'polish' | 'translate' | 'fix-grammar' | 'continue' | 'rewrite';

/** 一版结果的产生方式 */
export type VersionKind =
  /** 首轮：直接对原文执行操作 */
  | 'initial'
  /** 追加指令：在上一版基础上继续修改 */
  | 'refine'
  /** 换一版：对同样的要求重新给一版明显不同的表达 */
  | 'retry';

export interface PolishVersion {
  id: string;
  /** 该版本的完整文本 */
  text: string;
  op: AiOperation;
  kind: VersionKind;
  /** 该轮用户提出的要求（首轮可能为空） */
  instruction: string;
  /** 展示用短标签，如「润色·正式」「更简洁」「换一版」 */
  label: string;
  createdAt: number;
}

/** 送给模型的一轮对话 */
export interface InlineTurn {
  kind: VersionKind;
  instruction: string;
  output: string;
}

/** 结果卡里的「继续润色」快捷指令 */
export const REFINE_PRESETS: { id: string; label: string; instruction: string }[] = [
  {
    id: 'shorter',
    label: '更简洁',
    instruction: '在保留全部关键信息的前提下进一步精简，删除冗余修饰与重复表达。',
  },
  {
    id: 'formal',
    label: '更正式',
    instruction: '提升正式度与书面感，避免口语化词汇与随意的语气。',
  },
  {
    id: 'plain',
    label: '更通俗',
    instruction: '降低阅读门槛，改用平实易懂的说法，拆开长句，避免生僻词。',
  },
  {
    id: 'detail',
    label: '更详细',
    instruction: '适当补充必要的细节与依据使表达更充分，但不得编造事实。',
  },
  {
    id: 'vivid',
    label: '更生动',
    instruction: '增强画面感与感染力，适当使用具体描写，但不夸张失真。',
  },
  {
    id: 'strict',
    label: '更严谨',
    instruction: '增强逻辑严密性与用词准确性，消除歧义与绝对化表述。',
  },
];

let seq = 0;
function nextId(): string {
  seq += 1;
  return `v_${Date.now().toString(36)}_${seq}`;
}

const OP_LABELS: Record<AiOperation, string> = {
  polish: '润色',
  translate: '翻译',
  'fix-grammar': '语法',
  continue: '续写',
  rewrite: '改写',
};

const TONE_LABELS: Record<string, string> = {
  natural: '自然',
  formal: '正式',
  concise: '简洁',
  lively: '活泼',
  academic: '学术',
};

const LANG_LABELS: Record<string, string> = {
  auto: '自动',
  zh: '中文',
  en: '英语',
  ja: '日语',
  ko: '韩语',
  fr: '法语',
  de: '德语',
  es: '西班牙语',
};

/** 生成一版结果的展示标签 */
export function buildVersionLabel(
  op: AiOperation,
  kind: VersionKind,
  instruction: string,
  options?: { targetLang?: string; tone?: string },
): string {
  if (kind === 'retry') return '换一版';
  if (kind === 'refine') {
    const preset = REFINE_PRESETS.find((p) => p.instruction === instruction);
    if (preset) return preset.label;
    const s = instruction.trim();
    return s.length > 8 ? s.slice(0, 8) + '…' : s || '再优化';
  }
  const base = OP_LABELS[op] || '结果';
  if (op === 'polish' && options?.tone) return `${base}·${TONE_LABELS[options.tone] || options.tone}`;
  if (op === 'translate' && options?.targetLang) {
    return `${base}·${LANG_LABELS[options.targetLang] || options.targetLang}`;
  }
  if (op === 'rewrite' && instruction) {
    const s = instruction.trim();
    return `${base}·${s.length > 6 ? s.slice(0, 6) + '…' : s}`;
  }
  return base;
}

export function createVersion(params: {
  text: string;
  op: AiOperation;
  kind: VersionKind;
  instruction: string;
  options?: { targetLang?: string; tone?: string };
}): PolishVersion {
  return {
    id: nextId(),
    text: params.text,
    op: params.op,
    kind: params.kind,
    instruction: params.instruction,
    label: buildVersionLabel(params.op, params.kind, params.instruction, params.options),
    createdAt: Date.now(),
  };
}

/** 保留的历史轮数上限（首轮始终保留，避免模型丢失原始要求） */
const MAX_HISTORY_TURNS = 5;

/**
 * 把版本列表还原成送给模型的多轮对话。
 * 轮数过多时只保留「首轮 + 最近若干轮」，避免 token 无限增长。
 */
export function versionsToTurns(versions: PolishVersion[]): InlineTurn[] {
  const turns: InlineTurn[] = versions.map((v) => ({
    kind: v.kind,
    instruction: v.instruction,
    output: v.text,
  }));
  if (turns.length <= MAX_HISTORY_TURNS) return turns;
  return [turns[0], ...turns.slice(-(MAX_HISTORY_TURNS - 1))];
}
