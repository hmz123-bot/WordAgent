import React, { useState, useRef, useEffect, useCallback, Fragment, type ReactNode } from 'react';
import { Robot, User, Close } from '@icon-park/react';
import type { ChatMessage } from '../hooks/useAgentTools';

/**
 * Chat Panel — 侧边栏 AI 对话面板。
 *
 * 类似 Word Copilot 侧边面板：总结全文、文档问答、多轮对话。
 * 答案可"插入到文档"（以纯文本插入，保持正文干净）。
 * 回答由 chatService 流式返回（DeepSeek 真实模型 / 本地模拟），此处只负责呈现。
 */

interface ChatPanelProps {
  visible: boolean;
  messages: ChatMessage[];
  isProcessing: boolean;
  onSendMessage: (message: string) => void;
  onStop: () => void;
  onClear: () => void;
  onInsertToDocument: (content: string) => void;
  onClose: () => void;
}

const ChatPanel: React.FC<ChatPanelProps> = ({
  visible,
  messages,
  isProcessing,
  onSendMessage,
  onStop,
  onClear,
  onInsertToDocument,
  onClose,
}) => {
  const [input, setInput] = useState('');
  const messagesEndRef = useRef<HTMLDivElement>(null);
  const inputRef = useRef<HTMLTextAreaElement>(null);

  // 滚动到最新消息
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  }, [messages]);

  // 自动 focus 输入框
  useEffect(() => {
    if (visible && inputRef.current) {
      inputRef.current.focus();
    }
  }, [visible]);

  const handleSend = useCallback(() => {
    const trimmed = input.trim();
    if (!trimmed || isProcessing) return;
    onSendMessage(trimmed);
    setInput('');
  }, [input, isProcessing, onSendMessage]);

  const handleKeyDown = useCallback(
    (e: React.KeyboardEvent) => {
      if (e.key === 'Enter' && !e.shiftKey) {
        e.preventDefault();
        handleSend();
      }
    },
    [handleSend],
  );

  // 快速指令
  const quickPrompts = [
    { label: '总结全文', prompt: '请总结这份文档的核心要点' },
    { label: '改进建议', prompt: '请给这份文档提出改进建议' },
    { label: '续写', prompt: '请从当前内容继续写作' },
    { label: '润色思路', prompt: '请给整篇文档的润色思路' },
  ];

  if (!visible) return null;

  const lastAssistant = messages[messages.length - 1];
  const showTyping = isProcessing && lastAssistant?.role === 'assistant' && !lastAssistant.content;

  return (
    <div className="chat-panel">
      <div className="chat-panel-header">
        <h3>AI 助手</h3>
        <div className="cp-header-actions">
          {messages.length > 0 && (
            <button className="cp-clear-btn" onClick={onClear} title="清空对话">
              清空
            </button>
          )}
          <button className="cp-close-btn" onClick={onClose} title="关闭">
            <Close theme="outline" size="15" />
          </button>
        </div>
      </div>

      <div className="chat-messages">
        {messages.length === 0 && (
          <div className="chat-welcome">
            <div className="chat-welcome-icon"><Robot theme="outline" size="34" /></div>
            <p>我是你的写作助手。可以帮你：</p>
            <ul>
              <li>总结文档要点</li>
              <li>回答文档相关问题</li>
              <li>提出改进建议</li>
              <li>帮你续写或改写</li>
            </ul>
            <div className="chat-quick-prompts">
              <span className="quick-label">快速开始：</span>
              {quickPrompts.map((qp) => (
                <button
                  key={qp.label}
                  className="quick-prompt-btn"
                  onClick={() => onSendMessage(qp.prompt)}
                >
                  {qp.prompt}
                </button>
              ))}
            </div>
          </div>
        )}

        {messages.map((msg, idx) => (
          <ChatBubble
            key={idx}
            message={msg}
            streaming={isProcessing && idx === messages.length - 1}
            onInsert={() => onInsertToDocument(stripMarkdown(msg.content))}
          />
        ))}

        {showTyping && (
          <div className="chat-bubble assistant">
            <div className="chat-typing">
              <span className="dot" />
              <span className="dot" />
              <span className="dot" />
            </div>
          </div>
        )}

        <div ref={messagesEndRef} />
      </div>

      <div className="chat-input-area">
        <textarea
          ref={inputRef}
          className="chat-input"
          placeholder="输入消息，Shift+Enter 换行..."
          value={input}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={handleKeyDown}
          disabled={isProcessing}
          rows={2}
        />
        {isProcessing ? (
          <button className="chat-stop-btn" onClick={onStop} title="停止生成">
            停止
          </button>
        ) : (
          <button
            className="chat-send-btn"
            onClick={handleSend}
            disabled={!input.trim()}
          >
            发送
          </button>
        )}
      </div>
    </div>
  );
};

/** 单个对话气泡 */
const ChatBubble: React.FC<{
  message: ChatMessage;
  streaming: boolean;
  onInsert: () => void;
}> = ({ message, streaming, onInsert }) => {
  const isUser = message.role === 'user';
  const isEmpty = !message.content.trim();

  return (
    <div className={`chat-bubble ${message.role}${message.mock ? ' mock' : ''}${streaming ? ' streaming' : ''}`}>
      <div className="chat-bubble-role">
        {isUser ? (
          <>
            <User theme="outline" size="13" />你
          </>
        ) : (
          <>
            <Robot theme="outline" size="13" />AI
            {message.mock && <span className="chat-mock-tag">本地模拟</span>}
          </>
        )}
      </div>

      {isUser ? (
        <div className="chat-bubble-content user-text">{message.content}</div>
      ) : isEmpty && streaming ? null : (
        <div className="chat-bubble-content md">{renderMarkdown(message.content)}</div>
      )}

      {!isUser && !isEmpty && (
        <div className="chat-bubble-actions">
          <button className="cb-insert-btn" onClick={onInsert} title="把这段内容以纯文本插入到光标处">
            插入到文档
          </button>
        </div>
      )}
    </div>
  );
};

// ============ 安全 Markdown 渲染（不使用 dangerouslySetInnerHTML） ============

/** 行内：处理 **加粗** */
function renderInline(text: string, keyPrefix: string): ReactNode[] {
  const parts = text.split(/(\*\*[^*]+\*\*)/g);
  return parts.map((p, i) => {
    if (/^\*\*[^*]+\*\*$/.test(p)) {
      return <strong key={`${keyPrefix}-b-${i}`}>{p.slice(2, -2)}</strong>;
    }
    return <Fragment key={`${keyPrefix}-t-${i}`}>{p}</Fragment>;
  });
}

/** 把 Markdown 渲染成 React 节点（列表 / 标题 / 代码块 / 加粗 / 换行） */
function renderMarkdown(text: string): ReactNode {
  if (!text) return null;
  const lines = text.split('\n');
  const blocks: ReactNode[] = [];
  let i = 0;
  let key = 0;

  while (i < lines.length) {
    const line = lines[i];

    // 代码块
    if (line.trim().startsWith('```')) {
      const code: string[] = [];
      i++;
      while (i < lines.length && !lines[i].trim().startsWith('```')) {
        code.push(lines[i]);
        i++;
      }
      i++;
      blocks.push(
        <pre key={key++} className="md-pre">
          <code>{code.join('\n')}</code>
        </pre>,
      );
      continue;
    }

    // 标题
    const h = line.match(/^(#{1,4})\s+(.*)$/);
    if (h) {
      const content = renderInline(h[2], `h${key}`);
      blocks.push(
        h[1].length <= 2 ? <h3 key={key++}>{content}</h3> : <h4 key={key++}>{content}</h4>,
      );
      i++;
      continue;
    }

    // 无序列表
    if (/^\s*[-*]\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*[-*]\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*[-*]\s+/, ''));
        i++;
      }
      blocks.push(
        <ul key={key++} className="md-ul">
          {items.map((it, idx) => (
            <li key={idx}>{renderInline(it, `ul${key}-${idx}`)}</li>
          ))}
        </ul>,
      );
      continue;
    }

    // 有序列表
    if (/^\s*\d+\.\s+/.test(line)) {
      const items: string[] = [];
      while (i < lines.length && /^\s*\d+\.\s+/.test(lines[i])) {
        items.push(lines[i].replace(/^\s*\d+\.\s+/, ''));
        i++;
      }
      blocks.push(
        <ol key={key++} className="md-ol">
          {items.map((it, idx) => (
            <li key={idx}>{renderInline(it, `ol${key}-${idx}`)}</li>
          ))}
        </ol>,
      );
      continue;
    }

    // 段落（收集到空行或块起始）
    const para: string[] = [];
    while (
      i < lines.length &&
      lines[i].trim() !== '' &&
      !lines[i].trim().startsWith('```') &&
      !/^(#{1,4})\s+/.test(lines[i]) &&
      !/^\s*[-*]\s+/.test(lines[i]) &&
      !/^\s*\d+\.\s+/.test(lines[i])
    ) {
      para.push(lines[i]);
      i++;
    }
    if (para.length) {
      blocks.push(
        <p key={key++} className="md-p">
          {para.map((pl, idx) => (
            <Fragment key={idx}>
              {idx > 0 && <br />}
              {renderInline(pl, `p${key}-${idx}`)}
            </Fragment>
          ))}
        </p>,
      );
    } else {
      i++;
    }
  }
  return blocks;
}

/** 去掉 Markdown 标记，得到可插入文档的纯文本 */
function stripMarkdown(md: string): string {
  return md
    .replace(/```[\w]*\n?/g, '')
    .replace(/```/g, '')
    .replace(/^#{1,4}\s+/gm, '')
    .replace(/^\s*[-*]\s+/gm, '')
    .replace(/^\s*\d+\.\s+/gm, '')
    .replace(/\*\*([^*]+)\*\*/g, '$1')
    .replace(/`([^`]+)`/g, '$1')
    .replace(/\n{2,}/g, '\n\n')
    .trim();
}

export default ChatPanel;
