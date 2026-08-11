import { Extension } from '@tiptap/core';
import { Plugin, PluginKey } from '@tiptap/pm/state';

/**
 * Paragraph ID Extension — 为每个段落附加持久化 paraId。
 *
 * 类似 w14:paraId 语义，paraId 在文档生命周期内不变。
 * Agent 多轮 tool call 之间用同一个 paraId 定位段落。
 *
 * 存储方式：段落节点的 DOM 上挂 data-paraid 属性。
 * 每次文档加载 / 新增段落时自动分配唯一 ID。
 */

export interface ParaIdState {
  paraIdMap: Map<string, string>; // editor's internal pos → paraId
}

const paraIdPluginKey = new PluginKey<ParaIdState>('paraId');

let idCounter = 0;

function generateParaId(): string {
  return `p_${++idCounter}_${Date.now().toString(36)}`;
}

export const ParagraphId = Extension.create({
  name: 'paragraphId',

  addOptions() {
    return {
      HTMLAttributes: {},
    };
  },

  addGlobalAttributes() {
    return [
      {
        types: ['paragraph', 'heading', 'blockquote'],
        attributes: {
          paraId: {
            default: null,
            parseHTML: (element) => element.getAttribute('data-paraid') || generateParaId(),
            renderHTML: (attributes) => {
              if (!attributes.paraId) return {};
              return { 'data-paraid': attributes.paraId as string };
            },
          },
        },
      },
    ];
  },

  addProseMirrorPlugins() {
    const extension = this;

    return [
      new Plugin<ParaIdState>({
        key: paraIdPluginKey,
        state: {
          init(): ParaIdState {
            return {
              paraIdMap: new Map(),
            };
          },
          apply(tr, prev): ParaIdState {
            const newMap = new Map(prev.paraIdMap);

            // 追踪新增/变更的段落
            tr.doc.descendants((node, pos) => {
              if (node.isBlock && node.attrs.paraId) {
                newMap.set(String(pos), node.attrs.paraId as string);
              }
              return true;
            });

            return { paraIdMap: newMap };
          },
        },

        appendTransaction(transactions, oldState, newState) {
          // 给没有 paraId 的段落自动分配
          const tr = newState.tr;
          let modified = false;

          newState.doc.descendants((node, pos) => {
            if (node.isBlock && !node.attrs.paraId) {
              tr.setNodeAttribute(pos, 'paraId', generateParaId());
              modified = true;
            }
            return true;
          });

          return modified ? tr : null;
        },
      }),
    ];
  },
});

// 辅助：获取当前选区所在段落的所有 paraId
export function getSelectedParaIds(state: any): string[] {
  const { from, to } = state.selection;
  const ids: string[] = [];

  state.doc.nodesBetween(from, to, (node: any, pos: number) => {
    if (node.isBlock && node.attrs.paraId) {
      if (!ids.includes(node.attrs.paraId as string)) {
        ids.push(node.attrs.paraId as string);
      }
    }
    return true;
  });

  return ids;
}

// 辅助：根据 paraId 查找段落内容
export function getParagraphByParaId(doc: any, paraId: string): { pos: number; node: any } | null {
  let result: { pos: number; node: any } | null = null;
  doc.descendants((node: any, pos: number) => {
    if (node.attrs?.paraId === paraId && !result) {
      result = { pos, node };
    }
    return !result;
  });
  return result;
}
