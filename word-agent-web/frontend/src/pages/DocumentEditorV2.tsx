import React, { useState, useRef, useCallback, useEffect, useMemo } from 'react';
import { useLocation, useParams, useNavigate } from 'react-router-dom';
import { Robot, Save, Check, Export, MagicWand, Time, Down } from '@icon-park/react';
import TiptapEditor from '../editor/TiptapEditor';
import InlineAssistant, { type AiOperation } from '../editor/components/InlineAssistant';
import ChatPanel from '../editor/components/ChatPanel';
import AcceptRejectBar from '../editor/components/AcceptRejectBar';
import DraftGeneratorModal, { type ApplyMode } from '../editor/components/DraftGeneratorModal';
import VersionHistoryPanel from '../editor/components/VersionHistoryPanel';
import SaveAsModal from '../editor/components/SaveAsModal';
import { useDiffMark } from '../editor/hooks/useDiffMark';
import { useUndoSnapshot } from '../editor/hooks/useUndoSnapshot';
import { useAgentTools } from '../editor/hooks/useAgentTools';
import { collectEditorContext } from '../editor/utils/contextCollector';
import { resolveModificationScope, type ModificationScope } from '../editor/utils/modificationScope';
import {
  createVersion,
  versionsToTurns,
  type PolishVersion,
  type VersionKind,
} from '../editor/utils/inlineSession';
import { deepseekInlineEdit, type InlineEditOptions } from '../agent/deepseek';
import type { AgentController } from '../agent/agentService';
import { getToken } from '../services/authService';
import { useToast } from '../components/Toast';
import {
  getDoc,
  upsertDoc,
  saveAsDoc,
  isGenId,
  notifyDocsChanged,
  type LocalDoc,
} from '../services/docStore';
import {
  recordVersion,
  deleteVersion,
  migrateVersions,
  countVersions,
  DOC_VERSIONS_EVENT,
  type DocVersion,
} from '../services/docVersions';
import { addTemplate } from '../services/templateStore';
import { exportDocument, EXPORT_OPTIONS, type ExportFormat } from '../services/docExport';

const DEMO_HTML =
  '<h2>欢迎使用 Word Agent</h2><p>这是一个集成 AI 辅助编辑的智能文档编辑器。</p><p>选中文字可以使用浮动工具栏进行 <strong>润色</strong>、<em>翻译</em> 等操作。</p><p>点击右上角的 <strong>AI 助手</strong> 可以打开侧边对话面板。</p>';

/**
 * 解析初始文档：优先用已持久化的真实文档；否则用写作台带入的 initialHtml（gen- 临时文档）。
 */
function resolveInitial(
  routeId: string | undefined,
  passed: { title?: string; initialHtml?: string } | null,
): { id: string; title: string; html: string; saved: boolean } {
  if (routeId && !isGenId(routeId)) {
    const existing = getDoc(routeId);
    if (existing) {
      return { id: existing.id, title: existing.title, html: existing.html, saved: true };
    }
  }
  return {
    id: routeId || '',
    title: passed?.title || '未命名文档',
    html: passed?.initialHtml || DEMO_HTML,
    saved: false,
  };
}

/**
 * DocumentEditorV2 — 完整的三层 AI 辅助编辑体验，并接入本地保存。
 *
 * ┌──────────────────────────────────────┐
 * │  AcceptRejectBar（AI修改审核栏）       │
 * ├──────────────────────────────────────┤
 * │  TiptapEditor（ProseMirror 编辑器）   │  ChatPanel（侧边栏）
 * │  InlineAssistant（划词浮窗）           │
 * └──────────────────────────────────────┘
 */
const DocumentEditorV2: React.FC = () => {
  const { toast } = useToast();
  const location = useLocation();
  const params = useParams();
  const navigate = useNavigate();
  const routeId = params.id;

  // 支持从「写作台 / 文档库」带内容跳入：location.state.{ title, initialHtml }
  const passed = (location.state as { title?: string; initialHtml?: string } | null) ?? null;

  // 只在首次挂载时解析一次初始文档
  const initial = useMemo(() => resolveInitial(routeId, passed), []); // eslint-disable-line react-hooks/exhaustive-deps

  const editorRef = useRef<any>(null);

  const [documentTitle, setDocumentTitle] = useState(initial.title);
  const [docId, setDocId] = useState(initial.id);
  const [currentDocJson, setCurrentDocJson] = useState<Record<string, any> | null>(null);
  const [currentDocHtml, setCurrentDocHtml] = useState(initial.html);

  // === 保存状态机 ===
  // idle: 与已保存一致 | dirty: 有改动未保存 | saving: 保存中 | saved: 刚保存成功
  const [saveState, setSaveState] = useState<'idle' | 'dirty' | 'saving' | 'saved'>(
    initial.saved ? 'idle' : 'dirty',
  );
  // 最近一次成功保存的内容（用于脏标记比较）
  const lastSavedRef = useRef<{ id: string; title: string; html: string }>({
    id: initial.id,
    title: initial.title,
    html: initial.html,
  });
  // 是否为尚未持久化的新文档
  const isNewRef = useRef(!initial.saved);
  // 保存函数引用（供保存按钮复用，避免闭包陈旧）
  const doSaveRef = useRef<() => void>(() => {});

  // === Inline Assistant 状态 ===
  const [iaVisible, setIaVisible] = useState(false);
  // 选区锚点矩形：top/bottom 用于浮窗上下翻转，left 为选区水平中心
  const [iaPosition, setIaPosition] = useState({ top: 0, bottom: 0, left: 0 });
  const [selectedText, setSelectedText] = useState('');
  const [selectedParaIds, setSelectedParaIds] = useState<string[]>([]);
  // 选中文字的编辑器内精确范围（用于应用 AI 结果）
  const [selectedRange, setSelectedRange] = useState<{ from: number; to: number } | null>(null);
  // 当前正在执行的 AI 操作（loading 态）
  const [iaLoading, setIaLoading] = useState<AiOperation | null>(null);
  // 划词浮窗的结果（流式预览 / 接受 / 拒绝 / 重试）
  const [iaPreview, setIaPreview] = useState('');
  const [iaError, setIaError] = useState<string | null>(null);
  // 修改范围：仅选区 / 当前句 / 当前段 / 全文（影响 AI 输入文本与替换范围）
  const [iaScope, setIaScope] = useState<ModificationScope>('selection');

  // === 多轮迭代润色会话 ===
  // 本次会话产生的所有版本（第 1 版 → 第 2 版 …），可来回切换、任选一版接受
  const [iaVersions, setIaVersions] = useState<PolishVersion[]>([]);
  // 当前正在查看的版本下标
  const [iaVersionIdx, setIaVersionIdx] = useState(0);
  // 是否与原文对比显示
  const [iaCompare, setIaCompare] = useState(false);
  // 会话上下文：首轮确定后固定复用，避免后续轮次因选区变化而错位
  const sessionRef = useRef<{
    op: AiOperation;
    sourceText: string;
    applyRange: { from: number; to: number };
    insertAt: number | null; // 续写用：插入位置
    options?: { targetLang?: string; tone?: string };
    ctxBefore: string;
    ctxAfter: string;
  } | null>(null);
  // 当前请求的中止句柄（供「停止」使用）
  const inlineCtrlRef = useRef<AgentController | null>(null);

  // 当前查看的版本
  const currentVersion = iaVersions[iaVersionIdx] ?? null;

  // === Chat Panel 状态 ===
  const [chatVisible, setChatVisible] = useState(false);

  // === 导出菜单状态 ===
  const [exportOpen, setExportOpen] = useState(false);
  const [exporting, setExporting] = useState<ExportFormat | null>(null);
  const exportMenuRef = useRef<HTMLDivElement>(null);

  // === 生成初稿 / 历史版本 / 另存为 ===
  const [draftOpen, setDraftOpen] = useState(false);
  const [historyOpen, setHistoryOpen] = useState(false);
  const [saveMenuOpen, setSaveMenuOpen] = useState(false);
  const [saveAsOpen, setSaveAsOpen] = useState(false);
  const [savingAs, setSavingAs] = useState(false);
  // 版本条数（仅用于顶栏角标，实际列表由面板自行读取）
  const [versionCount, setVersionCount] = useState(0);
  const saveMenuRef = useRef<HTMLDivElement>(null);
  // 文档是否已持久化（gen-/空 id 代表还没落库，历史版本需要先保存）
  const persisted = !!docId && !isGenId(docId);

  // === AI 操作统计 ===
  const [stats, setStats] = useState({ calls: 0, tokens: 0 });

  // === Hooks ===
  const diffMark = useDiffMark();
  const undo = useUndoSnapshot();
  const agent = useAgentTools();

  // === 编辑器就绪 ===
  const handleEditorReady = useCallback((editor: any) => {
    editorRef.current = editor;
    // 获取初始 token
    getToken().catch(() => {});
  }, []);

  // === 文档内容变化 ===
  const handleContentChange = useCallback(
    (json: Record<string, any>, html: string) => {
      setCurrentDocJson(json);
      setCurrentDocHtml(html);
    },
    [],
  );

  // === 实际保存逻辑 ===
  const doSave = useCallback(() => {
    if (saveState === 'saving') return;
    const editor = editorRef.current;
    const html = editor ? editor.getHTML() : currentDocHtml;
    const json = currentDocJson || (editor ? editor.getJSON() : null);
    const title = documentTitle?.trim() || '未命名文档';

    setSaveState('saving');
    const prevId = docId;
    // 用 setTimeout 让 "保存中" 状态至少有 1 帧渲染（模拟异步持久化）
    setTimeout(() => {
      try {
        const saved: LocalDoc = upsertDoc({
          id: isGenId(docId) ? undefined : docId,
          title,
          html,
          json: json || undefined,
        });
        // 首次保存时 gen- 临时 id 换成真实 id，把此前产生的版本（如生成初稿前备份）一并迁移过来
        if (prevId && prevId !== saved.id) migrateVersions(prevId, saved.id);
        lastSavedRef.current = { id: saved.id, title: saved.title, html: saved.html };
        isNewRef.current = false;
        setDocId(saved.id);
        // 把临时 gen- id 换成真实 id，刷新后仍是同一篇
        if (routeId !== saved.id) {
          navigate(`/editor-v2/${saved.id}`, { replace: true });
        }
        setSaveState('saved');
        notifyDocsChanged();
        toast('已保存到本地', 'success');
        // 若期间无新改动，1.5s 后回落到 idle
        setTimeout(() => {
          const dirty =
            isNewRef.current ||
            currentDocHtml !== lastSavedRef.current.html ||
            (documentTitle?.trim() || '未命名文档') !== lastSavedRef.current.title;
          if (!dirty) setSaveState('idle');
        }, 1500);
      } catch {
        setSaveState('idle');
        toast('保存失败', 'error');
      }
    }, 120);
  }, [saveState, currentDocHtml, currentDocJson, documentTitle, docId, routeId, navigate, toast]);

  // 保持最新 save 函数引用
  doSaveRef.current = doSave;

  // 标题变化
  const handleTitleChange = useCallback(
    (e: React.ChangeEvent<HTMLInputElement>) => setDocumentTitle(e.target.value),
    [],
  );

  // === 选区变化 → Inline Assistant 定位 ===
  const handleSelectionChange = useCallback(
    (paraIds: string[], range: { from: number; to: number }) => {
      // 生成过程中忽略选区抖动，避免正在流式输出的会话被中途重置
      if (inlineCtrlRef.current) return;

      if (paraIds.length === 0) {
        setIaVisible(false);
        return;
      }

      // 获取选区的屏幕位置
      const selection = window.getSelection();
      if (!selection || selection.isCollapsed) {
        setIaVisible(false);
        return;
      }

      const rangeRect = selection.getRangeAt(0).getBoundingClientRect();
      const selectedContent = selection.toString();

      if (selectedContent.trim().length === 0) {
        setIaVisible(false);
        return;
      }

      setSelectedText(selectedContent);
      setSelectedParaIds(paraIds);
      setSelectedRange(range);
      setIaPosition({
        top: rangeRect.top,
        bottom: rangeRect.bottom,
        left: rangeRect.left + rangeRect.width / 2,
      });
      // 切换选区 = 开启新会话：清空版本历史、重置范围为「仅选区」
      // （此处必然未在生成中，函数开头已提前返回）
      sessionRef.current = null;
      setIaScope('selection');
      setIaVersions([]);
      setIaVersionIdx(0);
      setIaCompare(false);
      setIaPreview('');
      setIaError(null);
      setIaLoading(null);
      setIaVisible(true);
    },
    [],
  );

  // === 划词 AI 统一入口：首轮 / 追加指令 / 换一版 ===
  const runInline = useCallback(
    (params: {
      operation: AiOperation;
      kind: VersionKind;
      instruction?: string;
      options?: { targetLang?: string; tone?: string };
    }) => {
      const { operation, kind } = params;
      const instruction = params.instruction || '';
      const isNew = kind === 'initial' || !sessionRef.current;

      // 首轮：按当前选区与「修改范围」建立会话上下文，之后各轮固定复用，
      // 避免用户在浮窗里操作时选区变动导致源文本 / 替换位置错位。
      if (isNew) {
        const editor = editorRef.current;
        if (!editor || !selectedRange) {
          toast('请先选中一段文字', 'error');
          return;
        }
        const range = selectedRange;
        let sourceText = selectedText;
        let applyRange: { from: number; to: number } = { from: range.from, to: range.to };
        let insertAt: number | null = null;
        let ctxBefore = '';
        let ctxAfter = '';
        try {
          const doc = editor.state.doc;
          if (operation === 'continue') {
            // 续写：始终插在选区之后，范围选择不适用
            insertAt = range.to;
            ctxBefore = doc.textBetween(Math.max(0, range.from - 600), range.from, '\n').trim();
            ctxAfter = doc.textBetween(range.to, Math.min(doc.content.size, range.to + 600), '\n').trim();
          } else {
            // 润色 / 翻译 / 语法 / 改写：按所选「修改范围」决定送给 AI 的文本与应用范围
            const resolved = resolveModificationScope(doc, range, iaScope);
            sourceText = resolved.sourceText;
            applyRange = resolved.applyRange;
          }
        } catch {
          /* ignore */
        }
        sessionRef.current = {
          op: operation,
          sourceText,
          applyRange,
          insertAt,
          options: params.options,
          ctxBefore,
          ctxAfter,
        };
        setIaVersions([]);
        setIaVersionIdx(0);
        setIaCompare(false);
      }

      const session = sessionRef.current;
      if (!session) return;

      // 已有版本 → 还原成多轮对话送回模型，让「再简洁一点」有明确的作用对象。
      // 换一版：只带到当前查看的那一版为止，等于"从这一版重新分叉"。
      const baseVersions = isNew ? [] : iaVersions;
      const history = isNew
        ? undefined
        : versionsToTurns(kind === 'retry' ? baseVersions.slice(0, iaVersionIdx + 1) : baseVersions);
      const newIdx = baseVersions.length;

      setIaLoading(session.op);
      setIaPreview('');
      setIaError(null);

      const opts: InlineEditOptions = {
        targetLang: session.options?.targetLang,
        tone: session.options?.tone,
        contextBefore: session.ctxBefore,
        contextAfter: session.ctxAfter,
        history,
        variation: kind === 'retry',
      };

      inlineCtrlRef.current?.cancel();
      inlineCtrlRef.current = deepseekInlineEdit(
        session.sourceText,
        session.op,
        instruction,
        {
          onToken: (_delta: string, full: string) => setIaPreview(full),
          onDone: (full: string) => {
            inlineCtrlRef.current = null;
            setIaLoading(null);
            const text = (full || '').trim();
            if (!text) {
              setIaError('AI 未返回内容，请重试');
              return;
            }
            const version = createVersion({
              text,
              op: session.op,
              kind: isNew ? 'initial' : kind,
              instruction,
              options: session.options,
            });
            setIaVersions((prev) => (isNew ? [version] : [...prev, version]));
            setIaVersionIdx(newIdx);
            setIaPreview('');
          },
          onError: (msg: string) => {
            inlineCtrlRef.current = null;
            setIaLoading(null);
            setIaError(msg);
          },
        },
        opts,
      );
    },
    [selectedText, selectedRange, iaScope, iaVersions, iaVersionIdx, toast],
  );

  // 浮窗主面板的五个操作 → 开启新会话
  const handleInlineAction = useCallback(
    (operation: AiOperation, customInstruction?: string, options?: { targetLang?: string; tone?: string }) => {
      runInline({ operation, kind: 'initial', instruction: customInstruction, options });
    },
    [runInline],
  );

  // 结果卡的「继续润色」：在当前版本基础上追加要求，产生下一版
  const handleRefineInline = useCallback(
    (instruction: string) => {
      const session = sessionRef.current;
      const text = instruction.trim();
      if (!session || !text) return;
      runInline({ operation: session.op, kind: 'refine', instruction: text, options: session.options });
    },
    [runInline],
  );

  // 结束会话并收起浮窗
  const closeInlineSession = useCallback(() => {
    inlineCtrlRef.current?.cancel();
    inlineCtrlRef.current = null;
    sessionRef.current = null;
    setIaVersions([]);
    setIaVersionIdx(0);
    setIaCompare(false);
    setIaPreview('');
    setIaError(null);
    setIaLoading(null);
    setIaVisible(false);
  }, []);

  // === 接受 / 放弃 / 换一版 / 停止 ===
  const handleAcceptInline = useCallback(() => {
    const editor = editorRef.current;
    const session = sessionRef.current;
    const version = iaVersions[iaVersionIdx];
    if (!editor || !session || !version) return;
    const text = version.text;
    const op = session.op;
    try {
      if (op === 'continue') {
        // 续写：插入到选区之后
        const at = session.insertAt ?? selectedRange?.to;
        if (at == null) return;
        editor.chain().focus().insertContentAt(at, ' ' + text).run();
      } else {
        // 其余操作：按所选「修改范围」替换（默认即选区）
        const r = session.applyRange;
        editor.chain().focus().insertContentAt({ from: r.from, to: r.to }, text).run();
      }
      const labelMap: Record<AiOperation, string> = {
        polish: '已润色',
        translate: '已翻译',
        'fix-grammar': '语法已修正',
        continue: '已续写',
        rewrite: '已改写',
      };
      const rounds = iaVersions.length > 1 ? `（第 ${iaVersionIdx + 1} 版）` : '';
      toast(labelMap[op] + rounds, 'success');
      setStats((prev) => ({
        calls: prev.calls + 1,
        tokens: prev.tokens + Math.max(1, Math.round(text.length / 1.6)),
      }));
    } catch {
      toast('应用修改失败，请重试', 'error');
    }
    closeInlineSession();
  }, [iaVersions, iaVersionIdx, selectedRange, toast, closeInlineSession]);

  const handleRejectInline = useCallback(() => {
    closeInlineSession();
  }, [closeInlineSession]);

  const handleRetryInline = useCallback(() => {
    const session = sessionRef.current;
    if (!session) return;
    // 首轮还没出结果（例如报错）→ 重新跑首轮；否则基于当前版本换一版
    if (iaVersions.length === 0) {
      runInline({ operation: session.op, kind: 'initial', options: session.options });
      return;
    }
    runInline({
      operation: session.op,
      kind: 'retry',
      instruction: iaVersions[iaVersionIdx]?.instruction || '',
      options: session.options,
    });
  }, [iaVersions, iaVersionIdx, runInline]);

  // 生成过程中「停止」：已流式产出的部分仍保留为一版，避免白等
  const handleStopInline = useCallback(() => {
    inlineCtrlRef.current?.cancel();
    inlineCtrlRef.current = null;
    setIaLoading(null);
    const partial = iaPreview.trim();
    if (!partial) return;
    const session = sessionRef.current;
    const idx = iaVersions.length;
    const version = {
      ...createVersion({
        text: partial,
        op: session?.op ?? 'polish',
        kind: idx === 0 ? ('initial' as const) : ('refine' as const),
        instruction: '',
        options: session?.options,
      }),
      label: '已停止',
    };
    setIaVersions((prev) => [...prev, version]);
    setIaVersionIdx(idx);
    setIaPreview('');
  }, [iaPreview, iaVersions.length]);

  // === Accept/Reject 处理 ===
  const handleAccept = useCallback(() => {
    // 简化：接受当前第一条待处理的建议
    const pendingIds = Array.from(diffMark.suggestions.keys()).filter(
      (id) => !diffMark.appliedChanges.has(id),
    );
    if (pendingIds.length > 0) {
      diffMark.acceptSuggestion(pendingIds[0]);
    }
  }, [diffMark]);

  const handleReject = useCallback(() => {
    const pendingIds = Array.from(diffMark.suggestions.keys()).filter(
      (id) => !diffMark.appliedChanges.has(id),
    );
    if (pendingIds.length > 0) {
      diffMark.rejectSuggestion(pendingIds[0]);
    }
  }, [diffMark]);

  const handleAcceptAll = useCallback(() => {
    const result = diffMark.acceptAll();
    // 应用所有修改到编辑器
    const editor = editorRef.current;
    if (editor && result) {
      Object.entries(result).forEach(([paraId, text]) => {
        // 通过编辑器 API 应用修改
        editor.commands.setContent(editor.getHTML()); // 简化：全量刷新
      });
    }
    undo.clearSnapshots();
  }, [diffMark, undo]);

  const handleRejectAll = useCallback(() => {
    const snapshot = undo.restoreFromSnapshot();
    const editor = editorRef.current;
    if (editor && snapshot) {
      editor.commands.setContent(snapshot);
    }
    diffMark.rejectAll();
  }, [diffMark, undo]);

  // === Chat Panel 消息 ===
  const handleSendChat = useCallback(
    (message: string) => {
      // 把当前文档正文与标题作为上下文传给 AI 助手，使其能回答文档相关问题
      agent.sendMessage(message, {
        html: currentDocHtml,
        title: documentTitle?.trim() || '未命名文档',
      });
    },
    [agent, currentDocHtml, documentTitle],
  );

  const handleInsertToDoc = useCallback(
    (content: string) => {
      const editor = editorRef.current;
      if (editor) {
        // 在光标位置插入聊天结果的纯文本版
        const div = document.createElement('div');
        div.innerHTML = content;
        const plainText = div.textContent || content;
        editor.chain().focus().insertContent(plainText).run();
      }
    },
    [],
  );

  // === 导出当前文档 ===
  const handleExport = useCallback(
    async (format: ExportFormat) => {
      setExportOpen(false);
      const editor = editorRef.current;
      const html = editor ? editor.getHTML() : currentDocHtml;
      const title = documentTitle?.trim() || '未命名文档';
      setExporting(format);
      try {
        await exportDocument(html, title, format);
        if (format === 'pdf') {
          toast('已打开打印窗口，请在对话框中选择「另存为 PDF」', 'success');
        } else {
          toast(`已导出为 .${format}`, 'success');
        }
      } catch (e) {
        toast((e as Error)?.message || '导出失败', 'error');
      } finally {
        setExporting(null);
      }
    },
    [currentDocHtml, documentTitle, toast],
  );

  // === 生成初稿：把 AI 写出的初稿写入编辑器 ===
  const handleApplyDraft = useCallback(
    (html: string, mode: ApplyMode) => {
      const editor = editorRef.current;
      if (!editor) return;
      try {
        if (mode === 'replace') {
          // 覆盖前先把原内容存一版，误操作可从历史版本恢复
          const original = editor.getHTML();
          if (original && original.replace(/<[^>]*>/g, '').trim()) {
            recordVersion({
              docId: docId || 'draft-temp',
              title: documentTitle?.trim() || '未命名文档',
              html: original,
              reason: 'draft',
              note: '生成初稿前的内容',
            });
          }
          editor.commands.setContent(html);
          toast('初稿已写入，原内容已存为历史版本', 'success');
        } else {
          // 追加到末尾
          editor.chain().focus().setTextSelection(editor.state.doc.content.size).insertContent(html).run();
          toast('初稿已追加到文末', 'success');
        }
        setStats((prev) => ({
          calls: prev.calls + 1,
          tokens: prev.tokens + Math.max(1, Math.round(html.length / 1.6)),
        }));
      } catch {
        toast('写入失败，请重试', 'error');
      }
      setDraftOpen(false);
    },
    [docId, documentTitle, toast],
  );

  // === 历史版本：手动快照 / 恢复 / 删除 ===
  const handleSnapshot = useCallback(() => {
    if (!persisted) {
      toast('请先保存文档，再创建版本', 'error');
      return;
    }
    const editor = editorRef.current;
    const html = editor ? editor.getHTML() : currentDocHtml;
    const created = recordVersion({
      docId,
      title: documentTitle?.trim() || '未命名文档',
      html,
      reason: 'snapshot',
    });
    toast(created ? '已存为一个版本' : '内容与最新版本相同，无需重复保存', created ? 'success' : 'info');
  }, [persisted, docId, documentTitle, currentDocHtml, toast]);

  const handleRestoreVersion = useCallback(
    (version: DocVersion) => {
      const editor = editorRef.current;
      if (!editor) return;
      // 恢复本身也可回退：先把当前内容存一版
      recordVersion({
        docId,
        title: documentTitle?.trim() || '未命名文档',
        html: editor.getHTML(),
        reason: 'restore',
        note: '恢复前的内容',
      });
      editor.commands.setContent(version.html);
      setDocumentTitle(version.title || '未命名文档');
      toast('已恢复到该版本，记得点「保存」确认', 'success');
    },
    [docId, documentTitle, toast],
  );

  const handleDeleteVersion = useCallback(
    (version: DocVersion) => {
      deleteVersion(version.docId || docId, version.id);
      toast('已删除该版本', 'success');
    },
    [docId, toast],
  );

  // === 保存为模板：把当前内容沉淀成可复用的"我的模板" ===
  const handleSaveAsTemplate = useCallback(() => {
    const editor = editorRef.current;
    const html = editor ? editor.getHTML() : currentDocHtml;
    const title = documentTitle?.trim() || '未命名文档';
    if (!html || html.replace(/<[^>]*>/g, '').trim().length === 0) {
      toast('文档为空，无法存为模板', 'warning');
      return;
    }
    addTemplate({ title, html, category: '我的模板' });
    toast(`已保存为模板「${title}」`, 'success');
    setSaveMenuOpen(false);
  }, [documentTitle, currentDocHtml, toast]);

  // === 另存为：复制成一篇全新文档 ===
  const handleSaveAs = useCallback(
    (title: string, openCopy: boolean) => {
      const editor = editorRef.current;
      const html = editor ? editor.getHTML() : currentDocHtml;
      const json = currentDocJson || (editor ? editor.getJSON() : null);
      setSavingAs(true);
      setTimeout(() => {
        try {
          const copy = saveAsDoc({ title, html, json: json || undefined });
          notifyDocsChanged();
          setSaveAsOpen(false);
          if (openCopy) {
            // 切到副本：同步内部状态，避免仍按原文档的脏标记提示
            lastSavedRef.current = { id: copy.id, title: copy.title, html: copy.html };
            isNewRef.current = false;
            setDocId(copy.id);
            setDocumentTitle(copy.title);
            setSaveState('idle');
            navigate(`/editor-v2/${copy.id}`, { replace: true });
            toast(`已另存为「${copy.title}」并切换到副本`, 'success');
          } else {
            toast(`已另存为「${copy.title}」，当前仍在原文档`, 'success');
          }
        } catch {
          toast('另存为失败', 'error');
        } finally {
          setSavingAs(false);
        }
      }, 100);
    },
    [currentDocHtml, currentDocJson, navigate, toast],
  );

  // === 版本数角标：随版本仓库变化刷新 ===
  useEffect(() => {
    const refresh = () => setVersionCount(persisted ? countVersions(docId) : 0);
    refresh();
    window.addEventListener(DOC_VERSIONS_EVENT, refresh);
    return () => window.removeEventListener(DOC_VERSIONS_EVENT, refresh);
  }, [docId, persisted]);

  // === 脏标记（仅标记未保存，不再自动保存，需手动点保存） ===
  useEffect(() => {
    const dirty =
      isNewRef.current ||
      currentDocHtml !== lastSavedRef.current.html ||
      (documentTitle?.trim() || '未命名文档') !== lastSavedRef.current.title;
    setSaveState((s) => (s === 'saving' ? s : dirty ? 'dirty' : 'idle'));
  }, [currentDocHtml, documentTitle]);

  // === 离开页面前若有未保存改动，弹出浏览器确认 ===
  useEffect(() => {
    const handler = (e: BeforeUnloadEvent) => {
      const dirty =
        isNewRef.current ||
        currentDocHtml !== lastSavedRef.current.html ||
        (documentTitle?.trim() || '未命名文档') !== lastSavedRef.current.title;
      if (dirty) {
        e.preventDefault();
        e.returnValue = '';
      }
    };
    window.addEventListener('beforeunload', handler);
    return () => window.removeEventListener('beforeunload', handler);
  }, [currentDocHtml, documentTitle]);

  // 点击导出菜单外部时关闭
  useEffect(() => {
    if (!exportOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (exportMenuRef.current && !exportMenuRef.current.contains(e.target as Node)) {
        setExportOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [exportOpen]);

  // 点击保存下拉外部时关闭
  useEffect(() => {
    if (!saveMenuOpen) return;
    const onDocClick = (e: MouseEvent) => {
      if (saveMenuRef.current && !saveMenuRef.current.contains(e.target as Node)) {
        setSaveMenuOpen(false);
      }
    };
    document.addEventListener('mousedown', onDocClick);
    return () => document.removeEventListener('mousedown', onDocClick);
  }, [saveMenuOpen]);

  // 保存按钮文案 / 图标
  const saveLabel =
    saveState === 'saving'
      ? '保存中…'
      : saveState === 'saved'
        ? '已保存'
        : isNewRef.current
          ? '保存'
          : '保存';

  return (
    <div className="document-editor-v2">
      {/* 顶栏 */}
      <header className="dv2-header">
        <div className="dv2-header-left">
          <input
            className="dv2-title-input"
            value={documentTitle}
            onChange={handleTitleChange}
            placeholder="未命名文档"
          />
        </div>
        <div className="dv2-header-right">
          {/* 保存状态指示 */}
          <span className={`dv2-save-state dv2-save-${saveState}`}>
            {saveState === 'saving' && <span className="dv2-spinner" />}
            {saveState === 'saved' && <Check theme="outline" size="13" />}
            {saveState === 'dirty' && <span className="dv2-dot" />}
            {saveState === 'idle' && <span className="dv2-dot dv2-dot-clean" />}
            {saveState === 'saving'
              ? '保存中'
              : saveState === 'saved'
                ? '已保存'
                : isNewRef.current
                  ? '未保存'
                  : '已保存'}
          </span>

          {/* 次级操作：统一中性幽灵按钮，降低视觉噪音 */}
          <div className="dv2-header-tools">
            {/* 生成初稿 */}
            <button
              className="dv2-draft-btn"
              onClick={() => setDraftOpen(true)}
              title="用 AI 生成一份完整初稿"
            >
              <MagicWand theme="outline" size="15" />
              生成初稿
            </button>

            {/* 历史版本 */}
            <button
              className={`dv2-history-btn ${historyOpen ? 'active' : ''}`}
              onClick={() => setHistoryOpen((v) => !v)}
              title="查看历史版本"
            >
              <Time theme="outline" size="15" />
              历史版本
              {versionCount > 0 && <span className="dv2-history-count">{versionCount}</span>}
            </button>

            {/* 导出菜单 */}
            <div className="dv2-export" ref={exportMenuRef}>
              <button
                className={`dv2-export-btn ${exportOpen ? 'active' : ''}`}
                onClick={() => setExportOpen((v) => !v)}
                disabled={exporting !== null}
                title="导出文档（可选格式）"
              >
                <Export theme="outline" size="15" />
                导出
              </button>
              {exportOpen && (
                <div className="dv2-export-menu" role="menu">
                  {EXPORT_OPTIONS.map((opt) => (
                    <button
                      key={opt.format}
                      className="dv2-export-item"
                      role="menuitem"
                      onClick={() => handleExport(opt.format)}
                      disabled={exporting !== null}
                    >
                      <span className="dv2-export-item-label">
                        {exporting === opt.format ? '导出中…' : opt.label}
                      </span>
                      <span className="dv2-export-item-desc">{opt.desc}</span>
                    </button>
                  ))}
                </div>
              )}
            </div>

            <button
              className={`dv2-chat-toggle ${chatVisible ? 'active' : ''}`}
              onClick={() => setChatVisible(!chatVisible)}
              title="AI 助手面板"
            >
              <Robot theme="outline" size="15" />AI 助手
            </button>
          </div>

          <span className="dv2-header-divider" />

          {/* 保存（带下拉：另存为 / 存为版本）——仅保留的唯一主按钮 */}
          <div className="dv2-save-group" ref={saveMenuRef}>
            <button
              className="dv2-save-btn"
              onClick={() => doSaveRef.current()}
              disabled={saveState === 'saving'}
              title="保存到本地（需手动点击）"
            >
              {saveState === 'saving' ? (
                <span className="dv2-spinner dv2-spinner-btn" />
              ) : (
                <Save theme="outline" size="15" />
              )}
              {saveLabel}
            </button>
            <button
              className={`dv2-save-caret ${saveMenuOpen ? 'active' : ''}`}
              onClick={() => setSaveMenuOpen((v) => !v)}
              disabled={saveState === 'saving'}
              title="更多保存方式"
              aria-label="更多保存方式"
            >
              <Down theme="outline" size="13" />
            </button>
            {saveMenuOpen && (
              <div className="dv2-save-menu" role="menu">
                <button
                  className="dv2-save-menu-item"
                  role="menuitem"
                  onClick={() => {
                    setSaveMenuOpen(false);
                    setSaveAsOpen(true);
                  }}
                >
                  <span className="dv2-save-menu-label">另存为…</span>
                  <span className="dv2-save-menu-desc">复制成一篇新文档，原文档不变</span>
                </button>
                <button
                  className="dv2-save-menu-item"
                  role="menuitem"
                  onClick={() => {
                    setSaveMenuOpen(false);
                    handleSnapshot();
                  }}
                >
                  <span className="dv2-save-menu-label">存为历史版本</span>
                  <span className="dv2-save-menu-desc">给当前内容打一个可回溯的快照</span>
                </button>
                <button
                  className="dv2-save-menu-item"
                  role="menuitem"
                  onClick={handleSaveAsTemplate}
                >
                  <span className="dv2-save-menu-label">保存为模板</span>
                  <span className="dv2-save-menu-desc">沉淀成"我的模板"，下次一键复用</span>
                </button>
              </div>
            )}
          </div>
        </div>
      </header>

      {/* Accept/Reject 栏 */}
      <AcceptRejectBar
        visible={diffMark.isPreviewMode}
        pendingCount={diffMark.pendingCount}
        totalCount={diffMark.suggestions.size}
        currentIndex={0}
        onAccept={handleAccept}
        onReject={handleReject}
        onAcceptAll={handleAcceptAll}
        onRejectAll={handleRejectAll}
        onPrev={() => {}}
        onNext={() => {}}
      />

      {/* 主体区域 */}
      <div className={`dv2-main ${chatVisible ? 'chat-open' : ''} ${historyOpen ? 'history-open' : ''}`}>
        <div className="dv2-editor-area">
          <TiptapEditor
            initialHtml={initial.html}
            placeholder="开始输入文档内容... 选中文字触发 AI 润色浮窗"
            showToolbar={true}
            onContentChange={handleContentChange}
            onSelectionChange={handleSelectionChange}
            onEditorReady={handleEditorReady}
            readonly={diffMark.isPreviewMode}
            editorRef={editorRef}
          />
        </div>

        {/* Inline Assistant 浮窗 */}
        <InlineAssistant
          visible={iaVisible}
          position={iaPosition}
          selectedText={selectedText}
          paraIds={selectedParaIds}
          loading={iaLoading}
          previewText={iaPreview}
          resultText={currentVersion?.text ?? null}
          errorText={iaError}
          onAction={handleInlineAction}
          onAccept={handleAcceptInline}
          onReject={handleRejectInline}
          onRetry={handleRetryInline}
          onDismiss={closeInlineSession}
          modificationScope={iaScope}
          onScopeChange={setIaScope}
          versions={iaVersions}
          versionIndex={iaVersionIdx}
          onVersionChange={setIaVersionIdx}
          onRefine={handleRefineInline}
          onStop={handleStopInline}
          originalText={sessionRef.current?.sourceText ?? ''}
          compare={iaCompare}
          onToggleCompare={() => setIaCompare((v) => !v)}
        />

        {/* 历史版本抽屉 */}
        <VersionHistoryPanel
          visible={historyOpen}
          docId={docId}
          persisted={persisted}
          currentHtml={currentDocHtml}
          onClose={() => setHistoryOpen(false)}
          onRestore={handleRestoreVersion}
          onDelete={handleDeleteVersion}
          onSnapshot={handleSnapshot}
        />

        {/* Chat Panel 侧边栏 */}
        <ChatPanel
          visible={chatVisible}
          messages={agent.messages}
          isProcessing={agent.isProcessing}
          onSendMessage={handleSendChat}
          onStop={agent.stop}
          onClear={agent.clearConversation}
          onInsertToDocument={handleInsertToDoc}
          onClose={() => setChatVisible(false)}
        />
      </div>

      {/* 生成初稿 */}
      <DraftGeneratorModal
        visible={draftOpen}
        defaultTopic={documentTitle}
        hasContent={!!currentDocHtml.replace(/<[^>]*>/g, '').trim()}
        onClose={() => setDraftOpen(false)}
        onApply={handleApplyDraft}
      />

      {/* 另存为 */}
      <SaveAsModal
        visible={saveAsOpen}
        currentTitle={documentTitle}
        dirty={saveState === 'dirty'}
        saving={savingAs}
        onClose={() => setSaveAsOpen(false)}
        onConfirm={handleSaveAs}
      />
    </div>
  );
};

export default DocumentEditorV2;
