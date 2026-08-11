import React, { useState, useEffect, useCallback } from 'react';
import { useNavigate, useLocation } from 'react-router-dom';
import {
  Calendar,
  DocDetail,
  Mail,
  Notes,
  Lightning,
  Copy,
  PreviewOpen,
  Send,
  Close,
  Refresh,
} from '@icon-park/react';
import type { IconComp } from '../types/icon';
import { useToast } from '../components/Toast';
import { useAgentWriter } from '../agent/useAgentWriter';
import {
  extractTopic,
  detectRefineOp,
  refineOpLabel,
  type DocType,
  type DocLength,
  type DocStyle,
  type DocPolish,
  type GenerateOptions,
} from '../agent/agentService';

const chips: { label: string; p: string; Icon: IconComp; type?: DocType }[] = [
  { label: '周报总结', Icon: Calendar, p: '帮我整理本周工作进展，生成一份结构清晰的周报，包含成果、风险与下周计划', type: '报告' },
  { label: '产品需求', Icon: DocDetail, p: '撰写一份产品需求文档 PRD，包含背景、目标用户、功能列表与验收标准', type: '产品需求文档' },
  { label: '商务邮件', Icon: Mail, p: '写一封委婉拒绝合作邀约的商务邮件，语气专业且不伤和气', type: '邮件' },
  { label: '会议纪要', Icon: Notes, p: '根据会议要点整理成结构化会议纪要，标注待办事项与负责人', type: '报告' },
];

const TYPE_OPTS: DocType[] = ['文章写作', '报告', '邮件', '提纲', '产品需求文档'];
const LENGTH_OPTS: DocLength[] = ['简短', '适中', '详细', '长文'];
const STYLE_OPTS: DocStyle[] = ['商务正式', '简洁明了', '学术严谨', '营销活泼', '技术文档', '故事化'];
const POLISH_OPTS: DocPolish[] = ['标准', '精修', '深度'];

const Segmented: React.FC<{
  options: string[];
  value: number;
  onChange: (i: number) => void;
}> = ({ options, value, onChange }) => (
  <div className="wa-seg">
    {options.map((o, i) => (
      <button key={o} className={i === value ? 'on' : ''} onClick={() => onChange(i)}>
        {o}
      </button>
    ))}
  </div>
);

interface HistoryItem {
  label: string;
}

const Writing: React.FC = () => {
  const navigate = useNavigate();
  const location = useLocation();
  const { toast } = useToast();
  const writer = useAgentWriter();

  const [prompt, setPrompt] = useState('');
  const [typeIdx, setTypeIdx] = useState(0);
  const [lenIdx, setLenIdx] = useState(1);
  const [style, setStyle] = useState<DocStyle>('商务正式');
  const [polishIdx, setPolishIdx] = useState(0);

  const [history, setHistory] = useState<HistoryItem[]>([]);
  const [refineInput, setRefineInput] = useState('');

  useEffect(() => {
    const t = new URLSearchParams(location.search).get('t');
    if (t) setPrompt(`基于「${t}」生成一份完整文档：`);
  }, [location.search]);

  const handleGenerate = useCallback(() => {
    const opts: GenerateOptions = {
      prompt: prompt.trim() || '生成一份文档',
      type: TYPE_OPTS[typeIdx],
      length: LENGTH_OPTS[lenIdx],
      style,
      polish: POLISH_OPTS[polishIdx],
    };
    writer.generate(opts);
    setHistory([{ label: '生成初稿' }]);
    setRefineInput('');
  }, [prompt, typeIdx, lenIdx, style, polishIdx, writer]);

  const handleRefine = useCallback(() => {
    const ins = refineInput.trim();
    if (!ins || writer.isStreaming) return;
    writer.refine(writer.text, ins);
    const op = detectRefineOp(ins);
    setHistory((h) => [...h, { label: `${refineOpLabel(op)}：${ins}` }]);
    setRefineInput('');
  }, [refineInput, writer]);

  const handleCopy = useCallback(() => {
    if (!writer.text) return;
    const div = document.createElement('div');
    div.innerHTML = writer.text;
    const plain = div.textContent || '';
    navigator.clipboard?.writeText(plain).then(
      () => toast('已复制全文', 'success'),
      () => toast('复制失败', 'error'),
    );
  }, [writer.text, toast]);

  const handleOpenInEditor = useCallback(() => {
    if (!writer.text) return;
    const title = extractTopic(prompt) || 'Agent 生成文档';
    navigate(`/editor-v2/gen-${Date.now()}`, {
      state: { title, initialHtml: writer.text },
    });
  }, [writer.text, prompt, navigate]);

  const statusText = writer.isStreaming
    ? 'Agent 正在书写…'
    : writer.text
      ? `生成完成 · 约 ${writer.tokens} tokens`
      : '输入需求后点击「生成」';

  return (
    <div className="wa-workbench">
      <div className="wa-prompt-box">
        <textarea
          value={prompt}
          onChange={(e) => setPrompt(e.target.value)}
          placeholder="描述你想要的文档，Word Agent 帮你一键生成…"
        />
        <div className="wa-prompt-actions">
          {chips.map(({ label, p, Icon, type }) => (
            <div
              key={label}
              className="wa-chip"
              onClick={() => {
                setPrompt(p);
                if (type) setTypeIdx(TYPE_OPTS.indexOf(type));
              }}
            >
              <Icon theme="outline" size="14" />
              {label}
            </div>
          ))}
          <span className="spacer" />
          {writer.isStreaming ? (
            <button className="wa-gen-btn ghost" onClick={writer.stop}>
              <Close theme="filled" size="15" />
              停止
            </button>
          ) : (
            <button className="wa-gen-btn" onClick={handleGenerate}>
              <Lightning theme="filled" size="15" />
              生成
            </button>
          )}
        </div>
      </div>

      <div className="wa-controls">
        <div className="wa-control">
          <span className="cl">类型</span>
          <Segmented options={TYPE_OPTS} value={typeIdx} onChange={setTypeIdx} />
        </div>
        <div className="wa-control">
          <span className="cl">篇幅</span>
          <Segmented options={LENGTH_OPTS} value={lenIdx} onChange={setLenIdx} />
        </div>
        <div className="wa-control">
          <span className="cl">文体</span>
          <select className="wa-select" value={style} onChange={(e) => setStyle(e.target.value as DocStyle)}>
            {STYLE_OPTS.map((s) => (
              <option key={s} value={s}>
                {s}
              </option>
            ))}
          </select>
        </div>
        <div className="wa-control">
          <span className="cl">润色</span>
          <Segmented options={POLISH_OPTS} value={polishIdx} onChange={setPolishIdx} />
        </div>
      </div>

      <div className="wa-result-head">
        <h3>生成结果</h3>
        <div className="wa-result-actions">
          <span className="wa-status">{statusText}</span>
          {writer.text && !writer.isStreaming && (
            <>
              <button className="wa-chip" onClick={handleCopy}>
                <Copy theme="outline" size="14" />
                复制
              </button>
              <button className="wa-chip" onClick={handleOpenInEditor}>
                <PreviewOpen theme="outline" size="14" />
                在编辑器中打开
              </button>
              <button className="wa-chip" onClick={handleGenerate}>
                <Refresh theme="outline" size="14" />
                重新生成
              </button>
            </>
          )}
        </div>
      </div>

      <div className="wa-doc-preview">
        {writer.isStreaming && <span className="wa-stream-cursor" />}
        {writer.text ? (
          <div className="wa-doc-body" dangerouslySetInnerHTML={{ __html: writer.text }} />
        ) : (
          <div className="wa-doc-empty">
            <p>这里会实时显示 Agent 写出的文档。</p>
            <p className="muted">选择上方类型 / 篇幅 / 文体 / 润色，描述需求后点击「生成」。</p>
          </div>
        )}
        {writer.error && <div className="wa-doc-error">{writer.error}</div>}
      </div>

      {writer.text && (
        <div className="wa-refine">
          <div className="wa-refine-head">
            <span>多轮润色</span>
            <span className="wa-refine-tip">继续下达指令，Agent 会改写同一篇文档</span>
          </div>
          <div className="wa-refine-input">
            <input
              value={refineInput}
              onChange={(e) => setRefineInput(e.target.value)}
              onKeyDown={(e) => e.key === 'Enter' && handleRefine()}
              placeholder="例如：把第二段扩写、改成更口语、压缩到 200 字、整体润色一遍…"
            />
            <button className="wa-gen-btn" onClick={handleRefine} disabled={writer.isStreaming}>
              <Send theme="filled" size="15" />
              润色
            </button>
          </div>
          {history.length > 0 && (
            <div className="wa-refine-history">
              {history.map((h, i) => (
                <span key={i} className="wa-refine-chip">
                  {i === 0 ? '●' : '↳'} {h.label}
                </span>
              ))}
            </div>
          )}
        </div>
      )}
    </div>
  );
};

export default Writing;
