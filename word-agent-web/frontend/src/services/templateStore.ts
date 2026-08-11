/**
 * templateStore —— 自定义模板（"我的模板"）的本地存储仓库。
 *
 * 与 docStore / docVersions 同构：单一 localStorage key、异常安全、写入广播事件。
 * 内置模板在 builtinTemplates.ts 中静态定义；本仓库只负责用户自己沉淀的模板，
 * 让用户把常用文档一键存为模板、随时复用。
 */

export interface CustomTemplate {
  id: string;
  title: string;
  html: string;
  /** 默认 "我的模板"；用户可在模板页按分类查看 */
  category: string;
  createdAt: string;
}

const LS_KEY = 'wa_templates';

/** 自定义模板发生变化时广播，供模板页即时刷新 */
export const TEMPLATE_EVENT = 'wa:templates-changed';

// ========== 底层读写（异常安全） ==========

function readAll(): CustomTemplate[] {
  try {
    const raw = localStorage.getItem(LS_KEY);
    if (!raw) return [];
    const arr = JSON.parse(raw);
    return Array.isArray(arr) ? (arr as CustomTemplate[]) : [];
  } catch {
    return [];
  }
}

function writeAll(list: CustomTemplate[]): void {
  try {
    localStorage.setItem(LS_KEY, JSON.stringify(list));
  } catch {
    /* 存储已满 / 隐私模式：静默降级 */
  }
}

function genId(): string {
  return `tpl-${Date.now()}-${Math.random().toString(36).slice(2, 8)}`;
}

// ========== 公开 API ==========

/** 列出全部自定义模板，按创建时间倒序 */
export function listTemplates(): CustomTemplate[] {
  return readAll().sort(
    (a, b) => new Date(b.createdAt).getTime() - new Date(a.createdAt).getTime(),
  );
}

export interface AddTemplateInput {
  title: string;
  html: string;
  category?: string;
}

/** 新增一个自定义模板，返回创建后的记录 */
export function addTemplate(input: AddTemplateInput): CustomTemplate {
  const created: CustomTemplate = {
    id: genId(),
    title: input.title?.trim() || '未命名模板',
    html: input.html || '',
    category: input.category?.trim() || '我的模板',
    createdAt: new Date().toISOString(),
  };
  const list = readAll();
  list.push(created);
  writeAll(list);
  notifyTemplatesChanged();
  return created;
}

/** 删除自定义模板 */
export function deleteTemplate(id: string): void {
  writeAll(readAll().filter((t) => t.id !== id));
  notifyTemplatesChanged();
}

/** 广播模板仓库变化事件 */
export function notifyTemplatesChanged(): void {
  window.dispatchEvent(new CustomEvent(TEMPLATE_EVENT));
}
