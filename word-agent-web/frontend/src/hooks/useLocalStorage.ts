import { useState, useEffect, useCallback, useRef } from 'react';

/**
 * 将 React State 自动持久化到 localStorage 的 Hook。
 *
 * 特性：
 * - 支持函数式初始值（lazy initializer）
 * - key 变化时自动重新读取（适用同组件切换文档场景）
 * - 同 key 跨 tab 同步（通过 storage 事件）
 * - 序列化失败自动降级为默认值
 * - 读/写 localStorage 异常静默吞掉（隐私模式/Safari 兼容）
 *
 * 用法：
 *   const [value, setValue, removeValue] = useLocalStorageState('my_key', defaultValue);
 *   // setValue 与 useState 的 setter 完全兼容
 *   // removeValue 清除该 key 并恢复默认值
 *
 * @param key         localStorage 键名，建议带前缀如 "wa_" 避免冲突
 * @param initialValue  默认值（当 localStorage 不存在或解析失败时使用）
 */
export function useLocalStorageState<T>(
  key: string,
  initialValue: T | (() => T),
): [T, React.Dispatch<React.SetStateAction<T>>, () => void] {
  // 保存 key 和初始值到 ref，避免闭包陈旧问题
  const keyRef = useRef(key);
  const initialValueRef = useRef(initialValue);
  initialValueRef.current = initialValue;

  const resolveDefault = (): T => {
    const init = initialValueRef.current;
    return init instanceof Function ? (init as () => T)() : init;
  };

  const readFromStorage = (k: string): T => {
    try {
      const stored = localStorage.getItem(k);
      if (stored !== null) return JSON.parse(stored) as T;
    } catch {
      /* 解析失败，降级到默认值 */
    }
    return resolveDefault();
  };

  const [state, setState] = useState<T>(() => readFromStorage(key));

  // key 变化时（如切换到不同文档 ID）重新读取
  useEffect(() => {
    if (keyRef.current !== key) {
      keyRef.current = key;
      setState(readFromStorage(key));
    }
  }, [key]);

  // 跨 Tab 同步：监听其他标签页对同一 key 的修改
  useEffect(() => {
    const handler = (e: StorageEvent) => {
      if (e.key === key && e.newValue !== null) {
        try {
          setState(JSON.parse(e.newValue) as T);
        } catch {
          /* ignore */
        }
      }
    };
    window.addEventListener('storage', handler);
    return () => window.removeEventListener('storage', handler);
  }, [key]);

  // 包装后的 setter — 写入 state 的同时写 localStorage
  const setValue: React.Dispatch<React.SetStateAction<T>> = useCallback(
    (value: React.SetStateAction<T>) => {
      setState((prev) => {
        const next = value instanceof Function ? value(prev) : value;
        try {
          localStorage.setItem(keyRef.current, JSON.stringify(next));
        } catch {
          /* storage 满或不可用 */
        }
        return next;
      });
    },
    [],
  );

  // 便捷删除方法：清除 localStorage 并恢复默认值
  const removeValue = useCallback(() => {
    try {
      localStorage.removeItem(keyRef.current);
    } catch {
      /* ignore */
    }
    setState(resolveDefault());
  }, []);

  return [state, setValue, removeValue];
}

/**
 * localStorage key 命名工具。
 * 所有 key 统一以 "wa_" 为前缀，避免与其他应用冲突。
 */
export const LSKeys = {
  /** 编辑器 — 文档的修改节点（per-doc, key = `wa_editor_{id}_nodes`） */
  editorNodes: (docId: string) => `wa_editor_${docId}_nodes`,
  /** 编辑器 — AI 指令输入框内容（per-doc） */
  editorAIInstruction: (docId: string) => `wa_editor_${docId}_ai`,
  /** 编辑器 — 当前标签页（edit/preview/changeset/version，per-doc） */
  editorActiveTab: (docId: string) => `wa_editor_${docId}_tab`,
  /** 编辑器 — AI 面板展开状态（per-doc） */
  editorAIPanel: (docId: string) => `wa_editor_${docId}_panel`,
  /** 编辑器 — AI 建议展开/折叠  */
  editorAIExpanded: (docId: string) => `wa_editor_${docId}_exp`,
  /** 文档列表 — 状态筛选 */
  listStatusFilter: `wa_list_status`,
  /** 文档列表 — 每页条数 */
  listPageSize: `wa_list_pageSize`,
  /** 搜索 — 搜索关键词 */
  searchQuery: `wa_search_query`,
  /** 搜索 — 区分大小写 */
  searchCaseSensitive: `wa_search_cs`,
  /** 搜索 — 全词匹配 */
  searchWholeWord: `wa_search_ww`,
};
