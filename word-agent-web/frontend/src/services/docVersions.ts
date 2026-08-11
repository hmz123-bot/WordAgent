/**
 * docVersions —— 文档「历史版本」仓库（localStorage 实现，纯前端无后端）。
 *
 * 与 docStore 的关系：
 *   docStore  → 保存文档的「当前状态」（一篇文档一条记录）
 *   docVersions → 保存文档的「历史轨迹」（一篇文档多条快照）
 *
 * 设计要点：
 *   - 单一 key `wa_doc_versions`，结构为 { [docId]: DocVersion[] }，数组按时间倒序（最新在前）
 *   - 内容去重：与最新一版 html + title 完全一致时不再落版本，避免反复点保存刷屏
 *   - 容量上限：每篇最多保留 MAX_PER_DOC 版，超出丢弃最旧的
 *   - 配额降级：localStorage 写满时按「每篇砍掉一半旧版本」重试，最终仍失败则静默放弃，
 *     绝不因为版本功能导致主流程（保存文档）崩溃
 *   - 变更后广播 DOC_VERSIONS_EVENT，供打开中的历史面板即时刷新
 */

/** 版本产生的原因，决定列表上的彩色标签 */
export type VersionReason =
  | 'initial' // 文档首次建立
  | 'save' // 手动点保存
  | 'snapshot' // 手动创建快照
  | 'restore' // 恢复旧版本前，对当前内容做的自动备份
  | 'draft' // 生成初稿覆盖前，对原内容做的自动备份
  | 'import'; // 从本地文件导入

export interface DocVersion {
  id: string;
  docId: string;
  title: string;
  html: string;
  /** 纯文本摘要，列表预览用 */
  snippet: string;
  /** 估算字数 */
  words: number;
  createdAt: string;
  reason: VersionReason;
  /** 附加说明，如「恢复前自动备份」 */
  note?: string;
}

const LS_KEY = 'wa_doc_versions';

/** 每篇文档最多保留的版本数 */
export const MAX_PER_DOC = 30;

/** 版本仓库变更事件 */
export const DOC_VERSIONS_EVENT = 'wa:doc-versions-changed';

export const VERSION_REASON_LABEL: Record<VersionReason, string> = {
  initial: '初始版本',
  save: '保存',
  snapshot: '手动快照',
  restore: '恢复前备份',
  draft: '生成初稿前备份',
  import: '导入',
};

type VersionMap = Record<string, DocVersion[]>;

// ========== 底层读写（异常安全 + 配额降级） ==========

function readAll(): VersionMap {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return {};
    const obj = JSON.parse(raw);
    return obj && typeof obj === 'object' && !Array.isArray(obj) ? (obj as VersionMap) : {};
  } catch {
    return {};
  }
}

/**
 * 写入版本表。localStorage 写满时逐轮裁剪旧版本重试，
 * 保证「存不下历史」不会连累「存得下正文」。
 */
function writeAll(map: VersionMap): boolean {
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      localStorage.setItem(LS_KEY, JSON.stringify(map));
      return true;
    } catch {
      // 每轮把每篇文档的版本数砍半（至少保留最新 1 版），再试
      let trimmed = false;
      for (const docId of Object.keys(map)) {
        const list = map[docId];
        if (list.length > 1) {
          map[docId] = list.slice(0, Math.max(1, Math.floor(list.length / 2)));
          trimmed = true;
        }
      }
      if (!trimmed) return false;
    }
  }
  return false;
}

// ========== 工具 ==========

function genVersionId(): string {
  return `ver-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

function htmlToText(html: string): string {
  const div = document.createElement('div');
  div.innerHTML = html || '';
  return (div.textContent || '').replace(/\s+/g, ' ').trim();
}

function toSnippet(html: string, max = 120): string {
  const text = htmlToText(html);
  return text.length > max ? text.slice(0, max) + '…' : text;
}

function countWords(html: string): number {
  const text = htmlToText(html);
  const cjk = (text.match(/[一-龥]/g) || []).length;
  const words = (text.replace(/[一-龥]/g, ' ').match(/[A-Za-z0-9]+/g) || []).length;
  return cjk + words;
}

/** 广播变更，供历史面板刷新 */
function notify(): void {
  try {
    window.dispatchEvent(new CustomEvent(DOC_VERSIONS_EVENT));
  } catch {
    /* SSR / 非浏览器环境 */
  }
}

// ========== 公开 API ==========

/** 列出某篇文档的全部版本（最新在前） */
export function listVersions(docId: string): DocVersion[] {
  if (!docId) return [];
  return readAll()[docId] || [];
}

/** 版本数量 */
export function countVersions(docId: string): number {
  return listVersions(docId).length;
}

/** 按 id 取单个版本 */
export function getVersion(docId: string, versionId: string): DocVersion | null {
  return listVersions(docId).find((v) => v.id === versionId) || null;
}

export interface RecordVersionInput {
  docId: string;
  title: string;
  html: string;
  reason: VersionReason;
  note?: string;
}

/**
 * 落一个新版本。
 * 若与最新一版内容 + 标题完全相同则跳过（返回 null），避免重复点保存产生一堆同样的版本。
 */
export function recordVersion(input: RecordVersionInput): DocVersion | null {
  const { docId } = input;
  if (!docId) return null;

  const map = readAll();
  const list = map[docId] || [];
  const title = input.title?.trim() || '未命名文档';
  const html = input.html || '';

  const latest = list[0];
  if (latest && latest.html === html && latest.title === title) return null;

  const version: DocVersion = {
    id: genVersionId(),
    docId,
    title,
    html,
    snippet: toSnippet(html),
    words: countWords(html),
    createdAt: new Date().toISOString(),
    reason: input.reason,
    note: input.note,
  };

  map[docId] = [version, ...list].slice(0, MAX_PER_DOC);
  if (!writeAll(map)) return null;
  notify();
  return version;
}

/** 删除单个版本 */
export function deleteVersion(docId: string, versionId: string): void {
  const map = readAll();
  const list = map[docId];
  if (!list) return;
  map[docId] = list.filter((v) => v.id !== versionId);
  if (map[docId].length === 0) delete map[docId];
  writeAll(map);
  notify();
}

/** 清空某篇文档的全部版本（删除文档时调用） */
export function clearVersions(docId: string): void {
  const map = readAll();
  if (!(docId in map)) return;
  delete map[docId];
  writeAll(map);
  notify();
}

/**
 * 把版本从旧 id 迁移到新 id。
 * 场景：写作台带入的 gen- 临时文档首次保存后拿到真实 id，历史不应丢失。
 */
export function migrateVersions(fromId: string, toId: string): void {
  if (!fromId || !toId || fromId === toId) return;
  const map = readAll();
  const list = map[fromId];
  if (!list || list.length === 0) return;
  const moved = list.map((v) => ({ ...v, docId: toId }));
  map[toId] = [...moved, ...(map[toId] || [])].slice(0, MAX_PER_DOC);
  delete map[fromId];
  writeAll(map);
  notify();
}

/** 相对时间展示：刚刚 / N 分钟前 / 今天 HH:mm / MM月DD日 HH:mm */
export function formatVersionTime(iso: string): string {
  const d = new Date(iso);
  const diff = Date.now() - d.getTime();
  const min = Math.floor(diff / 60000);
  if (min < 1) return '刚刚';
  if (min < 60) return `${min} 分钟前`;

  const hh = d.getHours().toString().padStart(2, '0');
  const mm = d.getMinutes().toString().padStart(2, '0');
  const today = new Date();
  const sameDay =
    d.getFullYear() === today.getFullYear() &&
    d.getMonth() === today.getMonth() &&
    d.getDate() === today.getDate();
  if (sameDay) return `今天 ${hh}:${mm}`;

  const month = (d.getMonth() + 1).toString().padStart(2, '0');
  const day = d.getDate().toString().padStart(2, '0');
  return `${month}月${day}日 ${hh}:${mm}`;
}

/** 把 HTML 转成纯文本（供版本对比使用） */
export function versionPlainText(html: string): string {
  const div = document.createElement('div');
  div.innerHTML = html || '';
  // 块级元素之间补换行，避免所有段落粘成一行影响 diff 可读性
  div.querySelectorAll('p,h1,h2,h3,h4,h5,h6,li,br,blockquote,pre,tr').forEach((el) => {
    el.appendChild(document.createTextNode('\n'));
  });
  return (div.textContent || '').replace(/\n{3,}/g, '\n\n').trim();
}
