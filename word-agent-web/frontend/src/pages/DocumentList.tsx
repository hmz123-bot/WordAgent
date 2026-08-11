import React, { useState, useEffect, useCallback, useRef } from 'react';
import { useNavigate } from 'react-router-dom';
import { FolderUpload } from '@icon-park/react';
import { useToast } from '../components/Toast';
import {
  listDocs,
  deleteDoc,
  countWords,
  DOC_STORE_EVENT,
  type LocalDoc,
} from '../services/docStore';
import { importDocumentFile, IMPORT_ACCEPT } from '../services/docImport';

// ========== 工具函数 ==========

function formatTime(iso: string): string {
  const d = new Date(iso);
  const now = new Date();
  const diff = now.getTime() - d.getTime();
  const days = Math.floor(diff / (1000 * 60 * 60 * 24));

  if (days === 0) {
    const h = d.getHours().toString().padStart(2, '0');
    const m = d.getMinutes().toString().padStart(2, '0');
    return `今天 ${h}:${m}`;
  }
  if (days === 1) return '昨天';
  if (days < 7) return `${days} 天前`;
  const month = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${month}月${day}日`;
}

// ========== 组件 ==========

const DocumentList: React.FC = () => {
  const navigate = useNavigate();
  const { toast } = useToast();

  const [allDocuments, setAllDocuments] = useState<LocalDoc[]>([]);
  const [searchQuery, setSearchQuery] = useState('');

  // 删除确认
  const [deleteTarget, setDeleteTarget] = useState<LocalDoc | null>(null);
  const [deleting, setDeleting] = useState(false);

  // 加载文档（来自本地仓库，无后端依赖）
  const loadDocuments = useCallback(() => {
    setAllDocuments(listDocs());
  }, []);

  useEffect(() => {
    loadDocuments();
    // 编辑器保存后广播事件，列表即时刷新
    const onChanged = () => loadDocuments();
    window.addEventListener(DOC_STORE_EVENT, onChanged);
    return () => window.removeEventListener(DOC_STORE_EVENT, onChanged);
  }, [loadDocuments]);

  // 过滤和搜索
  const filtered = allDocuments.filter((doc) => {
    if (!searchQuery) return true;
    const hay = `${doc.title} ${doc.snippet}`.toLowerCase();
    return hay.includes(searchQuery.toLowerCase());
  });

  // 统计
  const totalCount = allDocuments.length;
  const recentCount = allDocuments.filter(
    (d) => Date.now() - new Date(d.updatedAt).getTime() < 7 * 24 * 60 * 60 * 1000,
  ).length;
  const newWeekCount = allDocuments.filter(
    (d) => Date.now() - new Date(d.createdAt).getTime() < 7 * 24 * 60 * 60 * 1000,
  ).length;
  const totalWords = allDocuments.reduce((sum, d) => sum + countWords(d.html), 0);

  // 新建文档
  const handleCreate = () => {
    navigate(`/editor-v2/gen-${Date.now()}`, { state: { title: '未命名文档', initialHtml: '' } });
  };

  // 导入文档（本地文件 → 本地文档库）
  const fileInputRef = useRef<HTMLInputElement>(null);
  const [importing, setImporting] = useState(false);
  const openImport = () => fileInputRef.current?.click();
  const handleFileChange = async (e: React.ChangeEvent<HTMLInputElement>) => {
    const file = e.target.files?.[0];
    e.target.value = ''; // 允许重复导入同一文件
    if (!file) return;
    setImporting(true);
    try {
      const doc = await importDocumentFile(file);
      loadDocuments();
      toast(`已导入「${doc.title}」`, 'success');
      navigate(`/editor-v2/${doc.id}`);
    } catch (err) {
      toast(err instanceof Error ? err.message : '导入失败', 'error');
    } finally {
      setImporting(false);
    }
  };

  // 删除
  const handleDelete = async () => {
    if (!deleteTarget) return;
    setDeleting(true);
    try {
      deleteDoc(deleteTarget.id);
      setDeleteTarget(null);
      loadDocuments();
      toast('已删除', 'success');
    } catch {
      toast('删除失败', 'error');
    } finally {
      setDeleting(false);
    }
  };

  // 删除确认弹窗
  const renderDeleteModal = () => {
    if (!deleteTarget) return null;
    return (
      <div className="modal-overlay" onClick={() => !deleting && setDeleteTarget(null)}>
        <div className="modal" onClick={(e) => e.stopPropagation()}>
          <div className="modal-header">
            <h3 className="modal-title">确认删除</h3>
            <button className="modal-close" onClick={() => setDeleteTarget(null)}>
              <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2">
                <path d="M18 6L6 18M6 6l12 12" />
              </svg>
            </button>
          </div>
          <div className="modal-body">
            <p style={{ color: '#6B7280', lineHeight: 1.6 }}>
              确定要删除「<strong>{deleteTarget.title || '未命名文档'}</strong>」吗？
              此操作不可撤销，文档将从本地存储中永久删除。
            </p>
          </div>
          <div className="modal-footer">
            <button className="btn btn-secondary" onClick={() => setDeleteTarget(null)} disabled={deleting}>
              取消
            </button>
            <button className="btn btn-danger" onClick={handleDelete} disabled={deleting}>
              {deleting ? '删除中...' : '确认删除'}
            </button>
          </div>
        </div>
      </div>
    );
  };

  // ========== 渲染 ==========

  return (
    <div className="page-container">
      <div className="page-header">
        <div>
          <h1 className="page-title">文档</h1>
          <p className="page-subtitle">本地文档库，共 {totalCount} 篇</p>
        </div>
        <div className="page-header-actions">
          <button className="btn btn-secondary" onClick={openImport} disabled={importing}>
            <FolderUpload theme="outline" size="16" />
            {importing ? '导入中…' : '导入文档'}
          </button>
          <button className="btn btn-primary" onClick={handleCreate}>
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" style={{ marginRight: 6 }}>
              <line x1="12" y1="5" x2="12" y2="19" />
              <line x1="5" y1="12" x2="19" y2="12" />
            </svg>
            新建文档
          </button>
        </div>
        {/* 隐藏的文件选择器 */}
        <input
          ref={fileInputRef}
          type="file"
          accept={IMPORT_ACCEPT}
          style={{ display: 'none' }}
          onChange={handleFileChange}
        />
      </div>

      {/* 统计卡片 */}
      <div className="stats-grid">
        <div className="stat-card">
          <div className="stat-value">{totalCount}</div>
          <div className="stat-label">全部文档</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{recentCount}</div>
          <div className="stat-label">近期修改</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{newWeekCount}</div>
          <div className="stat-label">本周新建</div>
        </div>
        <div className="stat-card">
          <div className="stat-value">{totalWords.toLocaleString()}</div>
          <div className="stat-label">总字数</div>
        </div>
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
          placeholder="搜索文档名称或内容..."
          value={searchQuery}
          onChange={(e) => setSearchQuery(e.target.value)}
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
            {searchQuery ? '没有匹配的文档' : '还没有文档'}
          </p>
          <p className="empty-desc">
            {searchQuery ? '尝试修改搜索条件' : '点击右上角「新建文档」开始写作，或到写作台生成后保存'}
          </p>
        </div>
      )}

      {/* 表格 */}
      {filtered.length > 0 && (
        <div className="table-container">
          <table className="table">
            <thead>
              <tr>
                <th>文档名称</th>
                <th>字数</th>
                <th>修改时间</th>
                <th>操作</th>
              </tr>
            </thead>
            <tbody>
              {filtered.map((doc) => (
                <tr
                  key={doc.id}
                  className="table-row-clickable"
                  onClick={() => navigate(`/editor-v2/${doc.id}`)}
                >
                  <td>
                    <div className="doc-name-cell">
                      <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="#2D8C7F" strokeWidth="1.5">
                        <path d="M14 2H6a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h12a2 2 0 0 0 2-2V8z" />
                        <polyline points="14 2 14 8 20 8" />
                      </svg>
                      <div className="doc-name-wrap">
                        <span className="doc-name">{doc.title || '未命名文档'}</span>
                        {doc.snippet && <span className="doc-snippet">{doc.snippet}</span>}
                      </div>
                    </div>
                  </td>
                  <td className="cell-muted">{countWords(doc.html).toLocaleString()}</td>
                  <td className="cell-muted">{formatTime(doc.updatedAt)}</td>
                  <td>
                    <div className="inline-actions">
                      <button
                        className="btn btn-sm btn-danger-outline"
                        onClick={(e) => {
                          e.stopPropagation();
                          setDeleteTarget(doc);
                        }}
                      >
                        删除
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {renderDeleteModal()}
    </div>
  );
};

export default DocumentList;
