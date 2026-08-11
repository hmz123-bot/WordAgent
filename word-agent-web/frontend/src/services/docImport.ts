import mammoth from 'mammoth';
import { upsertDoc, type LocalDoc } from './docStore';

/** 支持导入的本地文件类型 */
export const IMPORT_ACCEPT = '.txt,.md,.markdown,.html,.htm,.docx';
export const IMPORT_LABEL = '文本 / Word 文件 (.txt, .md, .html, .docx)';

function filenameToTitle(name: string): string {
  return name.replace(/\.[^.]+$/, '').trim() || '未命名文档';
}

/** 轻量 Markdown → HTML（覆盖标题/加粗/斜体/行内代码/列表/链接） */
function mdToHtml(md: string): string {
  const lines = md.replace(/\r\n/g, '\n').split('\n');
  const out: string[] = [];
  let inList = false;
  const closeList = () => {
    if (inList) {
      out.push('</ul>');
      inList = false;
    }
  };
  const inline = (s: string) =>
    s
      .replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>')
      .replace(/(?<!\*)\*(?!\*)(.+?)\*/g, '<em>$1</em>')
      .replace(/`(.+?)`/g, '<code>$1</code>')
      .replace(/\[(.+?)\]\((.+?)\)/g, '<a href="$2">$1</a>');

  for (const raw of lines) {
    const line = raw.replace(/\s+$/, '');
    const h = line.match(/^(#{1,6})\s+(.*)$/);
    const li = line.match(/^[-*]\s+(.*)$/);
    if (h) {
      closeList();
      const level = h[1].length;
      out.push(`<h${level}>${inline(h[2])}</h${level}>`);
    } else if (li) {
      if (!inList) {
        out.push('<ul>');
        inList = true;
      }
      out.push(`<li>${inline(li[1])}</li>`);
    } else if (line.trim() === '') {
      closeList();
    } else {
      closeList();
      out.push(`<p>${inline(line)}</p>`);
    }
  }
  closeList();
  return out.join('\n');
}

/** 纯文本 → HTML（按空行分段） */
function textToHtml(txt: string): string {
  return txt
    .replace(/\r\n/g, '\n')
    .split(/\n\s*\n/)
    .map((block) => block.trim())
    .filter(Boolean)
    .map((block) => `<p>${block.replace(/\n/g, '<br/>')}</p>`)
    .join('\n');
}

/** HTML 文件 → 取 body 内容，去掉 script/style */
function htmlToHtml(html: string): string {
  const doc = new DOMParser().parseFromString(html, 'text/html');
  doc.querySelectorAll('script,style,noscript').forEach((el) => el.remove());
  return doc.body ? doc.body.innerHTML : html;
}

/**
 * 读取本地文件并导入为一篇本地文档。
 * 支持 .txt / .md(.markdown) / .html(.htm) / .docx；其余类型抛出明确错误。
 * 完全客户端、零后端依赖，复用 docStore.upsertDoc 入库。
 */
export function importDocumentFile(file: File): Promise<LocalDoc> {
  return new Promise((resolve, reject) => {
    const lower = file.name.toLowerCase();

    const finish = (html: string) => {
      if (!html.trim()) {
        reject(new Error('文件内容为空'));
        return;
      }
      try {
        const doc = upsertDoc(
          { title: filenameToTitle(file.name), html },
          { reason: 'import', note: file.name },
        );
        resolve(doc);
      } catch (e) {
        reject(e instanceof Error ? e : new Error('导入写入失败'));
      }
    };

    // .docx：用 mammoth 解析为 HTML（需浏览器端 Buffer 垫片，见 vite.config.ts）
    if (lower.endsWith('.docx')) {
      const reader = new FileReader();
      reader.onerror = () => reject(new Error('文件读取失败'));
      reader.onload = async () => {
        try {
          const result = await mammoth.convertToHtml({ arrayBuffer: reader.result as ArrayBuffer });
          finish(result.value);
        } catch (e) {
          reject(e instanceof Error ? e : new Error('docx 解析失败'));
        }
      };
      reader.readAsArrayBuffer(file);
      return;
    }

    // .txt / .md / .html：按文本读取后转换
    const reader = new FileReader();
    reader.onerror = () => reject(new Error('文件读取失败'));
    reader.onload = () => {
      try {
        const raw = String(reader.result || '');
        let html: string;
        if (lower.endsWith('.html') || lower.endsWith('.htm')) {
          html = htmlToHtml(raw);
        } else if (lower.endsWith('.md') || lower.endsWith('.markdown')) {
          html = mdToHtml(raw);
        } else if (lower.endsWith('.txt')) {
          html = textToHtml(raw);
        } else {
          reject(new Error('暂不支持该格式，请使用 .txt / .md / .html / .docx'));
          return;
        }
        finish(html);
      } catch (e) {
        reject(e instanceof Error ? e : new Error('导入解析失败'));
      }
    };
    reader.readAsText(file);
  });
}
