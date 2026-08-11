/**
 * docExport —— 文档导出服务（纯前端，无需后端）。
 *
 * 支持的导出格式（文件类型可由用户自由选择）：
 *   - html : 完整网页（保留排版与样式）
 *   - md   : Markdown（便于二次编辑 / 迁移）
 *   - txt  : 纯文本（仅文字）
 *   - docx : Word 文档（docx 库在浏览器端逐元素生成 OOXML，无 Node 依赖）
 *   - pdf  : 通过浏览器打印窗口另存为 PDF
 *
 * 设计要点：
 *   - 所有格式都基于编辑器当前的 HTML（Tiptap getHTML()）
 *   - docx 走 docx 库（浏览器原生，无需 Node 垫片），逐元素把 HTML 转成 Word 文档
 *   - pdf 不复制造 PDF 二进制，而是打开一个干净的打印窗口，由浏览器「另存为 PDF」
 *   - 统一通过临时 <a download> 触发浏览器下载
 */

import TurndownService from 'turndown';
import {
  Document,
  Packer,
  Paragraph,
  TextRun,
  HeadingLevel,
  AlignmentType,
  LevelFormat,
  ShadingType,
  BorderStyle,
  ExternalHyperlink,
  ImageRun,
} from 'docx';

export type ExportFormat = 'html' | 'md' | 'txt' | 'docx' | 'pdf';

export interface ExportOption {
  format: ExportFormat;
  /** 菜单显示名 */
  label: string;
  /** 菜单副说明 */
  desc: string;
  /** 文件扩展名（含点） */
  ext: string;
}

export const EXPORT_OPTIONS: ExportOption[] = [
  { format: 'html', label: 'HTML 网页', desc: '保留完整排版与样式', ext: '.html' },
  { format: 'md', label: 'Markdown', desc: '适合二次编辑 / 迁移', ext: '.md' },
  { format: 'txt', label: '纯文本', desc: '仅文字，无格式', ext: '.txt' },
  { format: 'docx', label: 'Word 文档', desc: '.docx，可用 Office / WPS 打开', ext: '.docx' },
  { format: 'pdf', label: 'PDF', desc: '调用浏览器打印另存为 PDF', ext: '.pdf' },
];

// ========== 通用工具 ==========

/** 根据标题生成安全的文件名（去除文件系统非法字符） */
function safeFileName(title: string, ext: string): string {
  const base =
    (title || '未命名文档')
      .trim()
      .replace(/[\\/:*?"<>|\n\r]+/g, '_')
      .replace(/\s+/g, ' ')
      .trim() || '未命名文档';
  return base + ext;
}

/** 用临时 <a download> 触发浏览器下载，并清理 ObjectURL */
function triggerDownload(blob: Blob, filename: string): void {
  const url = URL.createObjectURL(blob);
  const a = document.createElement('a');
  a.href = url;
  a.download = filename;
  document.body.appendChild(a);
  a.click();
  a.remove();
  setTimeout(() => URL.revokeObjectURL(url), 1500);
}

function escapeHtml(s: string): string {
  return s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');
}

/** 拼成完整 HTML 文档（pdf 需要完整结构；docx 内部也会用） */
function fullHtmlDoc(title: string, bodyHtml: string): string {
  return `<!DOCTYPE html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<title>${escapeHtml(title)}</title>
</head>
<body>
${bodyHtml}
</body>
</html>`;
}

// ========== 文本 / Markdown ==========

/** HTML → 纯文本（保留基本换行，去除标签） */
function htmlToPlainText(html: string): string {
  const div = document.createElement('div');
  div.innerHTML = html || '';
  div.querySelectorAll('br').forEach((b) => b.replaceWith('\n'));
  div
    .querySelectorAll('p, div, li, h1, h2, h3, h4, h5, h6, blockquote, tr, hr')
    .forEach((el) => {
      el.appendChild(document.createTextNode('\n'));
    });
  const text = div.textContent || '';
  return text.replace(/\n{3,}/g, '\n\n').replace(/[ \t]+\n/g, '\n').replace(/^\n+|\n+$/g, '') + '\n';
}

let turndown: TurndownService | null = null;
function getTurndown(): TurndownService {
  if (!turndown) {
    turndown = new TurndownService({
      headingStyle: 'atx',
      hr: '---',
      bulletListMarker: '-',
      codeBlockStyle: 'fenced',
      emDelimiter: '*',
    });
  }
  return turndown;
}

/** HTML → Markdown */
function htmlToMarkdown(html: string): string {
  return getTurndown().turndown(html || '');
}

// ========== HTML → docx（docx 库，浏览器原生） ==========

const HEADING_MAP: Record<string, any> = {
  h1: HeadingLevel.HEADING_1,
  h2: HeadingLevel.HEADING_2,
  h3: HeadingLevel.HEADING_3,
  h4: HeadingLevel.HEADING_4,
  h5: HeadingLevel.HEADING_5,
  h6: HeadingLevel.HEADING_6,
};

interface RunStyle {
  bold?: boolean;
  italics?: boolean;
  underline?: boolean;
  strike?: boolean;
  code?: boolean;
  /** 行内换行（<br>） */
  br?: boolean;
}

interface BlockCtx {
  indent?: number;
  italic?: boolean;
}

type InlineItem = TextRun | ExternalHyperlink;

function classListOf(el: Element): string[] {
  return (el.getAttribute('class') || '').split(/\s+/).filter(Boolean);
}

/** atob 的跨环境兼容（浏览器有 atob，Node 端测试时用 Buffer 兜底） */
function atobCompat(s: string): string {
  const g = globalThis as any;
  if (typeof g.atob === 'function') return g.atob(s);
  return Buffer.from(s, 'base64').toString('binary');
}

function makeRun(text: string, style: RunStyle): TextRun {
  const opts: any = { text: style.br ? '' : text };
  if (style.br) opts.break = 1;
  if (style.bold) opts.bold = true;
  if (style.italics) opts.italics = true;
  if (style.underline) opts.underline = {};
  if (style.strike) opts.strike = true;
  if (style.code) {
    opts.font = 'Consolas';
    opts.shading = { type: ShadingType.CLEAR, fill: 'F2F2F2' };
  }
  return new TextRun(opts);
}

/** 把某个节点的子节点转成行内元素（TextRun / 超链接） */
function inlineItems(node: Node, style: RunStyle): InlineItem[] {
  const items: InlineItem[] = [];
  node.childNodes.forEach((child) => {
    if (child.nodeType === 3 /* TEXT_NODE */) {
      const text = child.textContent || '';
      if (text) items.push(makeRun(text, style));
      return;
    }
    if (child.nodeType !== 1) return;
    const el = child as Element;
    const tag = el.tagName.toLowerCase();
    const cls = classListOf(el);
    const st: RunStyle = { ...style };

    if (tag === 'strong' || tag === 'b' || cls.includes('font-bold')) st.bold = true;
    if (tag === 'em' || tag === 'i') st.italics = true;
    if (tag === 'u') st.underline = true;
    if (tag === 's' || tag === 'strike' || tag === 'del') st.strike = true;
    if (tag === 'code') st.code = true;
    if (tag === 'br') {
      items.push(makeRun('', { ...st, br: true }));
      return;
    }
    if (tag === 'a') {
      const href = el.getAttribute('href') || '';
      const text = el.textContent || href;
      items.push(new ExternalHyperlink({ children: [new TextRun({ text, style: 'Hyperlink' })], link: href }));
      return;
    }
    items.push(...inlineItems(el, st));
  });
  return items;
}

/** 解析图片 data URI → ImageRun（仅支持 png/jpg/gif；其余忽略，退化为占位） */
function imageRunFromSrc(src: string): ImageRun | null {
  const m = /^data:(image\/(png|jpeg|jpg|gif));base64,(.+)$/i.exec(src);
  if (!m) return null;
  const mime = m[1].toLowerCase();
  const typeMap: Record<string, 'png' | 'jpg' | 'gif'> = {
    'image/png': 'png',
    'image/jpeg': 'jpg',
    'image/jpg': 'jpg',
    'image/gif': 'gif',
  };
  const type = typeMap[mime];
  if (!type) return null;
  const bin = atobCompat(m[3]);
  const bytes = new Uint8Array(bin.length);
  for (let i = 0; i < bin.length; i++) bytes[i] = bin.charCodeAt(i);

  const MAX = 480;
  let w = MAX;
  let h = MAX;
  try {
    if (type === 'png') {
      w = bytes[16] * 256 + bytes[17];
      h = bytes[18] * 256 + bytes[19];
    } else if (type === 'jpg') {
      let i = 2;
      while (i < bytes.length - 9) {
        if (bytes[i] !== 0xff) {
          i++;
          continue;
        }
        const marker = bytes[i + 1];
        if (marker >= 0xc0 && marker <= 0xcf && marker !== 0xc4 && marker !== 0xc8 && marker !== 0xcc) {
          h = bytes[i + 5] * 256 + bytes[i + 6];
          w = bytes[i + 7] * 256 + bytes[i + 8];
          break;
        }
        const len = bytes[i + 2] * 256 + bytes[i + 3];
        i += 2 + len;
      }
    }
  } catch {
    /* 保留默认尺寸 */
  }
  if (w > MAX) {
    const r = MAX / w;
    w = MAX;
    h = Math.round(h * r);
  }
  w = Math.max(1, Math.round(w));
  h = Math.max(1, Math.round(h));
  return new ImageRun({ type, data: bytes, transformation: { width: w, height: h } });
}

/** 列表项 → 段落（含嵌套列表递归） */
function liToParagraphs(li: Element, depth: number, ref: string, ctx: BlockCtx): Paragraph[] {
  const res: Paragraph[] = [];
  const items: InlineItem[] = [];
  const nested: Paragraph[] = [];
  li.childNodes.forEach((c) => {
    if (c.nodeType === 3 /* TEXT_NODE */) {
      const t = c.textContent || '';
      if (t.trim()) items.push(makeRun(t, { italics: ctx.italic }));
      return;
    }
    if (c.nodeType === 1) {
      const ce = c as Element;
      const ct = ce.tagName.toLowerCase();
      if (ct === 'ul') {
        nested.push(...listToParagraphs(ce, depth + 1, 'bullets', ctx));
        return;
      }
      if (ct === 'ol') {
        nested.push(...listToParagraphs(ce, depth + 1, 'numbering', ctx));
        return;
      }
      items.push(...inlineItems(ce, { italics: ctx.italic }));
    }
  });
  if (items.length) {
    res.push(
      new Paragraph({
        numbering: { reference: ref, level: depth },
        indent: ctx.indent ? { left: ctx.indent } : undefined,
        children: items,
      }),
    );
  }
  res.push(...nested);
  return res;
}

function listToParagraphs(ulEl: Element, depth: number, ref: string, ctx: BlockCtx): Paragraph[] {
  const res: Paragraph[] = [];
  ulEl.childNodes.forEach((c) => {
    if (c.nodeType === 1 && (c as Element).tagName.toLowerCase() === 'li') {
      res.push(...liToParagraphs(c as Element, depth, ref, ctx));
    }
  });
  return res;
}

/** 遍历块级子节点 → docx 段落数组 */
function blocksToParagraphs(parent: Node, depth: number, ctx: BlockCtx = {}): Paragraph[] {
  const out: Paragraph[] = [];
  parent.childNodes.forEach((child) => {
    if (child.nodeType === 3 /* TEXT_NODE */) {
      const t = (child.textContent || '').trim();
      if (t) out.push(new Paragraph({ children: [makeRun(t, {})] }));
      return;
    }
    if (child.nodeType !== 1) return;
    const el = child as Element;
    const tag = el.tagName.toLowerCase();

    if (HEADING_MAP[tag]) {
      const items = inlineItems(el, {});
      if (items.length) out.push(new Paragraph({ heading: HEADING_MAP[tag], children: items }));
    } else if (tag === 'p') {
      const items = inlineItems(el, {});
      if (items.length) out.push(new Paragraph({ children: items }));
    } else if (tag === 'blockquote') {
      const qctx: BlockCtx = { indent: 720, italic: true };
      el.childNodes.forEach((c) => {
        if (c.nodeType === 1) {
          const ce = c as Element;
          const ct = ce.tagName.toLowerCase();
          if (ct === 'ul' || ct === 'ol') {
            out.push(...listToParagraphs(ce, 0, ct === 'ul' ? 'bullets' : 'numbering', qctx));
          } else {
            const items = inlineItems(ce, { italics: true });
            if (items.length) out.push(new Paragraph({ indent: { left: 720 }, children: items }));
          }
        }
      });
    } else if (tag === 'ul' || tag === 'ol') {
      out.push(...listToParagraphs(el, depth, tag === 'ul' ? 'bullets' : 'numbering', ctx));
    } else if (tag === 'pre') {
      const codeEl = el.querySelector('code');
      const text = (codeEl || el).textContent || '';
      const lines = text.split('\n');
      const runs: TextRun[] = [];
      lines.forEach((ln, i) => {
        runs.push(new TextRun({ text: ln || ' ', font: 'Consolas', shading: { type: ShadingType.CLEAR, fill: 'F2F2F2' } }));
        if (i < lines.length - 1) runs.push(new TextRun({ text: '', break: 1 }));
      });
      out.push(new Paragraph({ children: runs }));
    } else if (tag === 'hr') {
      out.push(
        new Paragraph({
          border: { bottom: { color: '999999', space: 1, style: BorderStyle.SINGLE, size: 6 } },
          children: [new TextRun('')],
        }),
      );
    } else if (tag === 'img') {
      const ir = imageRunFromSrc(el.getAttribute('src') || '');
      if (ir) out.push(new Paragraph({ children: [ir] }));
      else out.push(new Paragraph({ children: [makeRun('[图片]', {})] }));
    } else if (tag === 'table') {
      el.querySelectorAll('tr').forEach((tr) => {
        const cells = Array.from(tr.querySelectorAll('td,th'))
          .map((td) => (td.textContent || '').trim())
          .filter(Boolean);
        if (cells.length) out.push(new Paragraph({ children: [makeRun(cells.join('  |  '), {})] }));
      });
    } else if (tag === 'div') {
      out.push(...blocksToParagraphs(el, depth, ctx));
    } else {
      const items = inlineItems(el, { italics: ctx.italic });
      if (items.length) {
        out.push(new Paragraph({ indent: ctx.indent ? { left: ctx.indent } : undefined, children: items }));
      }
    }
  });
  return out;
}

/** 生成 docx 所需的列表编号配置（bullet / decimal，支持 4 级嵌套） */
function buildNumbering(): any {
  const makeLevels = (format: any, text: (lvl: number) => string) =>
    [0, 1, 2, 3].map((lvl) => ({
      level: lvl,
      format,
      text: text(lvl),
      alignment: AlignmentType.LEFT,
      style: {
        paragraph: { indent: { left: 360 + lvl * 360, hanging: 360 } },
        run: format === LevelFormat.BULLET ? { font: 'Symbol' } : undefined,
      },
    }));
  return {
    config: [
      { reference: 'bullets', levels: makeLevels(LevelFormat.BULLET, (lvl) => ['•', 'o', '▪', ''][lvl] || '•') },
      { reference: 'numbering', levels: makeLevels(LevelFormat.DECIMAL, (lvl) => `${lvl + 1}.`) },
    ],
  };
}

/** HTML → Word .docx Blob（docx 库在浏览器端生成，无 Node 依赖） */
export async function htmlToDocxBlob(title: string, bodyHtml: string): Promise<Blob> {
  const parser = new DOMParser();
  const doc = parser.parseFromString(
    `<html><head><meta charset="utf-8"><title>${escapeHtml(title)}</title></head><body>${bodyHtml}</body></html>`,
    'text/html',
  );
  const children = blocksToParagraphs(doc.body, 0, {});
  const docx = new Document({
    title,
    numbering: buildNumbering(),
    sections: [{ children }],
  });
  return Packer.toBlob(docx);
}

// ========== PDF（打印窗口另存） ==========

/** HTML → 新窗口并打印（用于 PDF 另存） */
function openPrintWindow(title: string, bodyHtml: string): void {
  const win = window.open('', '_blank', 'width=900,height=720');
  if (!win) {
    throw new Error('无法打开打印窗口，请允许浏览器弹出窗口后重试');
  }
  win.document.open();
  win.document.write(fullHtmlDoc(title, bodyHtml));
  win.document.close();
  win.focus();
  setTimeout(() => win.print(), 350);
}

// ========== 统一导出入口 ==========

/**
 * 将文档导出为指定格式并触发下载。
 * @param html 文档 HTML（通常来自编辑器 getHTML()）
 * @param title 文档标题（用于文件名）
 * @param format 目标格式
 */
export async function exportDocument(html: string, title: string, format: ExportFormat): Promise<void> {
  const safeTitle = title || '未命名文档';
  switch (format) {
    case 'html': {
      const blob = new Blob([fullHtmlDoc(safeTitle, html)], { type: 'text/html;charset=utf-8' });
      triggerDownload(blob, safeFileName(safeTitle, '.html'));
      return;
    }
    case 'txt': {
      const blob = new Blob([htmlToPlainText(html)], { type: 'text/plain;charset=utf-8' });
      triggerDownload(blob, safeFileName(safeTitle, '.txt'));
      return;
    }
    case 'md': {
      const blob = new Blob([htmlToMarkdown(html)], { type: 'text/markdown;charset=utf-8' });
      triggerDownload(blob, safeFileName(safeTitle, '.md'));
      return;
    }
    case 'docx': {
      const blob = await htmlToDocxBlob(safeTitle, html);
      triggerDownload(blob, safeFileName(safeTitle, '.docx'));
      return;
    }
    case 'pdf': {
      openPrintWindow(safeTitle, html);
      return;
    }
    default:
      throw new Error('不支持的导出格式: ' + String(format));
  }
}
