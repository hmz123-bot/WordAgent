import React, { useState, useCallback, useRef, useEffect, useLayoutEffect, useMemo } from 'react';
import {
  MagicWand,
  Translate,
  CheckCorrect,
  Right,
  Refresh,
  Left,
  Close,
} from '@icon-park/react';
import type { IconComp } from '../../types/icon';
import { SCOPE_OPTIONS, type ModificationScope } from '../utils/modificationScope';
import { REFINE_PRESETS, type PolishVersion, type AiOperation } from '../utils/inlineSession';
import { computeDiff } from '../utils/diffEngine';

/**
 * Inline Assistant — 划词浮窗 AI 操作面板。
 *
 * 用户在编辑器中选中文字后出现浮动面板，提供：
 * - 润色改写 / 翻译 / 语法校正 / 续写 / 自定义指令
 * - 修改范围选择（仅选区 / 当前句 / 当前段 / 全文）
 * - 多轮迭代润色：结果不满意可继续追加要求或换一版，
 *   每一版都留在版本列表里，可来回切换、与原文对比，最后任选一版接受。
 */

export type { AiOperation };

interface QuickAction {
  id: AiOperation;
  label: string;
  Icon: IconComp;
  description: string;
}

const QUICK_ACTIONS: QuickAction[] = [
  { id: 'polish', label: '润色', Icon: MagicWand, description: '优化文字表达' },
  { id: 'translate', label: '翻译', Icon: Translate, description: '翻译为中文' },
  { id: 'fix-grammar', label: '语法', Icon: CheckCorrect, description: '修正语法错误' },
  { id: 'continue', label: '续写', Icon: Right, description: '继续写作' },
  { id: 'rewrite', label: '改写', Icon: Refresh, description: '按指令重写' },
];

export interface InlineOptions {
  targetLang?: string;
  tone?: string;
}

const LANGS = [
  { id: 'auto', label: '自动' },
  { id: 'zh', label: '中文' },
  { id: 'en', label: '英语' },
  { id: 'ja', label: '日语' },
  { id: 'ko', label: '韩语' },
  { id: 'fr', label: '法语' },
];

const TONES = [
  { id: 'natural', label: '自然' },
  { id: 'formal', label: '正式' },
  { id: 'concise', label: '简洁' },
  { id: 'lively', label: '活泼' },
  { id: 'academic', label: '学术' },
];

/** 浮窗与选区之间的间距 / 与视口边缘的最小留白 */
const ANCHOR_GAP = 10;
const VIEWPORT_MARGIN = 8;

interface InlineAssistantProps {
  visible: boolean;
  /** 选区锚点：top/bottom 为选区上下边，left 为水平中心（视口坐标） */
  position: { top: number; bottom?: number; left: number };
  selectedText: string;
  paraIds: string[];
  loading?: AiOperation | null;
  previewText?: string;
  resultText?: string | null;
  errorText?: string | null;
  onAction: (operation: AiOperation, customInstruction?: string, options?: InlineOptions) => void;
  onAccept: () => void;
  onReject: () => void;
  onRetry: () => void;
  onDismiss: () => void;
  modificationScope?: ModificationScope;
  onScopeChange?: (s: ModificationScope) => void;
  // === 多轮迭代润色 ===
  /** 本次会话已产生的所有版本 */
  versions?: PolishVersion[];
  /** 当前查看的版本下标 */
  versionIndex?: number;
  onVersionChange?: (idx: number) => void;
  /** 在当前版本基础上追加要求，生成下一版 */
  onRefine?: (instruction: string) => void;
  /** 中止正在进行的生成 */
  onStop?: () => void;
  /** 本次会话的原文（用于对比） */
  originalText?: string;
  /** 是否与原文对比显示 */
  compare?: boolean;
  onToggleCompare?: () => void;
}

const InlineAssistant: React.FC<InlineAssistantProps> = ({
  visible,
  position,
  selectedText,
  paraIds,
  loading = null,
  previewText = '',
  resultText = null,
  errorText = null,
  onAction,
  onAccept,
  onReject,
  onRetry,
  onDismiss,
  modificationScope = 'selection',
  onScopeChange,
  versions = [],
  versionIndex = 0,
  onVersionChange,
  onRefine,
  onStop,
  originalText = '',
  compare = false,
  onToggleCompare,
}) => {
  const [showCustom, setShowCustom] = useState(false);
  const [customInstruction, setCustomInstruction] = useState('');
  const [pendingOp, setPendingOp] = useState<AiOperation | null>(null);
  const [refineInput, setRefineInput] = useState('');
  const inputRef = useRef<HTMLInputElement>(null);
  const panelRef = useRef<HTMLDivElement>(null);

  /**
   * 计算出的最终摆放位置。
   * 结果态面板可高达 ~450px，若始终锚在选区上方，正文第一屏的选区会把面板顶出视口，
   * 版本导航 / 换一版 / 对比等控件正好落在被裁掉的部分。这里改为测量后自适应：
   * 上方放不下就翻到下方，横向按视口夹取，箭头跟着选区中心走。
   */
  const [placement, setPlacement] = useState<{
    top: number;
    left: number;
    maxHeight: number;
    below: boolean;
    arrowLeft: number;
  } | null>(null);

  const scopeLabel = SCOPE_OPTIONS.find((s) => s.id === modificationScope)?.label ?? '';
  const currentVersion = versions[versionIndex] ?? null;
  const isBusy = loading !== null;
  // 是否处于「结果预览」态（有版本 / 流式内容 / 错误，或正在生成）
  const showResult = versions.length > 0 || !!previewText || !!errorText || isBusy;

  useEffect(() => {
    if (showCustom && inputRef.current) {
      inputRef.current.focus();
    }
  }, [showCustom]);

  // 测量面板真实尺寸 → 决定放在选区上方还是下方，并夹进视口
  useLayoutEffect(() => {
    if (!visible) {
      setPlacement(null);
      return;
    }

    const compute = () => {
      const node = panelRef.current;
      if (!node) return;

      // 临时解除高度约束，拿到内容的自然高度，避免"约束→变矮→又判定放得下"的来回抖动
      const prevMax = node.style.maxHeight;
      node.style.maxHeight = 'none';
      const naturalH = node.offsetHeight;
      const w = node.offsetWidth;
      node.style.maxHeight = prevMax;

      const vw = window.innerWidth;
      const vh = window.innerHeight;
      const anchorTop = position.top;
      const anchorBottom = position.bottom ?? position.top;

      const spaceAbove = anchorTop - VIEWPORT_MARGIN - ANCHOR_GAP;
      const spaceBelow = vh - anchorBottom - VIEWPORT_MARGIN - ANCHOR_GAP;
      // 上方放不下、且下方更宽敞时翻转到选区下方
      const below = naturalH > spaceAbove && spaceBelow > spaceAbove;

      const avail = Math.max(160, below ? spaceBelow : spaceAbove);
      const usedH = Math.min(naturalH, avail);

      let top = below ? anchorBottom + ANCHOR_GAP : anchorTop - ANCHOR_GAP - usedH;
      top = Math.max(VIEWPORT_MARGIN, Math.min(top, vh - usedH - VIEWPORT_MARGIN));

      let left = position.left - w / 2;
      left = Math.max(VIEWPORT_MARGIN, Math.min(left, vw - w - VIEWPORT_MARGIN));

      // 箭头始终指向选区中心（面板被夹取时不再是正中间）
      const arrowLeft = Math.min(Math.max(position.left - left, 14), Math.max(14, w - 14));

      setPlacement((prev) =>
        prev &&
        prev.top === top &&
        prev.left === left &&
        prev.below === below &&
        prev.maxHeight === avail &&
        prev.arrowLeft === arrowLeft
          ? prev
          : { top, left, maxHeight: avail, below, arrowLeft },
      );
    };

    compute();
    window.addEventListener('resize', compute);
    window.addEventListener('scroll', compute, true);
    return () => {
      window.removeEventListener('resize', compute);
      window.removeEventListener('scroll', compute, true);
    };
  }, [
    visible,
    position.top,
    position.bottom,
    position.left,
    showResult,
    versions.length,
    versionIndex,
    pendingOp,
    showCustom,
    compare,
    errorText,
    isBusy,
    previewText,
  ]);

  // 点击外部关闭
  useEffect(() => {
    const handleClickOutside = () => {
      if (!visible) return;
      // 生成中或已有结果时，误点外部不应丢掉整个会话（版本历史），需显式「放弃 / ×」
      if (isBusy || versions.length > 0) return;
      onDismiss();
    };
    if (visible) {
      // 延迟绑定，避免触发的 mouseup 立即关闭
      const timer = setTimeout(() => {
        document.addEventListener('mousedown', handleClickOutside);
      }, 100);
      return () => {
        clearTimeout(timer);
        document.removeEventListener('mousedown', handleClickOutside);
      };
    }
  }, [visible, onDismiss, isBusy, versions.length]);

  // 新版本产生后清空追加指令输入
  useEffect(() => {
    setRefineInput('');
  }, [versions.length]);

  // 与原文的字符级差异（仅在开启对比且已有结果时计算）
  const diffNodes = useMemo(() => {
    if (!compare || !currentVersion || !originalText) return null;
    return computeDiff(originalText, currentVersion.text).map((chunk, i) => (
      <span key={i} className={`ia-diff ia-diff-${chunk.type}`}>
        {chunk.text}
      </span>
    ));
  }, [compare, currentVersion, originalText]);

  const submitRefine = useCallback(() => {
    const text = refineInput.trim();
    if (!text || isBusy) return;
    onRefine?.(text);
    setRefineInput('');
  }, [refineInput, isBusy, onRefine]);

  if (!visible) return null;

  return (
    <div
      ref={panelRef}
      className={`inline-assistant ${showResult ? 'is-result' : ''} ${placement?.below ? 'is-below' : ''}`}
      style={{
        position: 'fixed',
        top: `${placement ? placement.top : position.top}px`,
        left: `${placement ? placement.left : position.left}px`,
        maxHeight: placement ? `${placement.maxHeight}px` : undefined,
        // 首帧先隐藏，测量出位置后再显示，避免闪一下错位
        visibility: placement ? 'visible' : 'hidden',
        zIndex: 9999,
        ['--ia-arrow-left' as any]: placement ? `${placement.arrowLeft}px` : '50%',
      }}
      onMouseDown={(e) => e.stopPropagation()}
    >
      <div className="inline-assistant-arrow" />

      {showResult ? (
        <>
          <div className="inline-assistant-header">
            <span className="selected-count">
              {versions.length > 1 && (
                <span className="ia-version-nav">
                  <button
                    className="ia-version-btn"
                    onClick={() => onVersionChange?.(versionIndex - 1)}
                    disabled={versionIndex <= 0 || isBusy}
                    title="上一版"
                  >
                    <Left theme="outline" size="12" />
                  </button>
                  <span className="ia-version-idx">
                    {versionIndex + 1}/{versions.length}
                  </span>
                  <button
                    className="ia-version-btn"
                    onClick={() => onVersionChange?.(versionIndex + 1)}
                    disabled={versionIndex >= versions.length - 1 || isBusy}
                    title="下一版"
                  >
                    <Right theme="outline" size="12" />
                  </button>
                </span>
              )}
              {isBusy
                ? versions.length > 0
                  ? '正在生成下一版…'
                  : '生成中…'
                : currentVersion
                  ? `${currentVersion.label} · ${currentVersion.text.length} 字`
                  : '预览'}
              {scopeLabel && !isBusy && <span className="ia-scope-tag">· {scopeLabel}</span>}
            </span>
            <button className="ia-close-btn" onClick={onDismiss} title="关闭">×</button>
          </div>

          <div className="ia-result-body">
            {errorText ? (
              <div className="ia-error">{errorText}</div>
            ) : isBusy ? (
              <div className="ia-preview-text">{previewText || '生成中…'}</div>
            ) : diffNodes ? (
              <div className="ia-preview-text ia-diff-view">{diffNodes}</div>
            ) : (
              <div className="ia-preview-text">{currentVersion?.text || previewText || '生成中…'}</div>
            )}
          </div>

          {isBusy ? (
            <div className="ia-result-actions">
              <button className="ia-stop-btn" onClick={onStop}>
                停止生成
              </button>
            </div>
          ) : (
            <>
              <div className="ia-result-actions">
                <button className="ia-accept-btn" onClick={onAccept} disabled={!currentVersion}>
                  接受{versions.length > 1 ? `第 ${versionIndex + 1} 版` : ''}
                </button>
                <button className="ia-reject-btn" onClick={onReject}>
                  放弃
                </button>
                <button className="ia-retry-btn" onClick={onRetry} title="基于当前版本换一种写法">
                  <Refresh theme="outline" size="14" />换一版
                </button>
                <button
                  className={`ia-compare-btn ${compare ? 'is-active' : ''}`}
                  onClick={onToggleCompare}
                  disabled={!currentVersion || !originalText}
                  title="与原文对比（红色删除 / 绿色新增）"
                >
                  对比
                </button>
              </div>

              {currentVersion && (
                <div className="ia-refine">
                  <div className="ia-refine-label">继续润色</div>
                  <div className="ia-refine-presets">
                    {REFINE_PRESETS.map((p) => (
                      <button
                        key={p.id}
                        className="ia-option-chip"
                        onClick={() => onRefine?.(p.instruction)}
                        title={p.instruction}
                      >
                        {p.label}
                      </button>
                    ))}
                  </div>
                  <div className="ia-refine-input-row">
                    <input
                      type="text"
                      className="ia-refine-input"
                      placeholder="或输入具体要求，如“开头点题、结尾加一句总结”"
                      value={refineInput}
                      onChange={(e) => setRefineInput(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === 'Enter') submitRefine();
                      }}
                    />
                    <button
                      className="ia-refine-submit"
                      onClick={submitRefine}
                      disabled={!refineInput.trim()}
                    >
                      再来一版
                    </button>
                  </div>
                </div>
              )}
            </>
          )}
        </>
      ) : pendingOp === 'translate' ? (
        <>
          <div className="inline-assistant-header">
            <span className="selected-count">翻译为</span>
            <button className="ia-close-btn" onClick={() => setPendingOp(null)} title="返回">×</button>
          </div>
          <div className="ia-options">
            {LANGS.map((l) => (
              <button
                key={l.id}
                className="ia-option-chip"
                onClick={() => {
                  onAction('translate', undefined, { targetLang: l.id });
                  setPendingOp(null);
                }}
              >
                {l.label}
              </button>
            ))}
          </div>
        </>
      ) : pendingOp === 'polish' ? (
        <>
          <div className="inline-assistant-header">
            <span className="selected-count">润色风格</span>
            <button className="ia-close-btn" onClick={() => setPendingOp(null)} title="返回">×</button>
          </div>
          <div className="ia-options">
            {TONES.map((t) => (
              <button
                key={t.id}
                className="ia-option-chip"
                onClick={() => {
                  onAction('polish', undefined, { tone: t.id });
                  setPendingOp(null);
                }}
              >
                {t.label}
              </button>
            ))}
          </div>
        </>
      ) : !showCustom ? (
        <>
          <div className="inline-assistant-header">
            <span className="selected-count">{selectedText.length} 字已选中</span>
            <button className="ia-close-btn" onClick={onDismiss} title="关闭">×</button>
          </div>
          <div className="ia-scope">
            <span className="ia-scope-label">修改范围</span>
            <div className="ia-scope-options">
              {SCOPE_OPTIONS.map((s) => (
                <button
                  key={s.id}
                  type="button"
                  className={`ia-scope-chip ${modificationScope === s.id ? 'is-active' : ''}`}
                  onClick={() => onScopeChange?.(s.id)}
                  disabled={isBusy}
                >
                  {s.label}
                </button>
              ))}
            </div>
          </div>
          <div className="inline-assistant-actions">
            {QUICK_ACTIONS.map((action) => {
              const isLoading = loading === action.id;
              const needsOption = action.id === 'translate' || action.id === 'polish';
              return (
                <button
                  key={action.id}
                  className={`ia-action-btn ${isLoading ? 'is-loading' : ''}`}
                  onClick={() => (needsOption ? setPendingOp(action.id) : onAction(action.id))}
                  disabled={isBusy}
                  title={action.description}
                >
                  {isLoading ? (
                    <span className="dv2-spinner dv2-spinner-btn" />
                  ) : (
                    <span className="ia-action-icon"><action.Icon theme="outline" size="16" /></span>
                  )}
                  <span className="ia-action-label">{action.label}</span>
                </button>
              );
            })}
          </div>
          <div className="inline-assistant-footer">
            <button className="ia-custom-btn" onClick={() => setShowCustom(true)}>
              自定义指令...
            </button>
          </div>
        </>
      ) : (
        <div className="inline-assistant-custom">
          <div className="ia-custom-header">
            <button className="ia-back-btn" onClick={() => setShowCustom(false)}>
              <Left theme="outline" size="14" />返回
            </button>
            <button className="ia-close-btn" onClick={onDismiss}>
              <Close theme="outline" size="14" />
            </button>
          </div>
          <input
            ref={inputRef}
            type="text"
            className="ia-custom-input"
            placeholder={'输入改写指令，如\u201C更简洁\u201D、\u201C更正式\u201D...'}
            value={customInstruction}
            onChange={(e) => setCustomInstruction(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter' && customInstruction.trim()) {
                onAction('rewrite', customInstruction.trim());
              }
            }}
          />
          <button
            className="ia-custom-submit"
            onClick={() => customInstruction.trim() && onAction('rewrite', customInstruction.trim())}
            disabled={!customInstruction.trim() || isBusy}
          >
            执行
          </button>
        </div>
      )}
    </div>
  );
};

export default InlineAssistant;
