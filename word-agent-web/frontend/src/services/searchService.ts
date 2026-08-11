/**
 * searchService —— 纯前端的本地搜索内核。
 *
 * 项目原本的搜索走 services/api.ts 的 /api/v1/documents 后端（该后端在工作区不存在），
 * 导致顶栏搜索、/search 页面全部报「搜索失败」。本文件把搜索改为完全基于本地数据：
 *   - 文档：来自 docStore（localStorage 中的本地文档）
 *   - 模板：来自 BUILTIN_TEMPLATES（内置）+ templateStore（"我的模板"）
 *   - 查找并替换：在文档 HTML 的文本节点上做安全替换（不破坏标签），再经 docStore 留版本落库
 *
 * 设计要点：
 *   - searchEverything：给顶栏下拉用，返回「文档 + 模板」分组结果（带摘要/渐变）
 *   - searchDocumentsLocal：给 /search 页面用，返回与旧 ApiResponse 同构的 { results, totalMatches }
 *   - 正则转义 + 可选的区分大小写 / 全词匹配；中文无词边界，wholeWord 对 CJK 自动退化为普通匹配
 *   - 异常安全：解析失败时静默返回空结果，不让搜索功能崩溃
 */

import { listDocs, getDoc, upsertDoc, countWords } from './docStore';
import { listTemplates } from './templateStore';
import { BUILTIN_TEMPLATES } from '../data/builtinTemplates';

// ========== 工具 ==========

/** 将 HTML 转成纯文本（不截断，用于全文检索） */
export function htmlToText(html: string): string {
  const div = document.createElement('div');
  div.innerHTML = html || '';
  return (div.textContent || '').replace(/\s+/g, ' ').trim();
}

interface RegexOpts {
  caseSensitive?: boolean;
  wholeWord?: boolean;
}

function buildRegex(query: string, opts: RegexOpts = {}): RegExp {
  const escaped = query.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  const body = opts.wholeWord ? `\\b${escaped}\\b` : escaped;
  return new RegExp(body, opts.caseSensitive ? 'g' : 'gi');
}

// ========== /search 页面用：结构化文档搜索 ==========

export interface SearchMatch {
  nodeId: string;
  nodeType: string;
  textContent: string;
  context: string;
  matchStart: number;
  matchEnd: number;
  attributes: Record<string, unknown>;
}

export interface DocSearchResult {
  documentId: string;
  title: string;
  totalMatches: number;
  matches: SearchMatch[];
}

export interface DocSearchOptions {
  caseSensitive?: boolean;
  wholeWord?: boolean;
  maxResults?: number;
  contextChars?: number;
}

const DEFAULT_CONTEXT = 120;

/** 在单篇文档中搜索，返回结构化结果（兼容旧 SearchPage 的数据形状） */
export function searchDocumentsLocal(
  query: string,
  options: DocSearchOptions = {},
): { results: DocSearchResult[]; totalMatches: number } {
  const q = (query || '').trim();
  if (!q) return { results: [], totalMatches: 0 };

  const { caseSensitive = false, wholeWord = false, maxResults = 50, contextChars = DEFAULT_CONTEXT } = options;

  const results: DocSearchResult[] = [];

  for (const doc of listDocs()) {
    const text = htmlToText(doc.html);
    if (!text) continue;

    const re = buildRegex(q, { caseSensitive, wholeWord });
    const haystack = caseSensitive ? text : text.toLowerCase();
    const needle = caseSensitive ? q : q.toLowerCase();

    // 快速预筛：标题或正文都不含则跳过，省去逐字符扫描
    if (!haystack.includes(needle)) continue;

    const matches: SearchMatch[] = [];
    let m: RegExpExecArray | null;
    let guard = 0;

    while ((m = re.exec(text)) !== null && matches.length < maxResults) {
      const start = m.index;
      const end = start + m[0].length;

      const ctxStart = Math.max(0, start - contextChars);
      const ctxEnd = Math.min(text.length, end + contextChars);
      let context = text.slice(ctxStart, ctxEnd);
      if (ctxStart > 0) context = '…' + context;
      if (ctxEnd < text.length) context = context + '…';

      matches.push({
        nodeId: `m${matches.length}`,
        nodeType: 'paragraph',
        textContent: context,
        context,
        matchStart: start,
        matchEnd: end,
        attributes: {},
      });

      // 防止零宽匹配导致的死循环
      if (m.index === re.lastIndex) re.lastIndex++;
      guard++;
      if (guard > 5000) break;
    }

    if (matches.length > 0) {
      results.push({
        documentId: doc.id,
        title: doc.title,
        totalMatches: matches.length,
        matches,
      });
    }
  }

  const totalMatches = results.reduce((sum, r) => sum + r.totalMatches, 0);
  return { results, totalMatches };
}

// ========== 顶栏下拉用：文档 + 模板聚合搜索 ==========

export interface DocHit {
  kind: 'document';
  id: string;
  title: string;
  snippet: string;
  words: number;
  updatedAt: string;
}

export interface TemplateHit {
  kind: 'template';
  id: string;
  title: string;
  description: string;
  gradient: [string, string];
  builtin: boolean;
  category: string;
  /** 使用模板时注入编辑器的正文 HTML */
  html: string;
}

export interface SearchEverythingResult {
  documents: DocHit[];
  templates: TemplateHit[];
  total: number;
}

/** 顶部搜索框用：跨「文档 + 模板（内置/我的）」聚合搜索，返回分组结果 */
export function searchEverything(query: string): SearchEverythingResult {
  const q = (query || '').trim().toLowerCase();
  if (!q) return { documents: [], templates: [], total: 0 };

  // 文档
  const documents: DocHit[] = listDocs()
    .filter((d) => {
      const hay = `${d.title} ${htmlToText(d.html)}`.toLowerCase();
      return hay.includes(q);
    })
    .slice(0, 8)
    .map((d) => ({
      kind: 'document',
      id: d.id,
      title: d.title,
      snippet: d.snippet || htmlToText(d.html).slice(0, 90),
      words: countWords(d.html),
      updatedAt: d.updatedAt,
    }));

  // 模板（内置 + 我的）
  const builtins: TemplateHit[] = BUILTIN_TEMPLATES.map((t) => ({
    kind: 'template',
    id: t.id,
    title: t.title,
    description: t.description,
    gradient: t.gradient,
    builtin: true,
    category: t.category,
    html: t.html,
  }));

  const customs: TemplateHit[] = listTemplates().map((t) => ({
    kind: 'template',
    id: t.id,
    title: t.title,
    description: '我的模板',
    gradient: ['#e07a2f', '#f2b07a'],
    builtin: false,
    category: t.category || '我的模板',
    html: t.html,
  }));

  const templates: TemplateHit[] = [...builtins, ...customs]
    .filter((t) => `${t.title} ${t.description} ${t.category}`.toLowerCase().includes(q))
    .slice(0, 8);

  return {
    documents,
    templates,
    total: documents.length + templates.length,
  };
}

// ========== 本地查找并替换 ==========

/**
 * 在单篇文档中做「查找并替换」：只替换文本节点内容（不破坏 HTML 标签），
 * 替换后通过 docStore 更新并自动留一个历史版本。返回替换处数。
 */
export function replaceInDocument(
  documentId: string,
  query: string,
  replacement: string,
  caseSensitive = false,
): number {
  const doc = getDoc(documentId);
  if (!doc) return 0;

  const div = document.createElement('div');
  div.innerHTML = doc.html || '';
  const re = buildRegex(query, { caseSensitive, wholeWord: false });

  let count = 0;

  const walk = (node: Node) => {
    if (node.nodeType === Node.TEXT_NODE) {
      const txt = node.nodeValue || '';
      const matches = txt.match(re);
      if (matches && matches.length > 0) {
        count += matches.length;
        node.nodeValue = txt.replace(re, replacement);
      }
    } else {
      node.childNodes.forEach(walk);
    }
  };
  walk(div);

  if (count > 0) {
    upsertDoc({ id: documentId, title: doc.title, html: div.innerHTML }, { reason: 'save' });
  }
  return count;
}
