/**
 * Context Collector v2 — 结构化上下文抽取。
 *
 * 比 v1 新增：
 *   1. 标题层级感知（headingLevel 1-6）
 *   2. blocks[] 结构化输出，每条带 paraId / type / headingLevel / charCount
 *   3. 章节边界检测（遇到同级标题视为新章节）
 *   4. context payload 归一化 → Gateway 层做截断决策
 */

export interface ParagraphInfo {
  /** 段落唯一 ID */
  paraId: string;
  /** 段落纯文本 */
  text: string;
  /** 类型：heading / paragraph / list_item / table */
  type: 'heading' | 'paragraph' | 'list_item' | 'table';
  /** 标题层级 1-6，非标题为 0 */
  headingLevel: number;
  /** 字符数（约等于 token 数，中文 1:1 英文 4:1） */
  charCount: number;
  /** 在文档中的位置序号 */
  position: number;
}

export interface StructuredContext {
  /** 用户选中的文本 */
  selection: string;
  /** 选区开头段落的 paraId */
  selectedParaId: string;
  /** 用户的改写指令 */
  instruction?: string;
  /** 问答问题 */
  question?: string;
  /** 结构化的段落数组（发给 Gateway 做截断决策） */
  blocks: ParagraphInfo[];
  /** 旧格式兼容：选区 + 上下文文本 */
  surroundingText: string;
  /** 旧格式兼容：全文文本 */
  fullDocument?: string;
  /** 文档标题 */
  documentTitle?: string;
  /** 写作风格提示 */
  styleHint?: string;
}

/**
 * 从 ProseMirror/Tiptap JSON doc 收集结构化上下文。
 *
 * @param editorDoc  ProseMirror doc JSON 序列化后的完整文档
 * @param selectionRange  选区段落索引 { from, to }，null 表示无选区
 * @param instruction     用户手动输入的改写指令（可选）
 */
export function collectStructuredContext(
  editorDoc: any,
  selectionRange?: { from: number; to: number } | null,
  instruction?: string,
): StructuredContext {
  const ctx: StructuredContext = {
    selection: '',
    selectedParaId: '',
    blocks: [],
    surroundingText: '',
    instruction,
  };

  if (!editorDoc?.content) return ctx;

  // 遍历文档，构建 ParagraphInfo[]（按自然顺序）
  let position = 0;
  for (const block of editorDoc.content) {
    const info = parseBlockInfo(block, position);
    ctx.blocks.push(info);
    position++;
  }

  if (ctx.blocks.length === 0) return ctx;

  // 处理选区
  if (selectionRange && ctx.blocks.length > 0) {
    const { from, to } = selectionRange;
    const clampedFrom = Math.max(0, Math.min(from, ctx.blocks.length - 1));
    const clampedTo = Math.max(clampedFrom, Math.min(to, ctx.blocks.length - 1));

    ctx.selectedParaId = ctx.blocks[clampedFrom]?.paraId || '';
    ctx.selection = ctx.blocks
      .slice(clampedFrom, clampedTo + 1)
      .map((b) => b.text)
      .join('\n');

    // 前后各 3 段拼 surroundingText
    const surroundStart = Math.max(0, clampedFrom - 3);
    const surroundEnd = Math.min(ctx.blocks.length, clampedTo + 4);
    ctx.surroundingText = ctx.blocks
      .slice(surroundStart, surroundEnd)
      .map(formatBlockForContext)
      .join('\n\n');
  } else {
    // 无选区 → 全文
    ctx.fullDocument = ctx.blocks.map(formatBlockForContext).join('\n\n');
    ctx.surroundingText = ctx.blocks.map((b) => b.text).join('\n\n');
    if (ctx.blocks.length > 0) {
      ctx.selectedParaId = ctx.blocks[0].paraId;
    }
  }

  return ctx;
}

/**
 * 从 ProseMirror block JSON 解析为 ParagraphInfo
 */
function parseBlockInfo(block: any, position: number): ParagraphInfo {
  const type = normalizeBlockType(block.type || 'paragraph');
  const headingLevel = extractHeadingLevel(block);
  const paraId = block.attrs?.paraId || `${type}_${position}`;
  const text = collectBlockText(block);

  return {
    paraId,
    text,
    type,
    headingLevel,
    charCount: text.length,
    position,
  };
}

/**
 * 标准化 block type 为 heading / paragraph / list_item / table
 */
function normalizeBlockType(prosemirrorType: string): ParagraphInfo['type'] {
  const t = prosemirrorType.toLowerCase();
  if (t.startsWith('heading') || t === 'head') return 'heading';
  if (t === 'list_item' || t === 'listitem' || t.includes('list_item')) return 'list_item';
  if (t === 'table' || t === 'table_cell' || t === 'table_header') return 'table';
  return 'paragraph';
}

/**
 * 提取标题层级：heading → 1-6，其他 → 0
 */
function extractHeadingLevel(block: any): number {
  const t = (block.type || '').toLowerCase();
  if (t.startsWith('heading')) {
    const level = parseInt(t.replace('heading', ''), 10);
    return level >= 1 && level <= 6 ? level : 1;
  }
  // ProseMirror heading 的 attrs.level
  if (block.attrs?.level && typeof block.attrs.level === 'number') {
    return Math.min(Math.max(block.attrs.level, 1), 6);
  }
  return 0;
}

/**
 * 递归收集 block 内所有文本
 */
function collectBlockText(block: any): string {
  if (!block) return '';
  if (typeof block === 'string') return block;
  if (block.text) return block.text;
  if (block.content && Array.isArray(block.content)) {
    return block.content.map(collectBlockText).join('');
  }
  return '';
}

/**
 * 格式化 block 为上下文文本（标题加 # 前缀）
 */
function formatBlockForContext(b: ParagraphInfo): string {
  if (b.headingLevel > 0) {
    return '#'.repeat(b.headingLevel) + ' ' + b.text;
  }
  return b.text;
}

// ─────────── 向后兼容 v1 API ───────────

export interface EditorContext {
  selection: string;
  surroundingText: string;
  fullDocument?: string;
  paragraphMap: Record<string, string>;
  selectedParaId?: string;
  instruction?: string;
  documentTitle?: string;
  styleHint?: string;
}

/**
 * 兼容旧接口：返回扁平结构
 */
export function collectEditorContext(
  editorDoc: any,
  selectionRange?: { from: number; to: number },
  parentParagraphCount: number = 2,
): EditorContext {
  const structured = collectStructuredContext(editorDoc, selectionRange);
  const paragraphMap: Record<string, string> = {};
  for (const b of structured.blocks) {
    paragraphMap[b.paraId] = b.text;
  }

  return {
    selection: structured.selection,
    selectedParaId: structured.selectedParaId,
    instruction: structured.instruction,
    fullDocument: structured.fullDocument,
    surroundingText: structured.surroundingText,
    paragraphMap,
    documentTitle: structured.documentTitle,
    styleHint: structured.styleHint,
  };
}

// ─────────── 工具函数 ───────────

export function extractPlainTextFromHtml(html: string): string {
  const div = document.createElement('div');
  div.innerHTML = html;
  return div.textContent || div.innerText || '';
}

export function splitIntoParagraphs(text: string): string[] {
  return text.split(/\n\s*\n/).filter((p) => p.trim().length > 0);
}
