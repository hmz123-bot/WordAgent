import React, { useState, useRef, useEffect, useCallback, useMemo } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import { Search, Crown } from '@icon-park/react';
import { searchEverything, type SearchEverythingResult, type DocHit, type TemplateHit } from '../services/searchService';

type FlatItem = (DocHit | TemplateHit) & { flatIndex: number };

const Topbar: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const path = location.pathname;
  const [query, setQuery] = useState('');
  const [result, setResult] = useState<SearchEverythingResult | null>(null);
  const [open, setOpen] = useState(false);
  const [activeIndex, setActiveIndex] = useState(-1);

  const rootRef = useRef<HTMLDivElement>(null);
  const debounceRef = useRef<number | null>(null);

  // 防抖搜索
  const runSearch = useCallback((value: string) => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
    debounceRef.current = window.setTimeout(() => {
      const r = searchEverything(value);
      setResult(r);
      setOpen(value.trim().length > 0);
      setActiveIndex(-1);
    }, 150);
  }, []);

  useEffect(() => () => {
    if (debounceRef.current) window.clearTimeout(debounceRef.current);
  }, []);

  // 点击外部关闭
  useEffect(() => {
    const handler = (e: MouseEvent) => {
      if (rootRef.current && !rootRef.current.contains(e.target as Node)) {
        setOpen(false);
      }
    };
    document.addEventListener('mousedown', handler);
    return () => document.removeEventListener('mousedown', handler);
  }, []);

  const handleChange = (value: string) => {
    setQuery(value);
    if (!value.trim()) {
      setOpen(false);
      setResult(null);
      return;
    }
    runSearch(value);
  };

  // 扁平化结果用于键盘导航
  const flatItems: FlatItem[] = useMemo(() => {
    if (!result) return [];
    const items: FlatItem[] = [];
    result.documents.forEach((d, i) => items.push({ ...d, flatIndex: i }));
    result.templates.forEach((t, i) => items.push({ ...t, flatIndex: result.documents.length + i }));
    return items;
  }, [result]);

  const goDocument = (id: string) => {
    setOpen(false);
    navigate(`/editor-v2/${id}`);
  };

  const goTemplate = (tpl: TemplateHit) => {
    setOpen(false);
    navigate(`/editor-v2/gen-${Date.now()}`, {
      state: { title: tpl.title, initialHtml: tpl.html },
    });
  };

  const activate = (item: FlatItem) => {
    if (item.kind === 'document') goDocument(item.id);
    else goTemplate(item as TemplateHit);
  };

  const handleKeyDown = (e: React.KeyboardEvent<HTMLInputElement>) => {
    if (e.key === 'ArrowDown') {
      e.preventDefault();
      if (flatItems.length > 0) {
        setOpen(true);
        setActiveIndex((i) => (i + 1) % flatItems.length);
      }
      return;
    }
    if (e.key === 'ArrowUp') {
      e.preventDefault();
      if (flatItems.length > 0) {
        setOpen(true);
        setActiveIndex((i) => (i <= 0 ? flatItems.length - 1 : i - 1));
      }
      return;
    }
    if (e.key === 'Enter') {
      if (open && activeIndex >= 0 && flatItems[activeIndex]) {
        e.preventDefault();
        activate(flatItems[activeIndex]);
      } else if (query.trim()) {
        e.preventDefault();
        setOpen(false);
        navigate(`/search?q=${encodeURIComponent(query.trim())}`);
      }
      return;
    }
    if (e.key === 'Escape') {
      setOpen(false);
    }
  };

  const hasResults = result && result.total > 0;

  return (
    <div className="wa-topbar" ref={rootRef}>
      <div className="wa-search">
        <Search theme="outline" size="15" />
        <input
          placeholder="搜索文档、模板…"
          value={query}
          onChange={(e) => handleChange(e.target.value)}
          onKeyDown={handleKeyDown}
          onFocus={() => { if (query.trim() && result) setOpen(true); }}
        />

        {open && (
          <div className="search-dropdown">
            {!hasResults && (
              <div className="search-dropdown-empty">未找到与「{query.trim()}」相关的内容</div>
            )}

            {result && result.documents.length > 0 && (
              <div className="search-group">
                <div className="search-group-title">文档</div>
                {result.documents.map((d, i) => {
                  const flatIndex = i;
                  return (
                    <div
                      key={d.id}
                      className={`search-row ${activeIndex === flatIndex ? 'active' : ''}`}
                      onMouseEnter={() => setActiveIndex(flatIndex)}
                      onClick={() => goDocument(d.id)}
                    >
                      <div className="search-row-icon doc">
                        <Search theme="outline" size="14" />
                      </div>
                      <div className="search-row-body">
                        <div className="search-row-title">{d.title}</div>
                        <div className="search-row-sub">{d.snippet || '空文档'}</div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {result && result.templates.length > 0 && (
              <div className="search-group">
                <div className="search-group-title">模板</div>
                {result.templates.map((t, i) => {
                  const flatIndex = (result?.documents.length ?? 0) + i;
                  return (
                    <div
                      key={`${t.builtin ? 'b' : 'c'}-${t.id}`}
                      className={`search-row ${activeIndex === flatIndex ? 'active' : ''}`}
                      onMouseEnter={() => setActiveIndex(flatIndex)}
                      onClick={() => goTemplate(t)}
                    >
                      <div
                        className="search-row-icon tpl"
                        style={{ background: `linear-gradient(135deg, ${t.gradient[0]}, ${t.gradient[1]})` }}
                      >
                        <Crown theme="filled" size="13" />
                      </div>
                      <div className="search-row-body">
                        <div className="search-row-title">
                          {t.title}
                          <span className="search-row-tag">{t.builtin ? '内置' : '我的'}</span>
                        </div>
                        <div className="search-row-sub">{t.description}</div>
                      </div>
                    </div>
                  );
                })}
              </div>
            )}

            {hasResults && (
              <div
                className="search-dropdown-footer"
                onClick={() => { setOpen(false); navigate(`/search?q=${encodeURIComponent(query.trim())}`); }}
              >
                查看「{query.trim()}」的全部结果 →
              </div>
            )}
          </div>
        )}
      </div>

      <button className="wa-upgrade" onClick={() => navigate('/write')}>
        <Crown theme="filled" size="15" />升级会员
      </button>
    </div>
  );
};

export default Topbar;
