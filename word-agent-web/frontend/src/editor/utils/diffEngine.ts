import { diff_match_patch as DiffMatchPatch, DIFF_DELETE, DIFF_INSERT, DIFF_EQUAL } from 'diff-match-patch';

/**
 * Diff 引擎 — 字符级文本差异计算。
 *
 * 用于：
 * 1. AI 修改前后对比，生成 diff 序列供 ProseMirror Decoration 使用
 * 2. 逐字符级别的 accept/reject
 */

const dmp = new DiffMatchPatch();

export type DiffChunkType = 'inserted' | 'deleted' | 'unchanged';

export interface DiffChunk {
  type: DiffChunkType;
  text: string;
  startIndex: number;  // 在原文中的位置
  endIndex: number;
}

export interface Suggestion {
  suggestionId: string;
  paragraphId: string;
  originalText: string;
  suggestedText: string;
  diffChunks: DiffChunk[];
  timestamp: number;
}

/**
 * 对两段文本做字符级 diff
 */
export function computeDiff(original: string, modified: string): DiffChunk[] {
  const patches = dmp.diff_main(original, modified);
  dmp.diff_cleanupSemantic(patches);

  const chunks: DiffChunk[] = [];
  let originalPos = 0;

  for (const [op, text] of patches) {
    switch (op) {
      case DIFF_EQUAL:
        chunks.push({
          type: 'unchanged',
          text,
          startIndex: originalPos,
          endIndex: originalPos + text.length,
        });
        originalPos += text.length;
        break;
      case DIFF_DELETE:
        chunks.push({
          type: 'deleted',
          text,
          startIndex: originalPos,
          endIndex: originalPos + text.length,
        });
        originalPos += text.length;
        break;
      case DIFF_INSERT:
        chunks.push({
          type: 'inserted',
          text,
          startIndex: originalPos,
          endIndex: originalPos,
        });
        break;
    }
  }

  return chunks;
}

/**
 * 从 diff chunks 重建"接受"后的文本
 */
export function acceptAllChanges(chunks: DiffChunk[]): string {
  let result = '';
  for (const chunk of chunks) {
    if (chunk.type === 'unchanged' || chunk.type === 'inserted') {
      result += chunk.text;
    }
    // deleted 的不包含
  }
  return result;
}

/**
 * 从 diff chunks 重建"拒绝"后的文本（恢复原样）
 */
export function rejectAllChanges(chunks: DiffChunk[]): string {
  let result = '';
  for (const chunk of chunks) {
    if (chunk.type === 'unchanged' || chunk.type === 'deleted') {
      result += chunk.text;
    }
    // inserted 的不包含
  }
  return result;
}

/**
 * 对单个 diff chunk 做 accept/reject
 * 返回新的文本内容
 */
export function acceptChunk(currentText: string, chunk: DiffChunk, chunks: DiffChunk[]): string {
  // 简化实现：接受单个 chunk = 在当前位置插入 chunk 文本后重建
  // 实际应为精确的逐块 accept/reject
  return acceptAllChanges(chunks);
}

export function rejectChunk(currentText: string, chunk: DiffChunk, chunks: DiffChunk[]): string {
  return rejectAllChanges(chunks);
}

/**
 * 创建 Suggestion 对象
 */
export function createSuggestion(
  paragraphId: string,
  originalText: string,
  aiText: string,
): Suggestion {
  return {
    suggestionId: `sg_${Date.now().toString(36)}_${Math.random().toString(36).substr(2, 6)}`,
    paragraphId,
    originalText,
    suggestedText: aiText,
    diffChunks: computeDiff(originalText, aiText),
    timestamp: Date.now(),
  };
}
