import React, { useState, useEffect, useMemo, useCallback } from 'react';
import { Close, Redo, Delete, Time } from '@icon-park/react';
import {
  listVersions,
  formatVersionTime,
  versionPlainText,
  VERSION_REASON_LABEL,
  DOC_VERSIONS_EVENT,
  MAX_PER_DOC,
  type DocVersion,
} from '../../services/docVersions';
import { computeDiff } from '../utils/diffEngine';

/**
 * VersionHistoryPanel —— 文档历史版本抽屉。
 *
 * 能力：
 *   - 时间线列出所有版本（保存 / 手动快照 / 恢复前备份 / 生成初稿前备份 / 导入）
 *   - 选中任一版本查看完整内容
 *   - 「对比当前」用字符级 diff 展示这一版与当前编辑内容的差异
 *   - 一键恢复（由调用方在恢复前对当前内容再存一版，因此恢复本身也可回退）
 *   - 删除单个版本
 *
 * 版本数据来自 localStorage，监听 DOC_VERSIONS_EVENT 实时刷新，
 * 因此在面板打开时点保存，列表会立刻多出一条。
 */

interface VersionHistoryPanelProps {
  visible: boolean;
  /** 当前文档 id；为空或 gen- 开头表示文档尚未保存 */
  docId: string;
  /** 文档是否已持久化 */
  persisted: boolean;
  /** 编辑器里的当前 HTML，用于「对比当前」 */
  currentHtml: string;
  onClose: () => void;
  onRestore: (version: DocVersion) => void;
  onDelete: (version: DocVersion) => void;
  /** 立即把当前内容存为一个手动快照 */
  onSnapshot: () => void;
}

const VersionHistoryPanel: React.FC<VersionHistoryPanelProps> = ({
  visible,
  docId,
  persisted,
  currentHtml,
  onClose,
  onRestore,
  onDelete,
  onSnapshot,
}) => {
  const [versions, setVersions] = useState<DocVersion[]>([]);
  const [selectedId, setSelectedId] = useState<string | null>(null);
  const [compare, setCompare] = useState(false);

  // 读取 + 订阅版本变化
  const reload = useCallback(() => {
    setVersions(persisted ? listVersions(docId) : []);
  }, [docId, persisted]);

  useEffect(() => {
    if (!visible) return;
    reload();
    const onChanged = () => reload();
    window.addEventListener(DOC_VERSIONS_EVENT, onChanged);
    return () => window.removeEventListener(DOC_VERSIONS_EVENT, onChanged);
  }, [visible, reload]);

  // 默认选中最新一版；若选中的版本被删掉则回落到最新
  useEffect(() => {
    if (versions.length === 0) {
      setSelectedId(null);
      return;
    }
    if (!selectedId || !versions.some((v) => v.id === selectedId)) {
      setSelectedId(versions[0].id);
    }
  }, [versions, selectedId]);

  const selected = useMemo(
    () => versions.find((v) => v.id === selectedId) || null,
    [versions, selectedId],
  );

  // 字符级对比：这一版 → 当前编辑内容
  const diffChunks = useMemo(() => {
    if (!compare || !selected) return null;
    return computeDiff(versionPlainText(selected.html), versionPlainText(currentHtml));
  }, [compare, selected, currentHtml]);

  const diffStats = useMemo(() => {
    if (!diffChunks) return null;
    let added = 0;
    let removed = 0;
    for (const c of diffChunks) {
      if (c.type === 'inserted') added += c.text.length;
      else if (c.type === 'deleted') removed += c.text.length;
    }
    return { added, removed };
  }, [diffChunks]);

  if (!visible) return null;

  return (
    <aside className="vh-panel">
      <header className="vh-header">
        <div className="vh-title">
          <Time theme="outline" size="15" />
          历史版本
          {persisted && <span className="vh-count">{versions.length}/{MAX_PER_DOC}</span>}
        </div>
        <button className="vh-close" onClick={onClose} title="关闭">
          <Close theme="outline" size="15" />
        </button>
      </header>

      {/* 未保存的新文档：历史需要有落点，先引导保存 */}
      {!persisted ? (
        <div className="vh-empty">
          <p className="vh-empty-title">这篇文档还没保存</p>
          <p className="vh-empty-desc">
            历史版本按文档记录。先点顶部「保存」建立文档，之后每次保存都会自动留下一版，可随时回看和恢复。
          </p>
        </div>
      ) : (
        <>
          <div className="vh-toolbar">
            <button className="vh-snapshot-btn" onClick={onSnapshot} title="把当前内容立即存为一个版本">
              存为当前版本
            </button>
            {selected && (
              <label className="vh-compare-toggle">
                <input type="checkbox" checked={compare} onChange={(e) => setCompare(e.target.checked)} />
                对比当前
              </label>
            )}
          </div>

          {versions.length === 0 ? (
            <div className="vh-empty">
              <p className="vh-empty-title">还没有历史版本</p>
              <p className="vh-empty-desc">点一次「保存」或上面的「存为当前版本」，就会产生第一条记录。</p>
            </div>
          ) : (
            <div className="vh-content">
              {/* 版本时间线 */}
              <ul className="vh-list">
                {versions.map((v, i) => (
                  <li
                    key={v.id}
                    className={`vh-item ${v.id === selectedId ? 'on' : ''}`}
                    onClick={() => setSelectedId(v.id)}
                  >
                    <div className="vh-item-main">
                      <span className="vh-item-time">{formatVersionTime(v.createdAt)}</span>
                      {i === 0 && <span className="vh-badge vh-badge-latest">最新</span>}
                      <span className={`vh-badge vh-badge-${v.reason}`}>{VERSION_REASON_LABEL[v.reason]}</span>
                    </div>
                    <div className="vh-item-title">{v.title || '未命名文档'}</div>
                    <div className="vh-item-meta">
                      {v.words.toLocaleString()} 字
                      {v.note ? ` · ${v.note}` : ''}
                    </div>
                    <button
                      className="vh-item-del"
                      title="删除此版本"
                      onClick={(e) => {
                        e.stopPropagation();
                        onDelete(v);
                      }}
                    >
                      <Delete theme="outline" size="13" />
                    </button>
                  </li>
                ))}
              </ul>

              {/* 选中版本详情 */}
              {selected && (
                <div className="vh-detail">
                  <div className="vh-detail-head">
                    <span className="vh-detail-title">
                      {formatVersionTime(selected.createdAt)} · {selected.words.toLocaleString()} 字
                      {compare && diffStats && (
                        <span className="vh-diff-stat">
                          <em className="add">+{diffStats.added}</em>
                          <em className="del">-{diffStats.removed}</em>
                        </span>
                      )}
                    </span>
                    <button className="vh-restore-btn" onClick={() => onRestore(selected)}>
                      <Redo theme="outline" size="13" />
                      恢复此版本
                    </button>
                  </div>

                  <div className="vh-detail-body">
                    {compare && diffChunks ? (
                      <div className="vh-diff">
                        {diffChunks.map((c, i) =>
                          c.type === 'unchanged' ? (
                            <span key={i}>{c.text}</span>
                          ) : c.type === 'inserted' ? (
                            <ins key={i}>{c.text}</ins>
                          ) : (
                            <del key={i}>{c.text}</del>
                          ),
                        )}
                      </div>
                    ) : (
                      <div className="vh-version-html" dangerouslySetInnerHTML={{ __html: selected.html }} />
                    )}
                  </div>

                  {compare && (
                    <div className="vh-diff-legend">
                      <span><del>删除</del> 该版本有、当前没有</span>
                      <span><ins>新增</ins> 当前有、该版本没有</span>
                    </div>
                  )}
                </div>
              )}
            </div>
          )}
        </>
      )}
    </aside>
  );
};

export default VersionHistoryPanel;
