import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useToast } from '../components/Toast';
import {
  BUILTIN_TEMPLATES,
  TEMPLATE_CATEGORIES,
  type BuiltinTemplate,
} from '../data/builtinTemplates';
import { Star } from '@icon-park/react';
import {
  listTemplates,
  deleteTemplate,
  TEMPLATE_EVENT,
  type CustomTemplate,
} from '../services/templateStore';
import type { IconComp } from '../types/icon';

interface CardData {
  key: string;
  title: string;
  category: string;
  description: string;
  Icon: IconComp;
  gradient: [string, string];
  html: string;
  custom?: boolean;
  id?: string;
}

const MY_CATEGORY = '我的模板';

const Templates: React.FC = () => {
  const navigate = useNavigate();
  const { toast } = useToast();

  const [activeCat, setActiveCat] = useState<string>('全部');
  const [query, setQuery] = useState('');
  const [custom, setCustom] = useState<CustomTemplate[]>([]);
  const [preview, setPreview] = useState<CardData | null>(null);

  // 加载自定义模板（来自本地仓库，无后端依赖）
  const loadCustom = useCallback(() => setCustom(listTemplates()), []);
  useEffect(() => {
    loadCustom();
    const onChanged = () => loadCustom();
    window.addEventListener(TEMPLATE_EVENT, onChanged);
    return () => window.removeEventListener(TEMPLATE_EVENT, onChanged);
  }, [loadCustom]);

  // 组装卡片：内置 + 自定义
  const cards: CardData[] = useMemo(() => {
    const builtin: CardData[] = BUILTIN_TEMPLATES.map((t: BuiltinTemplate) => ({
      key: `b-${t.id}`,
      title: t.title,
      category: t.category,
      description: t.description,
      Icon: t.Icon,
      gradient: t.gradient,
      html: t.html,
    }));
    const mine: CardData[] = custom.map((t) => ({
      key: `c-${t.id}`,
      title: t.title,
      category: MY_CATEGORY,
      description: '你保存的模板',
      Icon: Star,
      gradient: ['#3a2410', '#e07a2f'],
      html: t.html,
      custom: true,
      id: t.id,
    }));
    return [...mine, ...builtin];
  }, [custom]);

  // 分类 + 搜索过滤
  const filtered = cards.filter((c) => {
    if (activeCat === '全部') {
      // 全部：展示内置 + 自定义
    } else if (activeCat === MY_CATEGORY) {
      if (!c.custom) return false;
    } else {
      if (c.category !== activeCat) return false;
    }
    if (query.trim()) {
      const q = query.trim().toLowerCase();
      return `${c.title} ${c.description}`.toLowerCase().includes(q);
    }
    return true;
  });

  // 使用模板 → 以 gen- 临时文档打开编辑器（与"新建文档"同源机制）
  const useTemplate = useCallback(
    (card: CardData) => {
      navigate(`/editor-v2/gen-${Date.now()}`, {
        state: { title: card.title, initialHtml: card.html },
      });
      toast(`已用模板「${card.title}」新建文档`, 'success');
    },
    [navigate, toast],
  );

  // 删除自定义模板
  const handleDelete = useCallback(
    (card: CardData) => {
      if (!card.id) return;
      deleteTemplate(card.id);
      if (preview?.id === card.id) setPreview(null);
      toast('已删除模板', 'success');
    },
    [preview, toast],
  );

  // Esc 关闭预览
  useEffect(() => {
    if (!preview) return;
    const onKey = (e: KeyboardEvent) => e.key === 'Escape' && setPreview(null);
    window.addEventListener('keydown', onKey);
    return () => window.removeEventListener('keydown', onKey);
  }, [preview]);

  const customCount = custom.length;

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">模板</h1>
          <p className="page-subtitle">
            从模板开始写作，或把常用文档存为「我的模板」。共 {BUILTIN_TEMPLATES.length} 个内置模板
            {customCount > 0 ? `，+ ${customCount} 个我的模板` : ''}
          </p>
        </div>
      </div>

      {/* 分类标签 */}
      <div className="tpl-tabs">
        {TEMPLATE_CATEGORIES.map((cat) => (
          <button
            key={cat}
            className={`tpl-tab ${activeCat === cat ? 'active' : ''}`}
            onClick={() => setActiveCat(cat)}
          >
            {cat}
            {cat === MY_CATEGORY && customCount > 0 && (
              <span className="tpl-tab-count">{customCount}</span>
            )}
          </button>
        ))}
      </div>

      {/* 搜索 */}
      <div className="search-bar" style={{ marginBottom: 24 }}>
        <svg className="search-icon" width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
          <circle cx="11" cy="11" r="8" />
          <line x1="21" y1="21" x2="16.65" y2="16.65" />
        </svg>
        <input
          type="text"
          className="search-input"
          placeholder="搜索模板名称或说明..."
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
      </div>

      {/* 空状态 */}
      {filtered.length === 0 && (
        <div className="empty-state">
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="1.5">
            <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
            <polyline points="14 2 14 8 20 8" />
            <line x1="16" y1="13" x2="8" y2="13" />
            <line x1="16" y1="17" x2="8" y2="17" />
          </svg>
          <p className="empty-title">
            {query ? '没有匹配的模板' : activeCat === MY_CATEGORY ? '还没有我的模板' : '该分类暂无模板'}
          </p>
          <p className="empty-desc">
            {activeCat === MY_CATEGORY
              ? '在编辑器「保存」菜单中选择「保存为模板」，即可沉淀常用文档'
              : '试试切换分类或修改搜索条件'}
          </p>
        </div>
      )}

      {/* 模板卡片网格 */}
      {filtered.length > 0 && (
        <div className="tpl-grid">
          {filtered.map((card) => {
            const { Icon } = card;
            return (
              <div key={card.key} className="tpl-card" onClick={() => setPreview(card)}>
                <div
                  className="tpl-thumb"
                  style={{ background: `linear-gradient(150deg, ${card.gradient[0]}, ${card.gradient[1]})` }}
                >
                  <Icon theme="outline" size="40" fill="rgba(255,255,255,.92)" strokeWidth={2} />
                  {card.custom && <span className="tpl-mine-badge">我的</span>}
                </div>
                <div className="tpl-card-body">
                  <div className="tpl-card-head">
                    <span className="tpl-card-title">{card.title}</span>
                    <span className="tpl-cat-tag">{card.category}</span>
                  </div>
                  <p className="tpl-card-desc">{card.description}</p>
                  <button
                    className="tpl-use-btn"
                    onClick={(e) => {
                      e.stopPropagation();
                      useTemplate(card);
                    }}
                  >
                    使用模板
                  </button>
                </div>
              </div>
            );
          })}
        </div>
      )}

      {/* 预览弹窗 */}
      {preview && (
        <div className="modal-overlay" onClick={() => setPreview(null)}>
          <div className="modal tpl-preview-modal" onClick={(e) => e.stopPropagation()}>
            <div className="modal-header">
              <div>
                <h3 className="modal-title">{preview.title}</h3>
                <span className="tpl-cat-tag" style={{ marginTop: 6, display: 'inline-block' }}>
                  {preview.category}
                </span>
              </div>
              <button className="modal-close" onClick={() => setPreview(null)} aria-label="关闭">
                <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                  <path d="M18 6L6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
            <div className="modal-body">
              <p className="tpl-preview-desc">{preview.description}</p>
              <div className="tpl-preview-body" dangerouslySetInnerHTML={{ __html: preview.html }} />
            </div>
            <div className="modal-footer">
              {preview.custom && (
                <button
                  className="btn btn-danger-outline"
                  onClick={() => handleDelete(preview)}
                  style={{ marginRight: 'auto' }}
                >
                  删除模板
                </button>
              )}
              <button className="btn btn-secondary" onClick={() => setPreview(null)}>
                关闭
              </button>
              <button className="btn btn-primary" onClick={() => useTemplate(preview)}>
                使用模板
              </button>
            </div>
          </div>
        </div>
      )}
    </div>
  );
};

export default Templates;
