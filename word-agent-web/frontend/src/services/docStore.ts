/**
 * docStore —— 纯前端的本地文档仓库（localStorage 实现）。
 *
 * 背景：项目里原生的文档管理走 /api 后端（api.ts → localhost:8081），
 * 该后端在本工作区不存在，因此编辑器既无持久层也无保存入口。
 * docStore 让「保存」成为完全可用的前端能力，无需任何后端：
 *   - 编辑器加载已有文档（按 id）
 *   - 编辑器保存/自动保存（生成真实 id，URL 同步）
 *   - 文档列表读取、新建、删除、刷新
 *
 * 设计要点：
 *   - 单一 localStorage key `wa_docs` 存储全部文档数组（文档体量小，单键足够）
 *   - 真实文档 id 形如 `doc-<ts>-<rand>`；写作台跳入的临时 id 形如 `gen-<ts>`（未持久化）
 *   - upsertDoc 对已有 id 做更新、对 gen- / 缺失 id 做新建，并始终回写 updatedAt
 *   - 保存后广播 `DOC_STORE_EVENT` 自定义事件，供文档列表跨路由即时刷新
 *   - 每次保存自动向 docVersions 落一个历史版本（内容未变则自动跳过）
 *   - 预留真实后端接入点：日后若接后端，只需替换本文件的 4 个函数实现，调用方不变
 */

import { recordVersion, clearVersions, type VersionReason } from './docVersions';

export interface LocalDoc {
  id: string;
  title: string;
  html: string;
  /** 可选：Tiptap JSON，便于未来精确 diff / 协同 */
  json?: Record<string, any>;
  /** 纯文本摘要，用于列表预览 */
  snippet: string;
  createdAt: string;
  updatedAt: string;
}

const LS_KEY = 'wa_docs';

/** 自定义事件名：文档仓库发生变化时广播，供其他路由（如文档列表）刷新 */
export const DOC_STORE_EVENT = 'wa:docs-changed';

// ========== 底层读写（异常安全） ==========

function readAll(): LocalDoc[] {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? (arr as LocalDoc[]) : [];
  } catch {
    return [];
  }
}

function writeAll(docs: LocalDoc[]): void {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(docs));
  } catch {
    /* 存储已满 / 隐私模式：静默降级，保存能力不可用但不崩溃 */
  }
}

// ========== 工具函数 ==========

/** 判断是否为写作台临时 id（未持久化） */
export function isGenId(id?: string | null): boolean {
  return !!id && id.startsWith('gen-');
}

/** 生成一个真实的本地文档 id */
export function genDocId(): string {
  return `doc-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

/** 将 HTML 转为纯文本摘要（去标签、压空白、截断） */
export function htmlToSnippet(html: string, max = 140): string {
  const div = document.createElement('div');
  div.innerHTML = html || '';
  const text = (div.textContent || '').replace(/\s+/g, ' ').trim();
  return text.length > max ? text.slice(0, max) + '…' : text;
}

/** 估算正文字数（中文字符 + 英文单词） */
export function countWords(html: string): number {
  const div = document.createElement('div');
  div.innerHTML = html || '';
  const text = div.textContent || '';
  const cjk = (text.match(/[一-龥]/g) || []).length;
  const words = (text.replace(/[一-龥]/g, ' ').match(/[A-Za-z0-9]+/g) || []).length;
  return cjk + words;
}

// ========== 公开 API ==========

/** 列出全部文档，按更新时间倒序 */
export function listDocs(): LocalDoc[] {
  return readAll().sort(
    (a, b) => new Date(b.updatedAt).getTime() - new Date(a.updatedAt).getTime(),
  );
}

/** 按 id 读取单个文档 */
export function getDoc(id: string): LocalDoc | null {
  return readAll().find((d) => d.id === id) || null;
}

export interface SaveDocInput {
  /** 已有真实 id 传此值；gen- 或省略则新建 */
  id?: string;
  title: string;
  html: string;
  json?: Record<string, any>;
}

/** 保存时的附加选项 */
export interface UpsertOptions {
  /** 是否落历史版本，默认 true */
  snapshot?: boolean;
  /** 版本来源标签，默认更新为 'save'、新建为 'initial' */
  reason?: VersionReason;
  /** 版本备注 */
  note?: string;
}

/** 新建或更新文档，返回保存后的完整记录 */
export function upsertDoc(input: SaveDocInput, opts: UpsertOptions = {}): LocalDoc {
  const docs = readAll();
  const now = new Date().toISOString();
  const safeTitle = input.title?.trim() || '未命名文档';
  const takeSnapshot = opts.snapshot !== false;

  // 已存在真实 id → 更新
  if (input.id && !isGenId(input.id)) {
    const idx = docs.findIndex((d) => d.id === input.id);
    if (idx >= 0) {
      const updated: LocalDoc = {
        ...docs[idx],
        title: safeTitle,
        html: input.html,
        json: input.json,
        snippet: htmlToSnippet(input.html),
        updatedAt: now,
      };
      docs[idx] = updated;
      writeAll(docs);
      if (takeSnapshot) {
        recordVersion({
          docId: updated.id,
          title: updated.title,
          html: updated.html,
          reason: opts.reason || 'save',
          note: opts.note,
        });
      }
      return updated;
    }
  }

  // 新建
  const created: LocalDoc = {
    id: input.id && !isGenId(input.id) ? input.id : genDocId(),
    title: safeTitle,
    html: input.html,
    json: input.json,
    snippet: htmlToSnippet(input.html),
    createdAt: now,
    updatedAt: now,
  };
  docs.push(created);
  writeAll(docs);
  if (takeSnapshot) {
    recordVersion({
      docId: created.id,
      title: created.title,
      html: created.html,
      reason: opts.reason || 'initial',
      note: opts.note,
    });
  }
  return created;
}

export interface SaveAsInput {
  title: string;
  html: string;
  json?: Record<string, any>;
}

/**
 * 另存为：把当前内容复制成一篇**全新文档**，不影响原文档，也不继承其历史版本。
 * 与 upsertDoc 的区别是永远不会命中"更新已有 id"分支。
 */
export function saveAsDoc(input: SaveAsInput): LocalDoc {
  const docs = readAll();
  const now = new Date().toISOString();
  const created: LocalDoc = {
    id: genDocId(),
    title: input.title?.trim() || '未命名文档',
    html: input.html,
    json: input.json,
    snippet: htmlToSnippet(input.html),
    createdAt: now,
    updatedAt: now,
  };
  docs.push(created);
  writeAll(docs);
  recordVersion({
    docId: created.id,
    title: created.title,
    html: created.html,
    reason: 'initial',
    note: '由「另存为」创建',
  });
  return created;
}

/** 删除文档（连带清理其历史版本，避免残留占用存储） */
export function deleteDoc(id: string): void {
  writeAll(readAll().filter((d) => d.id !== id));
  clearVersions(id);
}

/** 广播文档仓库变化事件（供其他路由刷新） */
export function notifyDocsChanged(): void {
  window.dispatchEvent(new CustomEvent(DOC_STORE_EVENT));
}
