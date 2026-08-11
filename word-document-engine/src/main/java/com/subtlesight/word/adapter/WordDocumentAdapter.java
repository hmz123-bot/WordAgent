package com.subtlesight.word.adapter;

import com.subtlesight.word.model.*;
import com.subtlesight.word.model.enums.SupportLevel;

import java.util.*;

/**
 * Word 文档适配器接口 - 定义文档读取、创建、修改和分析的标准 API。
 * <p>
 * 支持：i18n（每个脚本字槽、BCP-47 语言标签、复杂文字加粗/斜体/大小）、
 * RTL（自动启用、段落/运行/表格/样式/页眉/页脚/文档默认）、
 * 段落（framePr、制表表速记、基于字符的缩进）、
 * 运行（下划线.color、位置半点）、表格（虚拟列操作、hMerge）、
 * 样式、文本框/形状、页眉/页脚、图片、方程、图表、注释、脚注、
 * 水印、书签、目录、超链接、章节、表单字段、SDT、字段、
 * OLE 对象、修订/跟踪变更、页面背景色、文档属性。
 * </p>
 */
public interface WordDocumentAdapter {

    // ========================================================================
    // 读取
    // ========================================================================

    /**
     * 读取 .docx 字节数组为 Web 编辑投影。
     */
    WebEditingProjection read(byte[] docxData);

    // ========================================================================
    // 创建
    // ========================================================================

    /**
     * 从 Web 编辑投影创建 .docx 字节数组。
     */
    byte[] create(WebEditingProjection projection);

    // ========================================================================
    // 修改
    // ========================================================================

    /**
     * 应用变更集到现有文档。
     */
    byte[] applyChanges(byte[] docxData, DocumentChangeSet changes);

    // ========================================================================
    // 分析
    // ========================================================================

    /**
     * 分析文档并返回统计信息。
     */
    DocumentStats analyze(byte[] docxData);

    // ========================================================================
    // 转换
    // ========================================================================

    /**
     * 转换文档格式（如 .doc → .docx）。
     */
    byte[] convert(byte[] data, String targetFormat);

    // ========================================================================
    // 验证
    // ========================================================================

    /**
     * 验证文档结构的完整性。
     */
    ConversionReport validate(byte[] docxData);

    // ========================================================================
    // 文档创建
    // ========================================================================

    /**
     * 创建空白文档。
     */
    byte[] createDocument(String templateName, Map<String, Object> options);

    // ========================================================================
    // 合并
    // ========================================================================

    /**
     * 合并多个文档。
     */
    byte[] mergeDocuments(List<byte[]> documents);

    // ========================================================================
    // 内部类型
    // ========================================================================

    /**
     * 文档统计信息。
     */
    class DocumentStats {
        private int paragraphCount;
        private int tableCount;
        private int imageCount;
        private int sectionCount;
        private int commentCount;
        private int footnoteCount;
        private int endnoteCount;
        private int bookmarkCount;
        private int fieldCount;
        private int contentControlCount;
        private int revisionCount;
        private int chartCount;
        private int equationCount;
        private int oleObjectCount;
        private int textBoxCount;
        private int headerCount;
        private int footerCount;
        private int wordCount;
        private int characterCount;
        private boolean hasRtl;
        private boolean hasComplexScript;
        private boolean hasCjk;
        private boolean hasTrackChanges;
        private boolean hasForms;
        private boolean hasMacros;
        private Map<String, Object> additionalMetrics;

        public DocumentStats() {
            this.additionalMetrics = new LinkedHashMap<>();
        }

        public int getParagraphCount() { return paragraphCount; }
        public void setParagraphCount(int paragraphCount) { this.paragraphCount = paragraphCount; }
        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }
        public int getImageCount() { return imageCount; }
        public void setImageCount(int imageCount) { this.imageCount = imageCount; }
        public int getSectionCount() { return sectionCount; }
        public void setSectionCount(int sectionCount) { this.sectionCount = sectionCount; }
        public int getCommentCount() { return commentCount; }
        public void setCommentCount(int commentCount) { this.commentCount = commentCount; }
        public int getFootnoteCount() { return footnoteCount; }
        public void setFootnoteCount(int footnoteCount) { this.footnoteCount = footnoteCount; }
        public int getEndnoteCount() { return endnoteCount; }
        public void setEndnoteCount(int endnoteCount) { this.endnoteCount = endnoteCount; }
        public int getBookmarkCount() { return bookmarkCount; }
        public void setBookmarkCount(int bookmarkCount) { this.bookmarkCount = bookmarkCount; }
        public int getFieldCount() { return fieldCount; }
        public void setFieldCount(int fieldCount) { this.fieldCount = fieldCount; }
        public int getContentControlCount() { return contentControlCount; }
        public void setContentControlCount(int contentControlCount) { this.contentControlCount = contentControlCount; }
        public int getRevisionCount() { return revisionCount; }
        public void setRevisionCount(int revisionCount) { this.revisionCount = revisionCount; }
        public int getChartCount() { return chartCount; }
        public void setChartCount(int chartCount) { this.chartCount = chartCount; }
        public int getEquationCount() { return equationCount; }
        public void setEquationCount(int equationCount) { this.equationCount = equationCount; }
        public int getOleObjectCount() { return oleObjectCount; }
        public void setOleObjectCount(int oleObjectCount) { this.oleObjectCount = oleObjectCount; }
        public int getTextBoxCount() { return textBoxCount; }
        public void setTextBoxCount(int textBoxCount) { this.textBoxCount = textBoxCount; }
        public int getHeaderCount() { return headerCount; }
        public void setHeaderCount(int headerCount) { this.headerCount = headerCount; }
        public int getFooterCount() { return footerCount; }
        public void setFooterCount(int footerCount) { this.footerCount = footerCount; }
        public int getWordCount() { return wordCount; }
        public void setWordCount(int wordCount) { this.wordCount = wordCount; }
        public int getCharacterCount() { return characterCount; }
        public void setCharacterCount(int characterCount) { this.characterCount = characterCount; }
        public boolean isHasRtl() { return hasRtl; }
        public void setHasRtl(boolean hasRtl) { this.hasRtl = hasRtl; }
        public boolean isHasComplexScript() { return hasComplexScript; }
        public void setHasComplexScript(boolean hasComplexScript) { this.hasComplexScript = hasComplexScript; }
        public boolean isHasCjk() { return hasCjk; }
        public void setHasCjk(boolean hasCjk) { this.hasCjk = hasCjk; }
        public boolean isHasTrackChanges() { return hasTrackChanges; }
        public void setHasTrackChanges(boolean hasTrackChanges) { this.hasTrackChanges = hasTrackChanges; }
        public boolean isHasForms() { return hasForms; }
        public void setHasForms(boolean hasForms) { this.hasForms = hasForms; }
        public boolean isHasMacros() { return hasMacros; }
        public void setHasMacros(boolean hasMacros) { this.hasMacros = hasMacros; }
        public Map<String, Object> getAdditionalMetrics() { return additionalMetrics; }
        public void setAdditionalMetrics(Map<String, Object> additionalMetrics) { this.additionalMetrics = additionalMetrics; }
    }

    /**
     * 解析结果。
     */
    class ParseResult {
        private List<DocumentNode> nodes;
        private Map<String, Object> metadata;
        private List<String> warnings;
        private boolean success;

        public ParseResult() {
            this.nodes = new ArrayList<>();
            this.metadata = new LinkedHashMap<>();
            this.warnings = new ArrayList<>();
            this.success = false;
        }

        public List<DocumentNode> getNodes() { return nodes; }
        public void setNodes(List<DocumentNode> nodes) { this.nodes = nodes; }
        public Map<String, Object> getMetadata() { return metadata; }
        public void setMetadata(Map<String, Object> metadata) { this.metadata = metadata; }
        public List<String> getWarnings() { return warnings; }
        public void setWarnings(List<String> warnings) { this.warnings = warnings; }
        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
    }
}