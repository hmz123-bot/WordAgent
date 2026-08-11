import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import {
  H1,
  AlignTextLeft,
  Table,
  ListMiddle,
  RectangleOne,
  List,
  Pic,
} from '@icon-park/react';
import type { IconComp } from '../types/icon';
import { useToast } from '../components/Toast';
import { useLocalStorageState, LSKeys } from '../hooks/useLocalStorage';
import {
  getDocument,
  getDocumentNodes,
  getVersionHistory,
  getChangeSets,
  updateNodeText,
  deleteDocument,
  createChangeSet,
  submitChangeSet,
  acceptChangeSet,
  rejectChangeSet,
  deleteChangeSet,
  aiEdit,
  getAiStatus,
  saveDocument,
  updateDocumentStatus,
  DocumentAsset,
  DocumentNode,
  DocumentVersion,
  ChangeSet,
  AiEditResponse,
  AiSuggestion,
  AiStatus,
  TableFormat,
  TableRowProperties,
  TableCellProperties,
  NodeUpdate,
  SaveResult,
} from '../services/api';

// ========== 工具函数 ==========

function formatTime(iso: string): string {
  const d = new Date(iso);
  const month = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  const h = d.getHours().toString().padStart(2, '0');
  const m = d.getMinutes().toString().padStart(2, '0');
  return `${month}月${day}日 ${h}:${m}`;
}

function getNodeTypeName(node: DocumentNode): string {
  return node.nodeType ? node.nodeType.toLowerCase() : '';
}

function getHeadingLevel(node: DocumentNode): number | undefined {
  if (node.styleId) {
    const match = node.styleId.match(/heading(\d+)/i);
    if (match) return parseInt(match[1], 10);
  }
  if (node.attributes && typeof node.attributes.level === 'number') {
    return node.attributes.level as number;
  }
  return undefined;
}

/** 大纲树节点图标 —— IconPark 组件映射 */
function getNodeIcon(node: DocumentNode): React.ReactElement {
  const type = getNodeTypeName(node);
  const map: Record<string, IconComp> = {
    heading: H1,
    paragraph: AlignTextLeft,
    table: Table,
    table_row: ListMiddle,
    table_cell: RectangleOne,
    list: List,
    image: Pic,
  };
  const Icon = map[type] || AlignTextLeft;
  return <Icon theme="outline" size="13" />;
}

function cellNodeText(node: DocumentNode): string {
  if (node.text) return node.text;
  // 拼接子节点的文本
  if (node.children) {
    return node.children.map(c => c.text || '').join('').trim();
  }
  return '';
}

function getNodeLabel(node: DocumentNode): string {
  const type = getNodeTypeName(node);
  if (type === 'heading') {
    const level = getHeadingLevel(node);
    if (level) return `H${level}`;
    return '标题';
  }
  const map: Record<string, string> = {
    heading: '标题',
    paragraph: '段落',
    table: '表格',
    table_row: '行',
    table_cell: '单元格',
    list: '列表',
    image: '图片',
  };
  return map[type] || node.nodeType || type;
}

function getFileNameWithoutExt(name: string): string {
  return name.replace(/\.(docx|doc|pdf|txt)$/i, '');
}

function getStatusLabel(status: string): string {
  const map: Record<string, string> = {
    IMPORTING: '导入中',
    DRAFT: '编辑中',
    READY: '已发布',
    EDITING: '编辑中',
    REVIEWING: '审核中',
    EXPORTING: '导出中',
    ERROR: '错误',
    ARCHIVED: '已归档',
  };
  return map[status] || status;
}

function getStatusTagClass(status: string): string {
  const map: Record<string, string> = {
    READY: 'tag-success',
    DRAFT: 'tag-warning',
    EDITING: 'tag-warning',
    REVIEWING: 'tag-info',
    ARCHIVED: 'tag-default',
    ERROR: 'tag-error',
  };
  return map[status] || 'tag-default';
}

function getReviewStatusLabel(status: string): string {
  const map: Record<string, string> = {
    PENDING: '草稿',
    SUBMITTED: '待审阅',
    ACCEPTED: '已通过',
    REJECTED: '已拒绝',
    FAILED: '失败',
  };
  return map[status] || status;
}

function getReviewStatusClass(status: string): string {
  const map: Record<string, string> = {
    PENDING: 'tag tag-default',
    SUBMITTED: 'tag tag-warning',
    ACCEPTED: 'tag tag-success',
    REJECTED: 'tag tag-error',
    FAILED: 'tag tag-error',
  };
  return map[status] || 'tag tag-default';
}

// ========== 节点树渲染 ==========

interface TreeNodeProps {
  node: DocumentNode;
  depth: number;
}

const TreeNode: React.FC<TreeNodeProps> = ({ node, depth }) => {
  const hasChildren = node.children && node.children.length > 0;
  const type = getNodeTypeName(node);
  let displayText = node.text;
  if (type === 'table') {
    displayText = displayText || `${node.children?.length || 0} 行`;
  } else if (type === 'table_row') {
    displayText = displayText || `${node.children?.length || 0} 个单元格`;
  } else if (type === 'table_cell') {
    const firstChild = node.children?.[0];
    displayText = firstChild?.text || displayText || '(空)';
  }
  return (
    <div className="outline-node" style={{ paddingLeft: depth * 16 }}>
      <div className="outline-node-header">
        <span className="outline-node-icon">{getNodeIcon(node)}</span>
        <span className="outline-node-label">{getNodeLabel(node)}</span>
        <span className="outline-node-text">{displayText || '(空)'}</span>
      </div>
      {hasChildren && node.children.map((child) => (
        <TreeNode key={child.nodeId} node={child} depth={depth + 1} />
      ))}
    </div>
  );
};

// ========== 主组件 ==========

const DocumentEditor: React.FC = () => {
  const { id } = useParams<{ id: string }>();
  const navigate = useNavigate();
  const { toast } = useToast();

  // 文档基本信息
  const [document, setDocument] = useState<DocumentAsset | null>(null);
  const [docLoading, setDocLoading] = useState(true);
  const [docError, setDocError] = useState<string | null>(null);

  // 大纲
  const [nodes, setNodes] = useState<DocumentNode[]>([]);
  const [nodesLoading, setNodesLoading] = useState(true);

  // 版本历史
  const [versions, setVersions] = useState<DocumentVersion[]>([]);
  const [versionsLoading, setVersionsLoading] = useState(true);

  // 变更集
  const [changeSets, setChangeSets] = useState<ChangeSet[]>([]);
  const [changeSetsLoading, setChangeSetsLoading] = useState(true);

  // Tab（持久化：记住上次使用的标签页）
  const [activeTab, setActiveTab] = useLocalStorageState<'edit' | 'preview' | 'changeset' | 'version'>(
    LSKeys.editorActiveTab(id || 'unknown'), 'edit'
  );

  // 编辑状态（持久化：防止意外刷新丢失修改）
  const [modifiedNodes, setModifiedNodes] = useLocalStorageState<Record<string, string>>(
    LSKeys.editorNodes(id || 'unknown'), {}
  );
  // Ref 始终指向最新的 modifiedNodes，避免 handleSave 闭包过期问题
  const modifiedNodesRef = useRef(modifiedNodes);
  modifiedNodesRef.current = modifiedNodes;
  const [isSaving, setIsSaving] = useState(false);

  // AI 面板状态
  const [aiPanelOpen, setAiPanelOpen] = useLocalStorageState<boolean>(
    LSKeys.editorAIPanel(id || 'unknown'), false
  );
  const [aiInstruction, setAiInstruction] = useLocalStorageState<string>(
    LSKeys.editorAIInstruction(id || 'unknown'), ''
  );
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState<AiEditResponse | null>(null);
  const [aiError, setAiError] = useState<string | null>(null);
  const [aiStatus, setAiStatus] = useState<AiStatus | null>(null);
  const [aiExpanded, setAiExpanded] = useLocalStorageState<Record<number, boolean>>(
    LSKeys.editorAIExpanded(id || 'unknown'), {}
  );

  const handleTextChange = useCallback((nodeId: string, text: string) => {
    // 先立即更新 ref —— 在 React 18 中 setState updater 是异步执行的，
    // 在 blur → click 同一事件循环中，必须先更新 ref 才能被 handleSave 读到
    const next = { ...modifiedNodesRef.current, [nodeId]: text };
    if (next[nodeId] === modifiedNodesRef.current[nodeId]) return; // 文本没变就跳过
    modifiedNodesRef.current = next;
    setModifiedNodes(next);
  }, []);

  // ========== AI 编辑 ==========

  const quickPrompts = [
    { label: '加粗标题', icon: 'B', instruction: '将文档中所有标题加粗' },
    { label: '翻译英文', icon: 'T', instruction: '将文档内容翻译为英文，保持格式' },
    { label: '优化排版', icon: 'P', instruction: '优化文档排版，改善段落间距和对齐，统一标题层级' },
    { label: '润色文本', icon: 'R', instruction: '润色文本内容，修正语法错误，改善表达流畅度' },
    { label: '扩写段落', icon: 'E', instruction: '对内容较短的段落进行合理扩写，丰富细节' },
    { label: '精简内容', icon: 'S', instruction: '精简文档内容，去除冗余表达，保持核心信息' },
  ];

  const buildDocumentContextText = useCallback((): string => {
    if (!nodes) return '';
    const walk = (nodeList: DocumentNode[], depth = 0): string[] => {
      const lines: string[] = [];
      for (const n of nodeList) {
        const indent = '  '.repeat(depth);
        lines.push(`${indent}[${n.nodeType}] id=${n.nodeId} | text=${n.text || '(空)'}`);
        if (n.children?.length) {
          lines.push(...walk(n.children, depth + 1));
        }
      }
      return lines;
    };
    return walk(nodes).join('\n');
  }, [nodes]);

  const handleAiEdit = useCallback(async (instruction: string) => {
    if (!id || !instruction.trim()) return;
    setAiLoading(true);
    setAiError(null);
    setAiResult(null);
    try {
      const context = buildDocumentContextText();
      const res = await aiEdit(id, instruction, context);
      if (res.data) {
        setAiResult(res.data);
        setAiExpanded({});
      }
    } catch (err) {
      const msg = err instanceof Error ? err.message : 'AI 编辑失败';
      setAiError(msg);
    } finally {
      setAiLoading(false);
    }
  }, [id, buildDocumentContextText]);

  const handleQuickPrompt = useCallback(async (instruction: string) => {
    setAiInstruction(instruction);
    await handleAiEdit(instruction);
  }, [handleAiEdit]);

  const handleAiEditAndApply = useCallback(async () => {
    if (!id || !aiResult || aiResult.suggestions.length === 0) return;
    setAiLoading(true);
    setAiError(null);
    try {
      // 逐个应用建议修改
      let appliedCount = 0;
      const failedNodes: { nodeId: string; reason: string }[] = [];
      const skippedNodes: string[] = [];

      for (const suggestion of aiResult.suggestions) {
        if (!suggestion.operation || suggestion.operation !== 'replace_text') {
          skippedNodes.push(`${suggestion.nodeId || '?'} (操作类型: ${suggestion.operation || '无'})`);
          continue;
        }
        if (!suggestion.nodeId) {
          skippedNodes.push('(缺少 nodeId)');
          continue;
        }
        try {
          await updateNodeText(id, suggestion.nodeId, suggestion.suggestedText);
          appliedCount++;
        } catch (e) {
          const reason = e instanceof Error ? e.message : String(e);
          failedNodes.push({ nodeId: suggestion.nodeId, reason });
        }
      }

      // 刷新节点树
      const nodesRes = await getDocumentNodes(id);
      if (nodesRes.data) {
        setNodes(nodesRes.data);
      }
      setAiResult(null);
      setAiInstruction('');

      // 构建详细的反馈信息
      let msg = `AI 编辑完成：成功 ${appliedCount} 条`;
      if (failedNodes.length > 0) {
        msg += `，失败 ${failedNodes.length} 条`;
        const detail = failedNodes.map(f => `  ${f.nodeId}: ${f.reason}`).join('\n');
        console.warn('应用 AI 建议失败详情:\n' + detail);
        setAiError(`应用失败 ${failedNodes.length} 条，请检查浏览器控制台查看详情`);
      }
      if (skippedNodes.length > 0) {
        msg += `，跳过 ${skippedNodes.length} 条（操作类型不匹配）`;
      }
      toast(msg, appliedCount > 0 ? 'success' : 'info');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '应用 AI 编辑失败';
      setAiError(msg);
    } finally {
      setAiLoading(false);
    }
  }, [id, aiResult]);

  const toggleSuggestion = useCallback((index: number) => {
    setAiExpanded(prev => ({ ...prev, [index]: !prev[index] }));
  }, []);

  // ========== 变更集操作 ==========
  const [csActionLoading, setCsActionLoading] = useState<string | null>(null); // 跟踪哪个变更集正在操作

  const refreshChangeSets = useCallback(async () => {
    if (!id) return;
    const res = await getChangeSets(id);
    setChangeSets(res.data || []);
  }, [id]);

  /** 保存为草稿 — 将 modifiedNodes 创建成一个变更集 */
  const handleCreateDraft = useCallback(async () => {
    if (!id) return;
    // 使用 ref 避免闭包过期问题（编辑后立刻点"保存为草稿"时 state 尚未更新）
    const currentNodes = modifiedNodesRef.current;
    const entries = Object.entries(currentNodes);
    if (entries.length === 0) {
      toast('没有未保存的修改', 'info');
      return;
    }
    console.log('[handleCreateDraft] id:', id, 'entries:', entries);
    setCsActionLoading('__draft__');
    try {
      const changes = entries.map(([nodeId, text]) => ({
        changeId: crypto.randomUUID?.() || `${Date.now()}-${nodeId}`,
        operation: 'REPLACE_TEXT',
        targetNodeId: nodeId,
        targetNodeType: 'paragraph',
        oldValue: { text: '' },
        newValue: { text },
        position: null,
        context: null,
      }));

      // 自动生成摘要：基于编辑的节点数量和类型
      const nodeNames = entries.map(([nodeId]) => nodeId).slice(0, 3);
      const summary = entries.length > 3
        ? `编辑了 ${nodeNames.join('、')} 等 ${entries.length} 处内容`
        : `编辑了 ${nodeNames.join('、')}`;

      await createChangeSet(id, summary, changes);
      await refreshChangeSets();
      setModifiedNodes({});
      modifiedNodesRef.current = {};
      toast(`已创建草稿，包含 ${changes.length} 处修改`, 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '创建草稿失败';
      toast(msg, 'error');
    } finally {
      setCsActionLoading(null);
    }
  }, [id, refreshChangeSets, toast]);

  /** 提审 */
  const handleSubmitCS = useCallback(async (csId: string) => {
    if (!id) return;
    setCsActionLoading(csId);
    try {
      await submitChangeSet(id, csId);
      await refreshChangeSets();
      toast('已提审', 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '提审失败';
      toast(msg, 'error');
    } finally {
      setCsActionLoading(null);
    }
  }, [id, refreshChangeSets, toast]);

  /** 接受合并 */
  const handleAcceptCS = useCallback(async (csId: string) => {
    if (!id) return;
    setCsActionLoading(csId);
    try {
      await acceptChangeSet(id, csId);
      // 合并后刷新节点树 + 变更集
      const nodesRes = await getDocumentNodes(id);
      if (nodesRes.data) setNodes(nodesRes.data);
      await refreshChangeSets();
      toast('已接受变更，文档已更新', 'success');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '接受失败';
      toast(msg, 'error');
    } finally {
      setCsActionLoading(null);
    }
  }, [id, refreshChangeSets, toast]);

  /** 拒绝 */
  const [rejectReasonOpen, setRejectReasonOpen] = useState<string | null>(null);
  const [rejectReason, setRejectReason] = useState('');

  const handleRejectCS = useCallback(async (csId: string) => {
    setRejectReasonOpen(csId);
    setRejectReason('');
  }, []);

  const confirmReject = useCallback(async () => {
    const csId = rejectReasonOpen;
    if (!id || !csId) return;
    setCsActionLoading(csId);
    setRejectReasonOpen(null);
    try {
      await rejectChangeSet(id, csId, rejectReason || undefined);
      await refreshChangeSets();
      toast('已拒绝变更集', 'warning');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '拒绝操作失败';
      toast(msg, 'error');
    } finally {
      setCsActionLoading(null);
    }
  }, [id, rejectReasonOpen, rejectReason, refreshChangeSets, toast]);

  /** 删除变更集 */
  const handleDeleteCS = useCallback(async (csId: string) => {
    if (!id) return;
    if (!window.confirm('确定删除此变更集吗？')) return;
    setCsActionLoading(csId);
    try {
      await deleteChangeSet(id, csId);
      await refreshChangeSets();
      toast('变更集已删除', 'info');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '删除失败';
      toast(msg, 'error');
    } finally {
      setCsActionLoading(null);
    }
  }, [id, refreshChangeSets, toast]);

  /** 删除文档 */
  const [deleteConfirmOpen, setDeleteConfirmOpen] = useState(false);
  const handleDeleteDocument = useCallback(async () => {
    if (!id) return;
    setCsActionLoading('__delete_doc__');
    try {
      await deleteDocument(id);
      toast('文档已删除', 'success');
      navigate('/documents');
    } catch (err) {
      const msg = err instanceof Error ? err.message : '删除失败';
      toast(msg, 'error');
      setCsActionLoading(null);
    }
  }, [id, navigate, toast]);

  // ========== 加载数据 ==========
  const loadData = useCallback(async () => {
    if (!id) return;

    setDocLoading(true);
    setDocError(null);
    try {
      const [docRes, nodesRes, versionsRes, csRes] = await Promise.all([
        getDocument(id),
        getDocumentNodes(id),
        getVersionHistory(id),
        getChangeSets(id),
      ]);
      setDocument(docRes.data);
      setNodes(nodesRes.data || []);
      setVersions(versionsRes.data || []);
      setChangeSets(csRes.data || []);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : '加载文档失败';
      setDocError(msg);
    } finally {
      setDocLoading(false);
      setNodesLoading(false);
      setVersionsLoading(false);
      setChangeSetsLoading(false);
    }
  }, [id]);

  useEffect(() => {
    loadData();
  }, [loadData]);

  // 进入编辑器时，将已发布文档切换为编辑中状态
  useEffect(() => {
    if (document && (document.status === 'READY' || document.status === 'ARCHIVED')) {
      updateDocumentStatus(document.documentId, 'EDITING').catch(() => {});
      setDocument((prev) => prev ? { ...prev, status: 'EDITING' } : prev);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [document?.documentId]);

  // ========== 保存编辑 ==========
  const handleSave = useCallback(async () => {
    if (!id || isSaving) return;
    const currentNodes = modifiedNodesRef.current;
    const entries = Object.entries(currentNodes);
    if (entries.length === 0) {
      console.log('[handleSave] 没有修改需要保存');
      return;
    }
    console.log('[handleSave] id:', id, 'entries:', entries);

    setIsSaving(true);
    try {
      const changes: NodeUpdate[] = entries.map(([nodeId, text]) => ({ nodeId, text }));
      const response = await saveDocument(id, changes);
      const result: SaveResult = response.data!;

      if (result.success) {
        setModifiedNodes({});
        modifiedNodesRef.current = {};
        await loadData();
        toast(`已保存！版本 v${result.newVersion}`, 'success');
      } else {
        toast(result.errorMessage || '保存失败', 'error');
      }
    } catch (err) {
      console.error('保存异常:', err);
      toast('保存异常，请重试', 'error');
    } finally {
      setIsSaving(false);
    }
  }, [id, isSaving, loadData]);

  // Ctrl+S 快捷键
  useEffect(() => {
    const handler = (e: KeyboardEvent) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 's') {
        e.preventDefault();
        handleSave();
      }
    };
    window.addEventListener('keydown', handler);
    return () => window.removeEventListener('keydown', handler);
  }, [handleSave]);

  // 文档加载完成后，提示本地恢复的未保存修改
  useEffect(() => {
    if (!docLoading && Object.keys(modifiedNodes).length > 0) {
      toast(`已恢复 ${Object.keys(modifiedNodes).length} 处未保存的修改`, 'info');
    }
  }, [docLoading]); // eslint-disable-line react-hooks/exhaustive-deps

  // ========== 渲染加载状态 ==========
  if (docLoading) {
    return (
      <div className="page-container">
        <div className="loading-state" style={{ minHeight: 400 }}>
          <div className="spinner" />
          <p>加载文档中...</p>
        </div>
      </div>
    );
  }

  // ========== 渲染错误状态 ==========
  if (docError || !document) {
    return (
      <div className="page-container">
        <div className="empty-state" style={{ minHeight: 400 }}>
          <svg width="48" height="48" viewBox="0 0 24 24" fill="none" stroke="#EF4444" strokeWidth="1.5">
            <circle cx="12" cy="12" r="10" />
            <line x1="12" y1="8" x2="12" y2="12" />
            <line x1="12" y1="16" x2="12.01" y2="16" />
          </svg>
          <p className="empty-title">加载失败</p>
          <p className="empty-desc">{docError || '文档不存在'}</p>
          <div style={{ display: 'flex', gap: 12 }}>
            <button className="btn btn-primary" onClick={loadData}>重试</button>
            <button className="btn btn-secondary" onClick={() => navigate('/documents')}>返回列表</button>
          </div>
        </div>
      </div>
    );
  }

  const docTitle = getFileNameWithoutExt(document.fileName);

  return (
    <div className="editor-layout">
      {/* 左侧工具栏 */}
      <div className="editor-sidebar">
        <div className="sidebar-section">
          <h3 className="sidebar-title">文档大纲</h3>
          {nodesLoading ? (
            <div className="loading-state" style={{ padding: 16 }}>
              <div className="spinner" />
            </div>
          ) : nodes.length === 0 ? (
            <p className="sidebar-empty">暂无大纲</p>
          ) : (
            <div className="outline-tree">
              {nodes.map((node) => (
                <TreeNode key={node.nodeId} node={node} depth={0} />
              ))}
            </div>
          )}
        </div>

        <div className="sidebar-section">
          <h3 className="sidebar-title">文档信息</h3>
          <div className="doc-info">
            <div className="doc-info-row">
              <span className="doc-info-label">状态</span>
              <span className={'tag ' + getStatusTagClass(document.status)}>
                {getStatusLabel(document.status)}
              </span>
            </div>
            <div className="doc-info-row">
              <span className="doc-info-label">版本</span>
              <span>v{document.currentVersion}</span>
            </div>
            <div className="doc-info-row">
              <span className="doc-info-label">创建时间</span>
              <span>{formatTime(document.createdAt)}</span>
            </div>
            <div className="doc-info-row">
              <span className="doc-info-label">修改时间</span>
              <span>{formatTime(document.updatedAt)}</span>
            </div>
            <div className="doc-info-row">
              <span className="doc-info-label">所有者</span>
              <span>{document.ownerId}</span>
            </div>
          </div>
        </div>
      </div>

      {/* 主编辑区 */}
      <div className="editor-main">
        {/* 顶部导航 */}
        <div className="editor-topbar">
          <button className="btn btn-sm btn-secondary" onClick={() => navigate('/documents')}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4 }}>
              <line x1="19" y1="12" x2="5" y2="12" />
              <polyline points="12 19 5 12 12 5" />
            </svg>
            返回
          </button>
          <h2 className="editor-title">{docTitle}</h2>
          {Object.keys(modifiedNodes).length > 0 && (
              <span className="tag tag-warning" style={{ marginRight: 8 }}>
                {Object.keys(modifiedNodes).length} 处未保存
              </span>
            )}
            <button className="btn btn-primary btn-sm" onClick={handleSave} disabled={isSaving}>
            {isSaving ? (
              <>
                <div className="spinner" style={{ width: 14, height: 14, marginRight: 4, borderWidth: 2 }} />
                保存中...
              </>
            ) : (
              <>
                <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4 }}>
                  <path d="M19 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h11l5 5v11a2 2 0 0 1-2 2z" />
                  <polyline points="17 21 17 13 7 13 7 21" />
                  <polyline points="7 3 7 8 15 8" />
                </svg>
                保存
              </>
            )}
          </button>
          {Object.keys(modifiedNodes).length > 0 && (
            <button className="btn btn-accent btn-sm" onClick={handleCreateDraft} disabled={csActionLoading === '__draft__'}>
              {csActionLoading === '__draft__' ? (
                <>保存中...</>
              ) : (
                <>
                  <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4 }}>
                    <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                    <polyline points="14 2 14 8 20 8" />
                  </svg>
                  保存为草稿
                </>
              )}
            </button>
          )}
          <button
            className={`btn btn-sm ${aiPanelOpen ? 'btn-primary' : 'btn-secondary'} ai-toggle-btn`}
            onClick={() => setAiPanelOpen(v => !v)}
            title="AI 智能编辑"
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 4 }}>
              <path d="M12 2l2.4 7.2L22 12l-7.6 2.8L12 22l-2.4-7.2L2 12l7.6-2.8z" />
            </svg>
            AI 编辑
          </button>
        </div>

        {/* TAB 切换 */}
        <div className="editor-tabs">
          <button
            className={`editor-tab ${activeTab === 'edit' ? 'active' : ''}`}
            onClick={() => setActiveTab('edit')}
          >
            编辑
          </button>
          <button
            className={`editor-tab ${activeTab === 'preview' ? 'active' : ''}`}
            onClick={() => setActiveTab('preview')}
          >
            预览
          </button>
          <button
            className={`editor-tab ${activeTab === 'changeset' ? 'active' : ''}`}
            onClick={() => setActiveTab('changeset')}
          >
            变更集
          </button>
          <button
            className={`editor-tab ${activeTab === 'version' ? 'active' : ''}`}
            onClick={() => setActiveTab('version')}
          >
            版本历史
          </button>
        </div>

        {/* 编辑面板 */}
        {activeTab === 'edit' && (
          <div className="editor-content">
            {nodes.length === 0 ? (
              <div className="editor-textarea-placeholder">
                <div className="empty-state" style={{ minHeight: 300 }}>
                  <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="1.5">
                    <path d="M11 4H4a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7" />
                    <path d="M18.5 2.5a2.121 2.121 0 0 1 3 3L12 15l-4 1 1-4 9.5-9.5z" />
                  </svg>
                  <p className="empty-title">文档内容为空</p>
                  <p className="empty-desc">请先导入文档</p>
                </div>
              </div>
            ) : (
              <div className="editor-textarea-placeholder">
                <div className="preview-body" style={{ padding: 'var(--space-lg)' }}>
                  {nodes.map(n => renderNodeEditor(n, handleTextChange))}
                </div>
              </div>
            )}
          </div>
        )}

        {/* 预览面板 */}
        {activeTab === 'preview' && (
          <div className="editor-content">
            <div className="preview-panel">
              <h1 className="preview-title">{docTitle}</h1>
              <div className="preview-meta">
                <span>版本 {document.currentVersion}</span>
                <span>·</span>
                <span>更新于 {formatTime(document.updatedAt)}</span>
                <span>·</span>
                <span>{getStatusLabel(document.status)}</span>
              </div>
              <div className="preview-body">
                {nodes.length === 0 ? (
                  <p className="preview-empty">文档内容为空</p>
                ) : (
                  nodes.map((node) => renderNodePreview(node))
                )}
              </div>
            </div>
          </div>
        )}

        {/* 变更集面板 */}
        {activeTab === 'changeset' && (
          <div className="editor-content">
            {/* 顶部操作栏 */}
            <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 20 }}>
              <h4 style={{ fontFamily: 'var(--font-heading)', fontSize: '1.125rem', fontWeight: 500 }}>
                变更集 ({changeSets.length})
              </h4>
              <div style={{ display: 'flex', gap: 10 }}>
                {Object.keys(modifiedNodes).length > 0 && (
                  <button
                    className="btn btn-accent"
                    style={{ fontSize: 13, padding: '7px 16px' }}
                    onClick={handleCreateDraft}
                    disabled={csActionLoading === '__draft__'}
                  >
                    {csActionLoading === '__draft__' ? '保存中...' : `保存为草稿 (${Object.keys(modifiedNodes).length})`}
                  </button>
                )}
                <button
                  className="btn btn-secondary"
                  style={{ fontSize: 13, padding: '7px 16px' }}
                  onClick={() => { if (window.confirm('确定删除此文档吗？此操作不可撤销。')) handleDeleteDocument(); }}
                  disabled={csActionLoading === '__delete_doc__'}
                >
                  {csActionLoading === '__delete_doc__' ? '删除中...' : '删除文档'}
                </button>
              </div>
            </div>

            {changeSetsLoading ? (
              <div className="loading-state" style={{ minHeight: 200 }}>
                <div className="spinner" />
              </div>
            ) : changeSets.length === 0 ? (
              <div className="empty-state" style={{ minHeight: 200 }}>
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="1.5">
                  <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                  <polyline points="14 2 14 8 20 8" />
                </svg>
                <p className="empty-title">暂无变更集</p>
                <p className="empty-desc">在编辑页面修改文档后，点击"保存为草稿"创建变更集</p>
              </div>
            ) : (
              <div className="changeset-list">
                {changeSets.map((cs) => {
                  const isBusy = csActionLoading === cs.changeSetId;
                  const status = cs.reviewStatus;
                  return (
                    <div key={cs.changeSetId} className="changeset-card">
                      <div className="changeset-header">
                        <div className="changeset-info">
                          <span className="changeset-summary">{cs.summary || '无摘要'}</span>
                          <span className="changeset-meta">
                            {cs.authorId} · {formatTime(cs.createdAt)}
                          </span>
                        </div>
                        <span className={getReviewStatusClass(status)}>
                          {getReviewStatusLabel(status)}
                        </span>
                      </div>

                      {cs.changes && cs.changes.length > 0 && (
                        <div className="changeset-changes">
                          {cs.changes.slice(0, 5).map((change) => (
                            <div key={change.changeId} className="change-item">
                              <span className="change-operation">{change.operation}</span>
                              <span className="change-target">{change.targetNodeType}</span>
                              <span className="change-context">{change.context || ''}</span>
                            </div>
                          ))}
                          {cs.changes.length > 5 && (
                            <div className="change-more">还有 {cs.changes.length - 5} 项变更...</div>
                          )}
                        </div>
                      )}

                      {cs.rejectionReason && (
                        <div className="changeset-rejection">拒绝原因: {cs.rejectionReason}</div>
                      )}
                      {cs.failureMessage && (
                        <div className="changeset-failure">失败原因: {cs.failureMessage}</div>
                      )}

                      {/* 操作按钮 */}
                      {!['ACCEPTED', 'REJECTED', 'FAILED'].includes(status) && (
                        <div style={{ marginTop: 12, display: 'flex', gap: 8, alignItems: 'center', flexWrap: 'wrap' }}>
                          {status === 'PENDING' && (
                            <button
                              className="btn btn-accent"
                              style={{ fontSize: 12, padding: '5px 14px' }}
                              onClick={() => handleSubmitCS(cs.changeSetId)}
                              disabled={isBusy}
                            >
                              {isBusy ? '处理中...' : '提审'}
                            </button>
                          )}
                          {status === 'SUBMITTED' && (
                            <>
                              <button
                                className="btn btn-primary"
                                style={{ fontSize: 12, padding: '5px 14px' }}
                                onClick={() => handleAcceptCS(cs.changeSetId)}
                                disabled={isBusy}
                              >
                                {isBusy ? '处理中...' : '接受'}
                              </button>
                              <button
                                className="btn btn-danger"
                                style={{ fontSize: 12, padding: '5px 14px' }}
                                onClick={() => handleRejectCS(cs.changeSetId)}
                                disabled={isBusy}
                              >
                                {isBusy ? '处理中...' : '拒绝'}
                              </button>
                            </>
                          )}
                          <button
                            className="btn btn-ghost"
                            style={{ fontSize: 12, padding: '5px 14px', color: 'var(--color-text-secondary)' }}
                            onClick={() => handleDeleteCS(cs.changeSetId)}
                            disabled={isBusy}
                          >
                            {isBusy ? '处理中...' : '删除'}
                          </button>
                        </div>
                      )}
                    </div>
                  );
                })}
              </div>
            )}
          </div>
        )}

        {/* 拒绝原因弹窗 */}
        {rejectReasonOpen && (
          <div style={modalOverlayStyle} onClick={() => setRejectReasonOpen(null)}>
            <div style={modalStyle} onClick={(e) => e.stopPropagation()}>
              <h4 style={{ fontFamily: 'var(--font-heading)', fontWeight: 500, marginBottom: 12 }}>拒绝变更集</h4>
              <textarea
                style={textareaStyle}
                placeholder="请填写拒绝原因（可选）"
                value={rejectReason}
                onChange={(e) => setRejectReason(e.target.value)}
                rows={3}
              />
              <div style={{ display: 'flex', gap: 8, justifyContent: 'flex-end', marginTop: 16 }}>
                <button className="btn btn-secondary" style={{ fontSize: 13 }} onClick={() => setRejectReasonOpen(null)}>取消</button>
                <button className="btn btn-danger" style={{ fontSize: 13 }} onClick={confirmReject}>确认拒绝</button>
              </div>
            </div>
          </div>
        )}

        {/* 版本历史面板 */}
        {activeTab === 'version' && (
          <div className="editor-content">
            {versionsLoading ? (
              <div className="loading-state" style={{ minHeight: 200 }}>
                <div className="spinner" />
              </div>
            ) : versions.length === 0 ? (
              <div className="empty-state" style={{ minHeight: 200 }}>
                <svg width="40" height="40" viewBox="0 0 24 24" fill="none" stroke="#9CA3AF" strokeWidth="1.5">
                  <circle cx="12" cy="12" r="10" />
                  <polyline points="12 6 12 12 16 14" />
                </svg>
                <p className="empty-title">暂无版本历史</p>
                <p className="empty-desc">保存文档后将生成版本记录</p>
              </div>
            ) : (
              <div className="version-timeline">
                {versions.map((ver) => (
                  <div key={ver.versionNumber} className="version-item">
                    <div className="version-dot" />
                    <div className="version-content">
                      <div className="version-header">
                        <span className="version-number">v{ver.versionNumber}</span>
                        <span className="version-time">{formatTime(ver.createdAt)}</span>
                      </div>
                      <p className="version-summary">{ver.changeSummary || '无变更摘要'}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </div>
        )}
      </div>

      {/* 右侧 — AI 智能编辑面板 */}
      <div className={`editor-ai-panel ${aiPanelOpen ? 'open' : ''}`}>
        <div className="ai-panel-header">
          <h3 className="sidebar-title">AI 智能编辑</h3>
          <button className="btn-icon" onClick={() => setAiPanelOpen(false)} title="关闭面板">
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
              <line x1="18" y1="6" x2="6" y2="18" /><line x1="6" y1="6" x2="18" y2="18" />
            </svg>
          </button>
        </div>

        <div className="ai-panel-body">
          {/* 快速指令按钮 */}
          <div className="ai-quick-prompts">
            <p className="ai-section-label">快速指令</p>
            <div className="ai-quick-grid">
              {quickPrompts.map((prompt) => (
                <button
                  key={prompt.label}
                  className="ai-quick-btn"
                  onClick={() => handleQuickPrompt(prompt.instruction)}
                  disabled={aiLoading}
                  title={prompt.instruction}
                >
                  <span className="ai-quick-icon">{prompt.icon}</span>
                  <span className="ai-quick-label">{prompt.label}</span>
                </button>
              ))}
            </div>
          </div>

          {/* 自定义指令输入 */}
          <div className="ai-custom-input">
            <p className="ai-section-label">自定义指令</p>
            <textarea
              className="ai-textarea"
              placeholder="例如：将所有标题改为蓝色，字体 16px..."
              value={aiInstruction}
              onChange={(e) => setAiInstruction(e.target.value)}
              rows={3}
              disabled={aiLoading}
            />
            <button
              className="btn btn-primary btn-block"
              onClick={() => handleAiEdit(aiInstruction)}
              disabled={aiLoading || !aiInstruction.trim()}
            >
              {aiLoading ? (
                <><span className="spinner-sm" /> 处理中...</>
              ) : (
                '执行编辑'
              )}
            </button>
          </div>

          {/* 错误提示 */}
          {aiError && (
            <div className="ai-error">
              <svg width="14" height="14" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <circle cx="12" cy="12" r="10" /><line x1="12" y1="8" x2="12" y2="12" /><line x1="12" y1="16" x2="12.01" y2="16" />
              </svg>
              <span>{aiError}</span>
            </div>
          )}

          {/* AI 结果 */}
          {aiResult && (
            <div className="ai-result">
              <div className="ai-result-header">
                <span className="ai-result-summary">{aiResult.summary || 'AI 编辑建议'}</span>
                <span className="ai-result-count">{aiResult.suggestions.length} 项建议</span>
              </div>

              {aiResult.suggestions.length > 0 ? (
                <div className="ai-suggestions">
                  {aiResult.suggestions.map((s, index) => (
                    <div key={index} className="ai-suggestion-item">
                      <div
                        className="ai-suggestion-header"
                        onClick={() => toggleSuggestion(index)}
                      >
                        <span className="ai-suggestion-index">#{index + 1}</span>
                        <span className="ai-suggestion-desc">{s.description}</span>
                        <svg
                          className={`ai-chevron ${aiExpanded[index] ? 'expanded' : ''}`}
                          width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2"
                        >
                          <polyline points="6 9 12 15 18 9" />
                        </svg>
                      </div>
                      {aiExpanded[index] && (
                        <div className="ai-suggestion-body">
                          <div className="ai-diff">
                            <div className="ai-diff-section">
                              <p className="ai-diff-label">原文</p>
                              <div className="ai-diff-original">{s.originalText || '(空)'}</div>
                            </div>
                            <div className="ai-diff-section">
                              <p className="ai-diff-label">修改后</p>
                              <div className="ai-diff-suggested">{s.suggestedText || '(空)'}</div>
                            </div>
                          </div>
                        </div>
                      )}
                    </div>
                  ))}
                </div>
              ) : (
                <p className="ai-no-change">AI 认为无需修改当前内容</p>
              )}

              {aiResult.suggestions.length > 0 && (
                <button
                  className="btn btn-primary btn-block"
                  onClick={handleAiEditAndApply}
                  disabled={aiLoading}
                  style={{ marginTop: 'var(--space-md)' }}
                >
                  {aiLoading ? (
                    <><span className="spinner-sm" /> 应用中...</>
                  ) : (
                    '应用所有建议'
                  )}
                </button>
              )}
            </div>
          )}
        </div>
      </div>
    </div>
  );
};

// ========== 节点预览渲染 ==========

function renderNodePreview(node: DocumentNode): React.ReactNode {
  const type = getNodeTypeName(node);
  switch (type) {
    case 'heading': {
      const level = getHeadingLevel(node) || 1;
      if (level === 1) return <h1 key={node.nodeId} className="preview-h1">{node.text}</h1>;
      if (level === 2) return <h2 key={node.nodeId} className="preview-h2">{node.text}</h2>;
      if (level === 3) return <h3 key={node.nodeId} className="preview-h3">{node.text}</h3>;
      return <h4 key={node.nodeId} className="preview-h4">{node.text}</h4>;
    }
    case 'paragraph':
      return <p key={node.nodeId} className="preview-p">{node.text}</p>;
    case 'list':
    case 'list_item':
      return <li key={node.nodeId} className="preview-li">{node.text}</li>;
    case 'table':
      return renderTablePreview(node);
    case 'image':
      return renderImagePreview(node);
    default:
      return <p key={node.nodeId} className="preview-p">{node.text}</p>;
  }
}

// ========== 图片渲染 ==========

function renderImagePreview(node: DocumentNode): React.ReactNode {
  const img = node.image;
  if (!img) return <p key={node.nodeId} className="preview-p">[图片: {node.text || '未命名'}]</p>;

  const dataUrl = img.encoding === 'base64' ? `data:${img.mimeType};base64,${img.data}` : img.data;
  const style: React.CSSProperties = { maxWidth: '100%', height: 'auto' };
  if (img.width) style.width = img.width;
  if (img.height) style.maxHeight = img.height;

  return (
    <div key={node.nodeId} className="preview-image-container">
      <img
        src={dataUrl}
        alt={img.altText || img.name || '图片'}
        style={style}
        className="preview-image"
        draggable={false}
      />
      {node.text && <p className="preview-image-caption">{node.text}</p>}
    </div>
  );
}

function renderImageEditor(node: DocumentNode): React.ReactNode {
  const img = node.image;
  if (!img) return <p key={node.nodeId} className="preview-p">[图片: {node.text || '未命名'}]</p>;

  const dataUrl = img.encoding === 'base64' ? `data:${img.mimeType};base64,${img.data}` : img.data;
  const style: React.CSSProperties = { maxWidth: '100%', height: 'auto', cursor: 'default' };
  if (img.width) style.width = img.width;
  if (img.height) style.maxHeight = img.height;

  return (
    <div key={node.nodeId} className="preview-image-container" style={{ position: 'relative' }}>
      <img
        src={dataUrl}
        alt={img.altText || img.name || '图片'}
        style={style}
        className="preview-image"
        draggable={false}
      />
      {node.text && <p className="preview-image-caption">{node.text}</p>}
    </div>
  );
}

// ========== 表格渲染 ==========

function renderTablePreview(tableNode: DocumentNode): React.ReactNode {
  const tf = tableNode.tableFormat;
  const rows = tableNode.children || [];
  if (rows.length === 0) {
    return <div key={tableNode.nodeId} className="preview-table-placeholder">[空表格]</div>;
  }

  const rowProps = tf?.rows || [];
  const hasHeader = rowProps.some(r => r.headerRow);
  const headerCount = rowProps.filter(r => r.headerRow).length;

  // 计算表格样式
  const tableStyle: React.CSSProperties = {};
  if (tf?.width) tableStyle.width = `${tf.width}in`;
  if (tf?.alignment === 'CENTER') tableStyle.margin = '0 auto';
  else if (tf?.alignment === 'RIGHT') tableStyle.marginLeft = 'auto';

  // 计算列宽样式
  const colWidths: (string | undefined)[] = [];
  if (tf?.columns) {
    for (const col of tf.columns) {
      colWidths.push(col.width ? `${col.width}in` : undefined);
    }
  }

  return (
    <table key={tableNode.nodeId} className="doc-table" style={tableStyle}>
      {colWidths.length > 0 && (
        <colgroup>
          {colWidths.map((w, i) => (
            <col key={i} style={w ? { width: w } : undefined} />
          ))}
        </colgroup>
      )}
      {hasHeader && (
        <thead>
          {rows.slice(0, headerCount).map((row, ri) =>
            renderTableRowPreview(row, ri, rowProps[ri], true)
          )}
        </thead>
      )}
      <tbody>
        {rows.slice(headerCount).map((row, ri) =>
          renderTableRowPreview(row, ri + headerCount, rowProps[ri + headerCount], false)
        )}
      </tbody>
    </table>
  );
}

function renderTableRowPreview(
  row: DocumentNode,
  rowIndex: number,
  rowProps: TableRowProperties | undefined,
  isHeader: boolean
): React.ReactNode {
  const cells = row.children || [];
  const cellPropsList = rowProps?.cells || [];
  const renderedCells: React.ReactNode[] = [];
  let actualCol = 0;

  for (let ci = 0; ci < cells.length; ci++) {
    const cell = cells[ci];
    const cp = cellPropsList[ci];

    // 跳过合并继续单元格（hMerge/vMerge）
    if (cp?.hMerge || cp?.vMerge) {
      actualCol++;
      continue;
    }

    const colSpan = cp?.colSpan || 1;
    const rowSpan = cp?.rowSpan || 1;
    const Tag = isHeader ? 'th' : 'td';

    // 计算单元格样式
    const cellStyle: React.CSSProperties = {};
    if (cp?.verticalAlign) cellStyle.verticalAlign = cp.verticalAlign as React.CSSProperties['verticalAlign'];
    if (cp?.width) cellStyle.width = `${cp.width}in`;

    const cellContent = (cell.children && cell.children.length > 0)
      ? cell.children.map(child => renderNodePreview(child))
      : <br />;

    renderedCells.push(
      <Tag
        key={cell.nodeId}
        colSpan={colSpan > 1 ? colSpan : undefined}
        rowSpan={rowSpan > 1 ? rowSpan : undefined}
        style={Object.keys(cellStyle).length > 0 ? cellStyle : undefined}
        className="doc-table-cell"
      >
        {cellContent}
      </Tag>
    );
    actualCol += colSpan;
  }

  const rowStyle: React.CSSProperties = {};
  if (rowProps?.height) rowStyle.height = `${rowProps.height}in`;

  return (
    <tr key={row.nodeId} style={Object.keys(rowStyle).length > 0 ? rowStyle : undefined}>
      {renderedCells}
    </tr>
  );
}

// ========== 节点编辑器渲染 ==========

function renderNodeEditor(node: DocumentNode, onChange?: (nodeId: string, text: string) => void): React.ReactNode {
  const type = getNodeTypeName(node);
  const handleBlur = (e: React.FocusEvent<HTMLElement>) => {
    const newText = e.currentTarget.innerText || '';
    if (onChange && newText !== (node.text || '')) {
      onChange(node.nodeId, newText);
    }
  };
  switch (type) {
    case 'heading': {
      const level = getHeadingLevel(node) || 1;
      const Tag = level === 1 ? 'h1' : level === 2 ? 'h2' : level === 3 ? 'h3' : 'h4';
      return <Tag key={node.nodeId} className={`preview-${Tag}`} contentEditable suppressContentEditableWarning onBlur={handleBlur}>{node.text}</Tag>;
    }
    case 'paragraph':
      return <p key={node.nodeId} className="preview-p" contentEditable suppressContentEditableWarning onBlur={handleBlur}>{node.text}</p>;
    case 'list':
    case 'list_item':
      return <li key={node.nodeId} className="preview-li" contentEditable suppressContentEditableWarning onBlur={handleBlur}>{node.text}</li>;
    case 'table':
      return renderTableEditor(node, onChange);
    case 'image':
      return renderImageEditor(node);
    default:
      return <p key={node.nodeId} className="preview-p" contentEditable suppressContentEditableWarning onBlur={handleBlur}>{node.text}</p>;
  }
}

// ========== 表格编辑器渲染 ==========

function renderTableEditor(tableNode: DocumentNode, onChange?: (nodeId: string, text: string) => void): React.ReactNode {
  const tf = tableNode.tableFormat;
  const rows = tableNode.children || [];
  if (rows.length === 0) {
    return <div key={tableNode.nodeId} className="preview-table-placeholder">[空表格]</div>;
  }

  const rowProps = tf?.rows || [];
  const hasHeader = rowProps.some(r => r.headerRow);
  const headerCount = rowProps.filter(r => r.headerRow).length;

  const tableStyle: React.CSSProperties = {};
  if (tf?.width) tableStyle.width = `${tf.width}in`;
  if (tf?.alignment === 'CENTER') tableStyle.margin = '0 auto';
  else if (tf?.alignment === 'RIGHT') tableStyle.marginLeft = 'auto';

  const colWidths: (string | undefined)[] = [];
  if (tf?.columns) {
    for (const col of tf.columns) {
      colWidths.push(col.width ? `${col.width}in` : undefined);
    }
  }

  return (
    <table key={tableNode.nodeId} className="doc-table doc-table-editor" style={tableStyle}>
      {colWidths.length > 0 && (
        <colgroup>
          {colWidths.map((w, i) => (
            <col key={i} style={w ? { width: w } : undefined} />
          ))}
        </colgroup>
      )}
      {hasHeader && (
        <thead>
          {rows.slice(0, headerCount).map((row, ri) =>
            renderTableRowEditor(row, ri, rowProps[ri], true, onChange)
          )}
        </thead>
      )}
      <tbody>
        {rows.slice(headerCount).map((row, ri) =>
          renderTableRowEditor(row, ri + headerCount, rowProps[ri + headerCount], false, onChange)
        )}
      </tbody>
    </table>
  );
}

function renderTableRowEditor(
  row: DocumentNode,
  rowIndex: number,
  rowProps: TableRowProperties | undefined,
  isHeader: boolean,
  onChange?: (nodeId: string, text: string) => void
): React.ReactNode {
  const cells = row.children || [];
  const cellPropsList = rowProps?.cells || [];
  const renderedCells: React.ReactNode[] = [];
  let actualCol = 0;

  for (let ci = 0; ci < cells.length; ci++) {
    const cell = cells[ci];
    const cp = cellPropsList[ci];

    if (cp?.hMerge || cp?.vMerge) {
      actualCol++;
      continue;
    }

    const colSpan = cp?.colSpan || 1;
    const rowSpan = cp?.rowSpan || 1;
    const Tag = isHeader ? 'th' : 'td';

    const cellStyle: React.CSSProperties = {};
    if (cp?.verticalAlign) cellStyle.verticalAlign = cp.verticalAlign as React.CSSProperties['verticalAlign'];
    if (cp?.width) cellStyle.width = `${cp.width}in`;

    const cellContent = (cell.children && cell.children.length > 0)
      ? cell.children.map(child => renderNodeEditor(child, onChange))
      : <br />;

    const handleCellBlur = (e: React.FocusEvent<HTMLElement>) => {
      const newText = e.currentTarget.innerText || '';
      if (onChange && newText !== (cellNodeText(cell) || '')) {
        onChange(cell.nodeId, newText);
      }
    };

    renderedCells.push(
      <Tag
        key={cell.nodeId}
        colSpan={colSpan > 1 ? colSpan : undefined}
        rowSpan={rowSpan > 1 ? rowSpan : undefined}
        style={Object.keys(cellStyle).length > 0 ? cellStyle : undefined}
        className="doc-table-cell doc-table-cell-editor"
        onBlur={handleCellBlur}
      >
        {cellContent}
      </Tag>
    );
    actualCol += colSpan;
  }

  const rowStyle: React.CSSProperties = {};
  if (rowProps?.height) rowStyle.height = `${rowProps.height}in`;

  return (
    <tr key={row.nodeId} style={Object.keys(rowStyle).length > 0 ? rowStyle : undefined}>
      {renderedCells}
    </tr>
  );
}

// ========== 内联样式 ==========

const modalOverlayStyle: React.CSSProperties = {
  position: 'fixed', top: 0, left: 0, right: 0, bottom: 0,
  background: 'rgba(0,0,0,0.35)', zIndex: 9999,
  display: 'flex', alignItems: 'center', justifyContent: 'center',
};

const modalStyle: React.CSSProperties = {
  background: 'var(--color-surface)', borderRadius: 12,
  padding: 24, width: 420, maxWidth: '90vw',
  boxShadow: 'var(--shadow-elevated)',
};

const textareaStyle: React.CSSProperties = {
  width: '100%', padding: '10px 14px',
  border: '1px solid var(--color-border)', borderRadius: 8,
  fontFamily: 'var(--font-body)', fontSize: 14,
  resize: 'vertical', outline: 'none',
  transition: 'border-color 150ms',
};

export default DocumentEditor;