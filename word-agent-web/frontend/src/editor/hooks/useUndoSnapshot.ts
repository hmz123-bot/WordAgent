import { useRef, useCallback } from 'react';

/**
 * useUndoSnapshot — AI 修改的 undo 栈管理。
 *
 * 核心：AI 的"拒绝全部"是一次性回退到 snapshot 的完整文档 JSON，
 * 而不是在 undo 栈里压十几个 step。和 Tiptap 的 rejectAllAiChanges
 * 一样，直接把 initialDoc 还原回去。
 */

interface SnapshotEntry {
  timestamp: number;
  description: string;
  docJson: Record<string, any>;
}

export function useUndoSnapshot() {
  const snapshotRef = useRef<SnapshotEntry[]>([]);
  const maxSnapshots = 20; // 最多保留 20 个快照

  /**
   * 保存快照 — 在 AI 修改之前调用
   */
  const takeSnapshot = useCallback((docJson: Record<string, any>, description: string) => {
    const entry: SnapshotEntry = {
      timestamp: Date.now(),
      description,
      docJson: JSON.parse(JSON.stringify(docJson)), // 深拷贝
    };

    snapshotRef.current.unshift(entry);
    if (snapshotRef.current.length > maxSnapshots) {
      snapshotRef.current = snapshotRef.current.slice(0, maxSnapshots);
    }
  }, []);

  /**
   * 获取最近一个快照（用于 rejectAll 回退）
   */
  const getLatestSnapshot = useCallback((): Record<string, any> | null => {
    return snapshotRef.current[0]?.docJson ?? null;
  }, []);

  /**
   * 弹出最近一个快照（回退后删除）
   */
  const popSnapshot = useCallback((): Record<string, any> | null => {
    const entry = snapshotRef.current.shift();
    return entry?.docJson ?? null;
  }, []);

  /**
   * AI rejectAll — 恢复到最新快照
   */
  const restoreFromSnapshot = useCallback((): Record<string, any> | null => {
    return popSnapshot();
  }, [popSnapshot]);

  /**
   * 清空快照栈
   */
  const clearSnapshots = useCallback(() => {
    snapshotRef.current = [];
  }, []);

  /**
   * 获取快照数量
   */
  const snapshotCount = () => snapshotRef.current.length;

  return {
    takeSnapshot,
    getLatestSnapshot,
    restoreFromSnapshot,
    clearSnapshots,
    snapshotCount,
  };
}
