import { useState, useRef, useCallback } from 'react';
import type { Suggestion, DiffChunk } from '../utils/diffEngine';
import { createSuggestion, acceptAllChanges, rejectAllChanges, acceptChunk, rejectChunk } from '../utils/diffEngine';

/**
 * useDiffMark — AI 修改的 accept/reject 状态管理。
 *
 * 两套模式：
 * A. Transaction 式：AI 改完，编辑器进 readonly preview，用户 accept/reject 后恢复
 * B. Diff-mark 式（当前实现）：AI 改后以 Decoration 标注差异，逐点 accept/reject
 */

interface DiffState {
  suggestions: Map<string, Suggestion>;
  appliedChanges: Map<string, 'accepted' | 'rejected'>;
  isPreviewMode: boolean;
}

export function useDiffMark() {
  const [state, setState] = useState<DiffState>({
    suggestions: new Map(),
    appliedChanges: new Map(),
    isPreviewMode: false,
  });

  // 原始文档快照（一个段落可能有多个 suggestion）
  const snapshotRef = useRef<Record<string, string>>({});

  /**
   * 添加一个新的 AI 建议到 diff 列表
   */
  const addSuggestion = useCallback((paraId: string, originalText: string, aiText: string) => {
    const suggestion = createSuggestion(paraId, originalText, aiText);
    setState((prev) => {
      const next = new Map(prev.suggestions);
      next.set(suggestion.suggestionId, suggestion);
      return {
        ...prev,
        suggestions: next,
        isPreviewMode: true,
      };
    });
    return suggestion;
  }, []);

  /**
   * 批量添加建议
   */
  const addSuggestions = useCallback(
    (suggestions: Array<{ paraId: string; original: string; aiText: string }>) => {
      const newSuggestions = new Map(state.suggestions);
      const newSnapshot: Record<string, string> = { ...snapshotRef.current };

      for (const s of suggestions) {
        const sg = createSuggestion(s.paraId, s.original, s.aiText);
        newSuggestions.set(sg.suggestionId, sg);
        newSnapshot[s.paraId] = s.original;
      }

      snapshotRef.current = newSnapshot;
      setState((prev) => ({
        ...prev,
        suggestions: newSuggestions,
        isPreviewMode: true,
      }));
    },
    [state.suggestions],
  );

  /**
   * 接受单个建议
   */
  const acceptSuggestion = useCallback(
    (suggestionId: string): string | null => {
      const suggestion = state.suggestions.get(suggestionId);
      if (!suggestion) return null;

      setState((prev) => {
        const nextApplied = new Map(prev.appliedChanges);
        nextApplied.set(suggestionId, 'accepted');

        // 检查是否该段落的所有建议都已处理
        const paraSuggestions = Array.from(prev.suggestions.values())
          .filter((s) => s.paragraphId === suggestion.paragraphId);

        const remainingUnresolved = paraSuggestions.filter(
          (s) =>
            s.suggestionId !== suggestionId &&
            !nextApplied.has(s.suggestionId),
        );

        return {
          ...prev,
          appliedChanges: nextApplied,
          isPreviewMode: remainingUnresolved.length > 0,
        };
      });

      return suggestion.suggestedText;
    },
    [state.suggestions],
  );

  /**
   * 拒绝单个建议
   */
  const rejectSuggestion = useCallback(
    (suggestionId: string): string | null => {
      const suggestion = state.suggestions.get(suggestionId);
      if (!suggestion) return null;

      setState((prev) => {
        const nextApplied = new Map(prev.appliedChanges);
        nextApplied.set(suggestionId, 'rejected');

        const paraSuggestions = Array.from(prev.suggestions.values())
          .filter((s) => s.paragraphId === suggestion.paragraphId);

        const remainingUnresolved = paraSuggestions.filter(
          (s) =>
            s.suggestionId !== suggestionId &&
            !nextApplied.has(s.suggestionId),
        );

        return {
          ...prev,
          appliedChanges: nextApplied,
          isPreviewMode: remainingUnresolved.length > 0,
        };
      });

      return suggestion.originalText;
    },
    [state.suggestions],
  );

  /**
   * 全部接受 — 所有建议生效
   */
  const acceptAll = useCallback((): Record<string, string> => {
    const result: Record<string, string> = {};
    const allApplied = new Map<string, 'accepted' | 'rejected'>();

    state.suggestions.forEach((sg) => {
      allApplied.set(sg.suggestionId, 'accepted');
      result[sg.paragraphId] = sg.suggestedText;
    });

    setState((prev) => ({
      ...prev,
      appliedChanges: allApplied,
      isPreviewMode: false,
    }));

    return result;
  }, [state.suggestions]);

  /**
   * 全部拒绝 — 回到 snapshot
   */
  const rejectAll = useCallback((): Record<string, string> => {
    const result: Record<string, string> = { ...snapshotRef.current };
    const allApplied = new Map<string, 'accepted' | 'rejected'>();

    state.suggestions.forEach((sg) => {
      allApplied.set(sg.suggestionId, 'rejected');
    });

    setState((prev) => ({
      ...prev,
      appliedChanges: allApplied,
      isPreviewMode: false,
    }));

    return result;
  }, [state.suggestions]);

  /**
   * 获取待处理的建议数量
   */
  const pendingCount = state.suggestions.size - state.appliedChanges.size;

  /**
   * 获取有待处理建议的 paraId 列表
   */
  const getPendingParaIds = useCallback((): string[] => {
    const ids = new Set<string>();
    state.suggestions.forEach((sg, id) => {
      if (!state.appliedChanges.has(id)) {
        ids.add(sg.paragraphId);
      }
    });
    return Array.from(ids);
  }, [state.suggestions, state.appliedChanges]);

  return {
    suggestions: state.suggestions,
    appliedChanges: state.appliedChanges,
    isPreviewMode: state.isPreviewMode,
    pendingCount,
    addSuggestion,
    addSuggestions,
    acceptSuggestion,
    rejectSuggestion,
    acceptAll,
    rejectAll,
    getPendingParaIds,
  };
}
