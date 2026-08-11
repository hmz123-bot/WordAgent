import React, { useCallback, useRef, useEffect, useState } from 'react';
import { useEditor, EditorContent } from '@tiptap/react';
import StarterKit from '@tiptap/starter-kit';
import Placeholder from '@tiptap/extension-placeholder';
import Highlight from '@tiptap/extension-highlight';
import { Link } from '@tiptap/extension-link';
import { Underline } from '@tiptap/extension-underline';
import {
  TextBold,
  TextItalic,
  TextUnderline,
  BackgroundColor,
  H1,
  H2,
  H3,
  List,
  ListNumbers,
  Quote,
  Undo,
  Redo,
} from '@icon-park/react';
import { ParagraphId, getSelectedParaIds, getParagraphByParaId } from './extensions/paragraphIdExtension';

interface TiptapEditorProps {
  /** 初始文档内容（JSON） */
  initialContent?: Record<string, any>;
  /** 初始文档内容（HTML） */
  initialHtml?: string;
  /** 是否显示工具栏 */
  showToolbar?: boolean;
  /** 占位符文本 */
  placeholder?: string;
  /** 内容变化回调 */
  onContentChange?: (json: Record<string, any>, html: string) => void;
  /** 选区变化回调（带 paraId 信息） */
  onSelectionChange?: (paraIds: string[], range: { from: number; to: number }) => void;
  /** 编辑器就绪回调 */
  onEditorReady?: (editor: any) => void;
  /** 只读模式 */
  readonly?: boolean;
  /** ref to expose editor */
  editorRef?: React.MutableRefObject<any>;
}

const TiptapEditor: React.FC<TiptapEditorProps> = ({
  initialContent,
  initialHtml,
  showToolbar = true,
  placeholder = '开始输入文档内容...',
  onContentChange,
  onSelectionChange,
  onEditorReady,
  readonly = false,
  editorRef: externalRef,
}) => {
  const editor = useEditor({
    extensions: [
      StarterKit.configure({
        heading: { levels: [1, 2, 3] },
      }),
      Placeholder.configure({ placeholder }),
      Highlight,
      Link.configure({ openOnClick: false }),
      Underline,
      ParagraphId,
    ],
    content: initialContent || (initialHtml || ''),
    editable: !readonly,
    onUpdate: ({ editor }) => {
      const json = editor.getJSON();
      const html = editor.getHTML();
      onContentChange?.(json, html);
    },
    onSelectionUpdate: ({ editor }) => {
      const { from, to } = editor.state.selection;
      const paraIds = getSelectedParaIds(editor.state);
      onSelectionChange?.(paraIds, { from, to });
    },
    onCreate: ({ editor }) => {
      onEditorReady?.(editor);
      if (externalRef) externalRef.current = editor;
    },
  });

  // 暴露编辑器实例
  useEffect(() => {
    if (editor && externalRef) {
      externalRef.current = editor;
    }
  }, [editor, externalRef]);

  if (!editor) {
    return <div className="tiptap-loading">加载编辑器中...</div>;
  }

  return (
    <div className={`tiptap-container ${readonly ? 'tiptap-readonly' : ''}`}>
      {showToolbar && <EditorToolbar editor={editor} />}
      <EditorContent editor={editor} className="tiptap-content" />
    </div>
  );
};

/**
 * 编辑器工具栏
 */
const EditorToolbar: React.FC<{ editor: any }> = ({ editor }) => {
  const ToolBtn: React.FC<{
    onClick: () => void;
    active?: boolean;
    title: string;
    children: React.ReactNode;
  }> = ({ onClick, active, title, children }) => (
    <button
      type="button"
      className={`toolbar-btn ${active ? 'active' : ''}`}
      onClick={onClick}
      title={title}
    >
      {children}
    </button>
  );

  return (
    <div className="tiptap-toolbar">
      <div className="toolbar-group">
        <ToolBtn
          onClick={() => editor.chain().focus().toggleBold().run()}
          active={editor.isActive('bold')}
          title="加粗 (Ctrl+B)"
        >
          <TextBold theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleItalic().run()}
          active={editor.isActive('italic')}
          title="斜体 (Ctrl+I)"
        >
          <TextItalic theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleUnderline().run()}
          active={editor.isActive('underline')}
          title="下划线 (Ctrl+U)"
        >
          <TextUnderline theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleHighlight().run()}
          active={editor.isActive('highlight')}
          title="高亮"
        >
          <BackgroundColor theme="outline" size="17" />
        </ToolBtn>
      </div>

      <div className="toolbar-group">
        <ToolBtn
          onClick={() => editor.chain().focus().toggleHeading({ level: 1 }).run()}
          active={editor.isActive('heading', { level: 1 })}
          title="标题 1"
        >
          <H1 theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleHeading({ level: 2 }).run()}
          active={editor.isActive('heading', { level: 2 })}
          title="标题 2"
        >
          <H2 theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleHeading({ level: 3 }).run()}
          active={editor.isActive('heading', { level: 3 })}
          title="标题 3"
        >
          <H3 theme="outline" size="17" />
        </ToolBtn>
      </div>

      <div className="toolbar-group">
        <ToolBtn
          onClick={() => editor.chain().focus().toggleBulletList().run()}
          active={editor.isActive('bulletList')}
          title="无序列表"
        >
          <List theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleOrderedList().run()}
          active={editor.isActive('orderedList')}
          title="有序列表"
        >
          <ListNumbers theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn
          onClick={() => editor.chain().focus().toggleBlockquote().run()}
          active={editor.isActive('blockquote')}
          title="引用"
        >
          <Quote theme="outline" size="17" />
        </ToolBtn>
      </div>

      <div className="toolbar-group toolbar-right">
        <ToolBtn onClick={() => editor.chain().focus().undo().run()} title="撤销">
          <Undo theme="outline" size="17" />
        </ToolBtn>
        <ToolBtn onClick={() => editor.chain().focus().redo().run()} title="重做">
          <Redo theme="outline" size="17" />
        </ToolBtn>
      </div>
    </div>
  );
};

export default TiptapEditor;
