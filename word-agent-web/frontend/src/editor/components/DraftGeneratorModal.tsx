import React, { useState, useEffect, useCallback, useMemo } from 'react';
import { Lightning, Close, Refresh } from '@icon-park/react';
import { useAgentWriter } from '../../agent/useAgentWriter';
import type { DocType, DocLength, DocStyle, DocPolish, GenerateOptions } from '../../agent/agentService';

/**
 * DraftGeneratorModal —— 在编辑器内「生成初稿」。
 *
 * 与写作台（/write）的区别：
 *   写作台是"先生成、再决定去哪"，这里是"已经在写这篇文档，缺一个开头"。
 *   因此默认用当前文档标题作为主题，生成后可选择**替换全文**或**追加到末尾**，
 *   替换前由调用方对原内容自动落一个历史版本，误操作可回退。
 */

const TYPE_OPTS: DocType[] = ['文章写作', '报告', '邮件', '提纲', '产品需求文档'];
const LENGTH_OPTS: DocLength[] = ['简短', '适中', '详细', '长文'];
const STYLE_OPTS: DocStyle[] = ['商务正式', '简洁明了', '学术严谨', '营销活泼', '技术文档', '故事化'];
const POLISH_OPTS: DocPolish[] = ['标准', '精修', '深度'];

/** 常用初稿场景，点一下就把提示词与类型都设好 */
const PRESETS: { label: string; prompt: (topic: string) => string; type: DocType }[] = [
  { label: '工作周报', type: '报告', prompt: (t) => `围绕「${t}」整理一份工作周报，包含本周进展、关键数据、风险与下周计划` },
  { label: '产品需求', type: '产品需求文档', prompt: (t) => `为「${t}」撰写产品需求文档，包含背景、目标用户、功能列表与验收标准` },
  { label: '方案汇报', type: '报告', prompt: (t) => `就「${t}」写一份方案汇报，说明现状、目标、实施路径与资源需求` },
  { label: '文章初稿', type: '文章写作', prompt: (t) => `以「${t}」为主题写一篇结构完整的文章，观点明确、层次清晰` },
  { label: '内容提纲', type: '提纲', prompt: (t) => `为「${t}」列一份详细的内容提纲，逐级拆分要点` },
];

type ApplyMode = 'replace' | 'append';

interface DraftGeneratorModalProps {
  visible: boolean;
  /** 当前文档标题，作为默认主题 */
  defaultTopic: string;
  /** 当前文档是否已有正文（决定默认应用方式与风险提示） */
  hasContent: boolean;
  onClose: () => void;
  onApply: (html: string, mode: ApplyMode) => void;
}

const Segmented: React.FC<{ options: string[]; value: number; onChange: (i: number) => void; disabled?: boolean }> = ({
  options,
  value,
  onChange,
  disabled,
}) => (
  <div className="dg-seg">
    {options.map((o, i) => (
      <button key={o} className={i === value ? 'on' : ''} disabled={disabled} onClick={() => onChange(i)} type="button">
        {o}
      </button>
    ))}
  </div>
);

const DraftGeneratorModal: React.FC<DraftGeneratorModalProps> = ({
  visible,
  defaultTopic,
  hasContent,
  onClose,
  onApply,
}) => {
  const writer = useAgentWriter();

  const [prompt, setPrompt] = useState('');
  const [typeIdx, setTypeIdx] = useState(0);
  const [lenIdx, setLenIdx] = useState(1);
  const [styleIdx, setStyleIdx] = useState(0);
  const [polishIdx, setPolishIdx] = useState(0);
  // 已有正文时默认「追加到末尾」，避免一上来就覆盖用户已写的东西
  const [mode, setMode] = useState<ApplyMode>(hasContent ? 'append' : 'replace');

  const topic = useMemo(() => (defaultTopic || '').trim() || '未命名主题', [defaultTopic]);

  // 每次打开时按当前标题重置提示词
  useEffect(() => {
    if (!visible) return;
    setPrompt((prev) => prev || `围绕「${topic}」写一份完整文档`);
    setMode(hasContent ? 'append' : 'replace');
  }, [visible, topic, hasContent]);

  // 关闭时停掉可能仍在流式输出的请求
  useEffect(() => {
    if (!visible) writer.stop();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [visible]);

  const handleGenerate = useCallback(() => {
    const opts: GenerateOptions = {
      prompt: prompt.trim() || `围绕「${topic}」写一份完整文档`,
      type: TYPE_OPTS[typeIdx],
      length: LENGTH_OPTS[lenIdx],
      style: STYLE_OPTS[styleIdx],
      polish: POLISH_OPTS[polishIdx],
    };
    writer.generate(opts);
  }, [prompt, topic, typeIdx, lenIdx, styleIdx, polishIdx, writer]);

  const handleApply = useCallback(() => {
    const html = (writer.text || '').trim();
    if (!html) return;
    onApply(html, mode);
  }, [writer.text, mode, onApply]);

  if (!visible) return null;

  const streaming = writer.isStreaming;
  const hasDraft = !!writer.text && !streaming;

  return (
    <div className="modal-overlay" onMouseDown={() => !streaming && onClose()}>
      <div className="modal dg-modal" onMouseDown={(e) => e.stopPropagation()}>
        <div className="modal-header">
          <h3 className="modal-title">生成初稿</h3>
          <button className="modal-close" onClick={onClose} disabled={streaming} title={streaming ? '生成中，请先停止' : '关闭'}>
            <Close theme="outline" size="16" />
          </button>
        </div>

        <div className="modal-body dg-body">
          {/* 需求描述 */}
          <label className="dg-label">写什么</label>
          <textarea
            className="dg-textarea"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            disabled={streaming}
            placeholder="描述这篇文档要写什么，越具体结果越好…"
            rows={3}
          />

          {/* 场景预设 */}
          <div className="dg-presets">
            {PRESETS.map((p) => (
              <button
                key={p.label}
                type="button"
                className="dg-preset"
                disabled={streaming}
                onClick={() => {
                  setPrompt(p.prompt(topic));
                  setTypeIdx(TYPE_OPTS.indexOf(p.type));
                }}
              >
                {p.label}
              </button>
            ))}
          </div>

          {/* 参数 */}
          <div className="dg-controls">
            <div className="dg-control">
              <span className="dg-cl">类型</span>
              <Segmented options={TYPE_OPTS} value={typeIdx} onChange={setTypeIdx} disabled={streaming} />
            </div>
            <div className="dg-control">
              <span className="dg-cl">篇幅</span>
              <Segmented options={LENGTH_OPTS} value={lenIdx} onChange={setLenIdx} disabled={streaming} />
            </div>
            <div className="dg-control">
              <span className="dg-cl">文体</span>
              <Segmented options={STYLE_OPTS} value={styleIdx} onChange={setStyleIdx} disabled={streaming} />
            </div>
            <div className="dg-control">
              <span className="dg-cl">润色</span>
              <Segmented options={POLISH_OPTS} value={polishIdx} onChange={setPolishIdx} disabled={streaming} />
            </div>
          </div>

          {/* 预览 */}
          <div className="dg-preview-head">
            <span className="dg-label">初稿预览</span>
            <span className="dg-status">
              {streaming ? 'AI 正在书写…' : writer.text ? `约 ${writer.tokens} tokens` : '尚未生成'}
            </span>
          </div>
          <div className="dg-preview">
            {writer.text ? (
              <div className="dg-preview-body" dangerouslySetInnerHTML={{ __html: writer.text }} />
            ) : (
              <div className="dg-preview-empty">
                填好上面的需求，点「生成」后这里会实时显示 AI 写出的初稿。
                <br />
                满意后再选择应用方式写入文档，不满意可以直接重新生成。
              </div>
            )}
            {streaming && <span className="dg-cursor" />}
          </div>
          {writer.error && <div className="dg-error">{writer.error}</div>}

          {/* 应用方式 */}
          {hasDraft && (
            <div className="dg-apply-mode">
              <span className="dg-cl">写入方式</span>
              <label className={mode === 'replace' ? 'on' : ''}>
                <input type="radio" checked={mode === 'replace'} onChange={() => setMode('replace')} />
                替换全文
              </label>
              <label className={mode === 'append' ? 'on' : ''}>
                <input type="radio" checked={mode === 'append'} onChange={() => setMode('append')} />
                追加到末尾
              </label>
              {hasContent && mode === 'replace' && (
                <span className="dg-warn">原内容会自动存为历史版本，可随时恢复</span>
              )}
            </div>
          )}
        </div>

        <div className="modal-footer dg-footer">
          <button className="btn btn-secondary" onClick={onClose} disabled={streaming}>
            取消
          </button>
          {streaming ? (
            <button className="btn btn-secondary" onClick={writer.stop}>
              <Close theme="filled" size="14" />
              停止生成
            </button>
          ) : (
            <button className="btn btn-secondary" onClick={handleGenerate}>
              {writer.text ? <Refresh theme="outline" size="14" /> : <Lightning theme="filled" size="14" />}
              {writer.text ? '重新生成' : '生成'}
            </button>
          )}
          <button className="btn btn-primary" onClick={handleApply} disabled={!hasDraft}>
            写入文档
          </button>
        </div>
      </div>
    </div>
  );
};

export default DraftGeneratorModal;
export type { ApplyMode };
