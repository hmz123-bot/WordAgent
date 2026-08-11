import React, { useState, useCallback, useEffect, useRef } from 'react';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useToast } from '../components/Toast';
import { useLocalStorageState, LSKeys } from '../hooks/useLocalStorage';
import {
  searchDocumentsLocal,
  replaceInDocument,
  type SearchMatch,
} from '../services/searchService';

// ========== 工具函数 ==========

function highlightText(text: string, query: string): React.ReactNode {
  if (!query || !text) return text;
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const parts = text.split(new RegExp(`(${escaped})`, 'gi'));
  return parts.map((part, i) =>
    part.toLowerCase() === query.toLowerCase()
      ? <mark key={i} className="search-highlight">{part}</mark>
      : part
  );
}

function getNodeTypeLabel(type: string): string {
  const map: Record<string, string> = {
    heading: '标题',
    paragraph: '段落',
    table: '表格',
    table_row: '行',
    table_cell: '单元格',
    list: '列表',
    image: '图片',
  };
  return map[type] || type;
}

function formatSearchTime(ms: number): string {
  if (ms < 1000) return `${ms}ms`;
  return `${(ms / 1000).toFixed(2)}s`;
}

/** 提取匹配周围的上下文片段 */
function getContextSnippet(text: string, query: string, range = 60): string {
  if (!text) return '';
  const idx = text.toLowerCase().indexOf(query.toLowerCase());
  if (idx < 0) return text.substring(0, range * 2);
  const start = Math.max(0, idx - range);
  const end = Math.min(text.length, idx + query.length + range);
  let snippet = text.substring(start, end);
  if (start > 0) snippet = '...' + snippet;
  if (end < text.length) snippet = snippet + '...';
  return snippet;
}

// ========== 搜索历史 ==========

const HISTORY_KEY = 'search_history';
const MAX_HISTORY = 10;

function loadHistory(): string[] {
  try {
    return JSON.parse(localStorage.getItem(HISTORY_KEY) || '[]');
  } catch { return []; }
}

function saveHistory(query: string) {
  const history = loadHistory().filter(h => h !== query);
  history.unshift(query);
  localStorage.setItem(HISTORY_KEY, JSON.stringify(history.slice(0, MAX_HISTORY)));
}

function clearHistory() {
  localStorage.removeItem(HISTORY_KEY);
}

// ========== 主组件 ==========

const SearchPage: React.FC = () => {
  const navigate = useNavigate();
  const [searchParams] = useSearchParams();
  const inputRef = useRef<HTMLInputElement>(null);
  const { toast } = useToast();

  // 搜索状态（持久化：记住上次搜索内容和选项）
  const [query, setQuery] = useLocalStorageState<string>(LSKeys.searchQuery, '');
  const [searching, setSearching] = useState(false);
  const [searched, setSearched] = useState(false);
  const [error, setError] = useState<string | null>(null);

  // 搜索结果
  const [results, setResults] = useState<Array<{
    documentId: string;
    title: string;
    totalMatches: number;
    matches: SearchMatch[];
  }>>([]);
  const [totalMatches, setTotalMatches] = useState(0);
  const [searchTime, setSearchTime] = useState(0);

  // 搜索历史
  const [history, setHistory] = useState<string[]>(loadHistory);
  const [showHistory, setShowHistory] = useState(false);

  // 选项（持久化：记住用户偏好）
  const [caseSensitive, setCaseSensitive] = useLocalStorageState<boolean>(LSKeys.searchCaseSensitive, false);
  const [wholeWord, setWholeWord] = useLocalStorageState<boolean>(LSKeys.searchWholeWord, false);

  // 展开结果
  const [expandedDocs, setExpandedDocs] = useState<Record<string, boolean>>({});

  // 替换弹窗
  const [showReplace, setShowReplace] = useState(false);
  const [replaceText, setReplaceText] = useState('');
  const [replacing, setReplacing] = useState(false);
  const [replaceResult, setReplaceResult] = useState<string | null>(null);
  const [replaceError, setReplaceError] = useState<string | null>(null);

  // 聚焦搜索框时显示历史
  useEffect(() => {
    const handler = () => setShowHistory(false);
    document.addEventListener('click', handler);
    return () => document.removeEventListener('click', handler);
  }, []);

  // 执行搜索
  const handleSearch = useCallback(async (searchQuery?: string) => {
    const q = (searchQuery || query).trim();
    if (!q) return;

    setSearching(true);
    setError(null);
    setSearched(false);
    setReplaceResult(null);
    setReplaceError(null);
    setShowHistory(false);

    saveHistory(q);
    setHistory(loadHistory());

    const startTime = Date.now();

    try {
      const { results: searchResults, totalMatches: total } = searchDocumentsLocal(q, {
        caseSensitive,
        wholeWord,
        maxResults: 100,
        contextChars: 120,
      });

      setResults(searchResults);
      setTotalMatches(total);
      setSearchTime(Date.now() - startTime);
      setSearched(true);
      // 默认展开所有有结果的文档
      const ex: Record<string, boolean> = {};
      searchResults.forEach(r => { ex[r.documentId] = true; });
      setExpandedDocs(ex);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '搜索失败';
      setError(msg);
      toast(msg, 'error');
    } finally {
      setSearching(false);
    }
  }, [query, caseSensitive, wholeWord, toast]);

  // 从顶栏 / 其他入口带 ?q= 进入时，自动执行搜索
  useEffect(() => {
    const q = searchParams.get('q');
    if (q) {
      setQuery(q);
      void handleSearch(q);
    }
    // 仅在挂载时根据 URL 参数触发一次
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  // 快捷键
  const handleKeyDown = (e: React.KeyboardEvent) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault();
      handleSearch();
    }
    if (e.key === 'Escape') {
      setShowHistory(false);
    }
  };

  // 打开替换弹窗
  const openReplace = () => {
    if (results.length === 0) return;
    setShowReplace(true);
    setReplaceText('');
    setReplaceResult(null);
    setReplaceError(null);
  };

  // 执行替换
  const handleReplace = async () => {
    if (!replaceText.trim() || results.length === 0) return;

    setReplacing(true);
    setReplaceResult(null);
    setReplaceError(null);

    let totalReplaced = 0;
    let replacedDocs = 0;
    let failedDocs = 0;

    for (const doc of results) {
      try {
        const n = replaceInDocument(
          doc.documentId,
          query.trim(),
          replaceText.trim(),
          caseSensitive,
        );
        totalReplaced += n;
        if (n > 0) replacedDocs++;
      } catch {
        failedDocs++;
      }
    }

    setReplacing(false);

    if (failedDocs > 0) {
      setReplaceResult(`成功替换 ${totalReplaced} 处（${replacedDocs} 篇文档），${failedDocs} 篇文档替换失败`);
    } else {
      setReplaceResult(`成功替换 ${totalReplaced} 处，共 ${replacedDocs} 篇文档`);
    }

    // 重新搜索
    handleSearch();
  };

  const toggleExpandDoc = (docId: string) => {
    setExpandedDocs(prev => ({ ...prev, [docId]: !prev[docId] }));
  };

  // 清除搜索历史
  const clearSearchHistory = (e: React.MouseEvent) => {
    e.stopPropagation();
    clearHistory();
    setHistory([]);
  };

  return (
    <div className="page-container" style={{ maxWidth: 900 }}>
      <div className="page-header">
        <div>
          <h1 className="page-title">搜索</h1>
          <p className="page-subtitle">跨文档全文搜索 · 支持高亮定位与批量替换</p>
        </div>
        <div className="search-shortcuts-hint">
          <kbd>Enter</kbd> 搜索 &nbsp; <kbd>Esc</kbd> 关闭
        </div>
      </div>

      {/* 搜索栏 */}
      <div className="search-hero" style={{ position: 'relative' }}>
        <div className="search-bar search-bar-large">
          <svg className="search-icon" width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
          </svg>
          <input
            ref={inputRef}
            type="text"
            className="search-input"
            placeholder="输入搜索关键词..."
            value={query}
            onChange={(e) => setQuery(e.target.value)}
            onKeyDown={handleKeyDown}
            onFocus={() => !searched && history.length > 0 && setShowHistory(true)}
            onClick={(e) => { e.stopPropagation(); if (!searched && history.length > 0) setShowHistory(true); }}
            autoFocus
          />
          {query && (
            <button
              className="btn-icon"
              onClick={() => { setQuery(''); setSearched(false); setResults([]); }}
              style={{ color: 'var(--color-text-tertiary)', cursor: 'pointer' }}
            >
              <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
              </svg>
            </button>
          )}
          <button
            className="btn btn-primary"
            style={{ fontSize: 14, padding: '9px 20px' }}
            onClick={() => handleSearch()}
            disabled={searching || !query.trim()}
          >
            {searching ? '搜索中...' : '搜索'}
          </button>
        </div>

        {/* 搜索历史下拉 */}
        {showHistory && history.length > 0 && (
          <div className="search-history-dropdown" onClick={(e) => e.stopPropagation()}>
            <div className="history-header">
              <span>最近搜索</span>
              <button className="btn-text" onClick={clearSearchHistory} style={{ fontSize: 12, color: 'var(--color-text-tertiary)' }}>
                清除历史
              </button>
            </div>
            {history.map((h, i) => (
              <div
                key={i}
                className="history-item"
                onClick={() => { setQuery(h); setShowHistory(false); handleSearch(h); }}
              >
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="var(--color-text-tertiary)" strokeWidth="2">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                <span>{h}</span>
              </div>
            ))}
          </div>
        )}

        {/* 搜索选项 */}
        <div className="search-options-inline">
          <label className="search-option">
            <input type="checkbox" checked={caseSensitive} onChange={e => setCaseSensitive(e.target.checked)} />
            <span>区分大小写</span>
          </label>
          <label className="search-option">
            <input type="checkbox" checked={wholeWord} onChange={e => setWholeWord(e.target.checked)} />
            <span>全词匹配</span>
          </label>
        </div>
      </div>

      {/* 错误状态 */}
      {error && !searching && (
        <div className="empty-state" style={{ marginTop: 32 }}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#EF4444" strokeWidth="1.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <p className="empty-title">搜索失败</p>
          <p className="empty-desc">{error}</p>
          <button className="btn btn-primary" onClick={() => handleSearch()}>重试</button>
        </div>
      )}

      {/* 搜索中 */}
      {searching && (
        <div className="loading-state" style={{ marginTop: 32 }}>
          <div className="spinner" />
          <p>正在扫描所有文档...</p>
        </div>
      )}

      {/* 搜索结果为空 */}
      {!searching && searched && !error && results.length === 0 && (
        <div className="empty-state" style={{ marginTop: 32 }}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="1.5">
            <circle cx="11" cy="11" r="8" />
            <line x1="21" y1="21" x2="16.65" y2="16.65" />
            <line x1="8" y1="11" x2="14" y2="11" />
          </svg>
          <p className="empty-title">未找到匹配结果</p>
          <p className="empty-desc">
            没有文档包含「<strong>{query}</strong>」，请尝试其他关键词或确认搜索选项
          </p>
        </div>
      )}

      {/* 搜索结果 */}
      {!searching && searched && results.length > 0 && (
        <>
          <div className="search-summary">
            <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
              <span>
                找到 <strong>{totalMatches}</strong> 处匹配，分布在 <strong>{results.length}</strong> 篇文档
              </span>
              <span className="search-summary-time">耗时 {formatSearchTime(searchTime)}</span>
            </div>
            <div style={{ display: 'flex', gap: 8 }}>
              <button
                className="btn btn-secondary"
                style={{ fontSize: 13, padding: '6px 14px' }}
                onClick={() => {
                  const allExpanded = Object.values(expandedDocs).every(Boolean);
                  const ex: Record<string, boolean> = {};
                  results.forEach(r => { ex[r.documentId] = !allExpanded; });
                  setExpandedDocs(ex);
                }}
              >
                {Object.values(expandedDocs).every(Boolean) ? '全部折叠' : '全部展开'}
              </button>
              <button className="btn btn-accent" style={{ fontSize: 13, padding: '6px 14px' }} onClick={openReplace}>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4 }}>
                  <path d="M16 3h5v5L8 19l-5-5L16 3z" />
                  <path d="M11 8l5 5" />
                </svg>
                批量替换
              </button>
            </div>
          </div>

          <div className="search-results">
            {results.map((docResult) => {
              const isExpanded = expandedDocs[docResult.documentId] !== false;
              const matchCount = docResult.matches.length;
              return (
                <div key={docResult.documentId} className="search-doc-group">
                  <div
                    className="search-doc-header"
                    onClick={() => toggleExpandDoc(docResult.documentId)}
                    style={{ cursor: 'pointer' }}
                  >
                    <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                      <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="#2D8C7F" strokeWidth="1.5">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                      </svg>
                      <span
                        className="search-doc-title"
                        onClick={(e) => { e.stopPropagation(); navigate(`/editor-v2/${docResult.documentId}`); }}
                      >
                        {docResult.title}
                      </span>
                    </div>
                    <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
                      <span className="search-doc-count">{docResult.totalMatches} 处匹配</span>
                      <svg
                        width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                        style={{
                          transform: isExpanded ? 'rotate(180deg)' : 'rotate(0)',
                          transition: 'transform 200ms',
                          color: 'var(--color-text-tertiary)',
                        }}
                      >
                        <polyline points="6 9 12 15 18 9" />
                      </svg>
                    </div>
                  </div>

                  {isExpanded && (
                    <div className="search-matches">
                      {docResult.matches.map((match, idx) => (
                        <div key={`${match.nodeId}-${idx}`} className="search-match-item">
                          <div className="match-path">
                            <span className="match-type">{getNodeTypeLabel(match.nodeType)}</span>
                            {match.context && (
                              <span className="match-location">
                                {getContextSnippet(match.context, query)}
                              </span>
                            )}
                          </div>
                          <div className="match-text">
                            {highlightText(match.textContent, query)}
                          </div>
                        </div>
                      ))}
                      {matchCount > 15 && (
                        <div className="match-more">
                          显示前 15 处，共 {matchCount} 处匹配
                        </div>
                      )}
                    </div>
                  )}
                </div>
              );
            })}
          </div>
        </>
      )}

      {/* 替换弹窗 */}
      {showReplace && (
        <div className="modal-overlay" onClick={() => !replacing && setShowReplace(false)}>
          <div className="modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <h3 className="modal-title">查找并替换</h3>
              <button className="modal-close" onClick={() => { setShowReplace(false); setReplaceResult(null); }}>
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="modal-body">
              <div className="form-group">
                <label className="form-label">查找</label>
                <input type="text" className="form-input" value={query} readOnly />
              </div>
              <div className="form-group">
                <label className="form-label">替换为</label>
                <input
                  type="text"
                  className="form-input"
                  placeholder="输入替换文本..."
                  value={replaceText}
                  onChange={(e) => setReplaceText(e.target.value)}
                  autoFocus
                  onKeyDown={(e) => { if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); handleReplace(); } }}
                />
              </div>
              <div className="form-hint">
                将在 <strong>{results.length}</strong> 篇文档、共 <strong>{totalMatches}</strong> 处匹配中执行替换
              </div>
              {replaceResult && (
                <div className="form-success">{replaceResult}</div>
              )}
              {replaceError && (
                <div className="form-error">{replaceError}</div>
              )}
            </div>
            <div className="modal-footer">
              <button
                className="btn btn-secondary"
                onClick={() => { setShowReplace(false); setReplaceResult(null); }}
                disabled={replacing}
              >
                取消
              </button>
              <button
                className="btn btn-primary"
                onClick={handleReplace}
                disabled={replacing || !replaceText.trim()}
              >
                {replacing ? '替换中...' : `全部替换 (${totalMatches} 处)`}
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default SearchPage;
