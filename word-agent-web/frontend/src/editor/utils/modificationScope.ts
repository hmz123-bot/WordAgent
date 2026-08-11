/**
 * modificationScope — 划词浮窗「修改范围」的计算工具。
 *
 * 给定编辑器文档、当前选区、以及用户选择的范围（仅选区 / 当前句 / 当前段 / 全文），
 * 返回两部分：
 *   - sourceText：应该送给 AI 的原文（即该范围内的纯文本）
 *   - applyRange：AI 结果应替换的编辑器精确位置 { from, to }
 *
 * 续写（continue）不使用本工具，因为它永远是「在选区之后插入」。
 */

export type ModificationScope = 'selection' | 'sentence' | 'paragraph' | 'document';

export const SCOPE_OPTIONS: { id: ModificationScope; label: string }[] = [
  { id: 'selection', label: '仅选区' },
  { id: 'sentence', label: '当前句' },
  { id: 'paragraph', label: '当前段' },
  { id: 'document', label: '全文' },
];

// 句子边界字符（中英文句号、叹号、问号、分号、换行、省略号）
const SENTENCE_BOUNDARY = /[。！？!?；;\n…]/;

function isBoundary(ch: string): boolean {
  return SENTENCE_BOUNDARY.test(ch);
}

/** 在整段文本中，找出包含 [start, end] 的句子边界 [s, e]。 */
function expandToSentence(text: string, start: number, end: number): [number, number] {
  let s = 0;
  for (let i = start - 1; i >= 0; i--) {
    if (isBoundary(text[i])) {
      s = i + 1;
      break;
    }
  }
  let e = text.length;
  for (let i = end; i < text.length; i++) {
    if (isBoundary(text[i])) {
      e = i + 1;
      break;
    }
  }
  return [s, e];
}

/**
 * 把「段内文本偏移量」换算成「文档绝对位置」。
 * 通过遍历文本节点累计字符数，正确处理带 mark 的情况。
 */
function textOffsetToDocPos(doc: any, blockStart: number, blockSize: number, offset: number): number {
  let acc = 0;
  let result = blockStart + offset;
  let done = false;
  doc.nodesBetween(blockStart, blockStart + blockSize, (node: any, pos: number) => {
    if (done) return false;
    if (node.isText) {
      const len = node.text ? node.text.length : 0;
      if (acc + len >= offset) {
        result = pos + (offset - acc);
        done = true;
        return false;
      }
      acc += len;
    }
    return true;
  });
  return result;
}

/** 找到包含 pos 的「文本块」（段落/标题/列表项）的边界。 */
function textblockBoundary(doc: any, pos: number): { from: number; to: number; node: any } | null {
  const $pos = doc.resolve(pos);
  let depth = $pos.depth;
  while (depth >= 1) {
    const node = $pos.node(depth);
    if (node && node.isTextblock) {
      return { from: $pos.before(depth), to: $pos.after(depth), node };
    }
    depth--;
  }
  return null;
}

export function resolveModificationScope(
  doc: any,
  selection: { from: number; to: number },
  scope: ModificationScope,
): { sourceText: string; applyRange: { from: number; to: number } } {
  if (scope === 'document') {
    const from = 0;
    const to = doc.content.size;
    return { sourceText: doc.textBetween(from, to, '\n'), applyRange: { from, to } };
  }

  if (scope === 'paragraph') {
    const start = textblockBoundary(doc, selection.from) || {
      from: selection.from,
      to: selection.from,
      node: null,
    };
    const end = textblockBoundary(doc, selection.to) || {
      from: selection.to,
      to: selection.to,
      node: null,
    };
    const from = Math.min(start.from, end.from);
    const to = Math.max(start.to, end.to);
    return { sourceText: doc.textBetween(from, to, '\n'), applyRange: { from, to } };
  }

  if (scope === 'sentence') {
    const block = textblockBoundary(doc, selection.from);
    if (!block || !block.node) {
      return {
        sourceText: doc.textBetween(selection.from, selection.to, '\n'),
        applyRange: { from: selection.from, to: selection.to },
      };
    }
    const blockStart = block.from + 1; // 文本块内容起点
    const blockText = block.node.textContent;
    const selStartOffset = doc.textBetween(blockStart, selection.from, '\n').length;
    const selEndOffset = selStartOffset + doc.textBetween(selection.from, selection.to, '\n').length;
    const [s, e] = expandToSentence(blockText, selStartOffset, selEndOffset);
    const from = textOffsetToDocPos(doc, blockStart, block.node.nodeSize, s);
    const to = textOffsetToDocPos(doc, blockStart, block.node.nodeSize, e);
    return { sourceText: doc.textBetween(from, to, '\n'), applyRange: { from, to } };
  }

  // 默认：仅选区
  return {
    sourceText: doc.textBetween(selection.from, selection.to, '\n'),
    applyRange: { from: selection.from, to: selection.to },
  };
}
