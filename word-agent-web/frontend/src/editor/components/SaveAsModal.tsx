import React, { useState, useEffect, useRef } from 'react';
import { Close } from '@icon-park/react';

/**
 * SaveAsModal —— 另存为副本。
 *
 * 语义与「保存」明确区分：
 *   保存   → 覆盖当前文档，产生一个历史版本
 *   另存为 → 复制出一篇**全新文档**（新 id），原文档保持不变、不受影响
 *
 * 可选择存完是否直接切到副本继续编辑；不切换时留在原文档，副本静默入库。
 */

interface SaveAsModalProps {
  visible: boolean;
  /** 当前文档标题，用于生成默认副本名 */
  currentTitle: string;
  /** 当前是否有未保存改动，用于提示副本包含的是编辑器里的最新内容 */
  dirty: boolean;
  saving: boolean;
  onClose: () => void;
  onConfirm: (title: string, openCopy: boolean) => void;
}

const SaveAsModal: React.FC<SaveAsModalProps> = ({
  visible,
  currentTitle,
  dirty,
  saving,
  onClose,
  onConfirm,
}) => {
  const [title, setTitle] = useState('');
  const [openCopy, setOpenCopy] = useState(true);
  const inputRef = useRef<HTMLInputElement>(null);

  // 每次打开都重置为「原名 副本」，并选中便于直接改写
  useEffect(() => {
    if (!visible) return;
    const base = (currentTitle || '').trim() || '未命名文档';
    setTitle(`${base} 副本`);
    setOpenCopy(true);
    const timer = setTimeout(() => {
      inputRef.current?.focus();
      inputRef.current?.select();
    }, 40);
    return () => clearTimeout(timer);
  }, [visible, currentTitle]);

  if (!visible) return null;

  const finalTitle = title.trim() || '未命名文档';

  const submit = () => {
    if (saving) return;
    onConfirm(finalTitle, openCopy);
  };

  return (
    <div className="modal-overlay" onMouseDown={() => !saving && onClose()}>
      <div className="modal sa-modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">另存为</h3>
          <button className="modal-close" onClick={onClose} disabled={saving}>
            <Close theme="outline" size="16" />
          </button>
        </div>

        <div className="modal-body sa-body">
          <label className="sa-label" htmlFor="sa-title">
            副本名称
          </label>
          <input
            id="sa-title"
            ref={inputRef}
            className="sa-input"
            value={title}
            disabled={saving}
            onChange={(e) => setTitle(e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') submit();
              if (e.key === 'Escape' && !saving) onClose();
            }}
            placeholder="未命名文档"
          />

          <label className="sa-check">
            <input
              type="checkbox"
              checked={openCopy}
              disabled={saving}
              onChange={(e) => setOpenCopy(e.target.checked)}
            />
            保存后打开副本继续编辑
          </label>

          <p className="sa-hint">
            将在文档库中创建一篇<strong>全新文档</strong>，原文档「{(currentTitle || '').trim() || '未命名文档'}」不会被改动。
            {dirty && <> 副本内容取自编辑器里的当前状态（含尚未保存的改动）。</>}
          </p>
        </div>

        <div className="modal-footer">
          <button className="btn btn-secondary" onClick={onClose} disabled={saving}>
            取消
          </button>
          <button className="btn btn-primary" onClick={submit} disabled={saving}>
            {saving ? '保存中…' : '另存为副本'}
          </button>
        </div>
      </div>
    </div>
  );
};

export default SaveAsModal;
