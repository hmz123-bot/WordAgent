package com.subtlesight.word.model;

import com.subtlesight.word.model.enums.NodeType;
import com.subtlesight.word.model.enums.SupportLevel;
import com.subtlesight.word.model.formatting.*;
import com.subtlesight.word.model.i18n.*;

import java.util.*;

/**
 * 文档节点 - 文档内容的通用树形表示。
 * <p>
 * 支持所有文档元素类型，包括段落、运行、表格、文本框、图片、
 * 方程、图表、超链接、字段、内容控件、修订等。
 * </p>
 */
public class DocumentNode {

    private String nodeId;
    private NodeType nodeType;
    private String text;
    private SupportLevel supportLevel;

    // ======== 子节点 ========
    private List<DocumentNode> children;

    // ======== 属性 ========
    private Map<String, Object> attributes;

    // ======== 样式引用 ========
    private String styleId;

    // ======== 段落格式 ========
    private ParagraphFormat paragraphFormat;

    // ======== 运行格式 ========
    private RunFormat runFormat;

    // ======== 表格格式 ========
    private TableFormat tableFormat;

    // ======== 文本框格式 ========
    private TextBoxFormat textBoxFormat;

    // ======== 字体槽（每个脚本） ========
    private List<FontSlot> fontSlots;

    // ======== RTL 配置 ========
    private Boolean rtl;
    private TextDirection textDirection;

    // ======== 语言标签（每个脚本槽） ========
    private LanguageTag languageTag;
    private LanguageTag eastAsianLanguageTag;
    private LanguageTag complexScriptLanguageTag;

    // ======== 修订/跟踪变更 ========
    private List<RevisionModel> revisions;

    // ======== 内容控件 (SDT) ========
    private ContentControl contentControl;

    // ======== 字段 ========
    private FieldModel field;

    // ======== 超链接 ========
    private WebEditingProjection.Hyperlink hyperlink;

    // ======== 书签 ========
    private WebEditingProjection.Bookmark bookmark;

    // ======== 注释引用 ========
    private String commentReference;

    // ======== 脚注引用 ========
    private String footnoteReference;

    // ======== 图片 ========
    private WebEditingProjection.ImageResource image;

    // ======== 方程 ========
    private WebEditingProjection.Equation equation;

    // ======== 图表 ========
    private WebEditingProjection.Chart chart;

    // ======== OLE 对象 ========
    private WebEditingProjection.OleObject oleObject;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    // ======== 锚点（稳定定位） ========
    private NodeAnchor anchor;

    public DocumentNode() {
        this.children = new ArrayList<>();
        this.attributes = new LinkedHashMap<>();
        this.fontSlots = new ArrayList<>();
        this.revisions = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // ======== Getters & Setters ========

    public String getNodeId() { return nodeId; }
    public void setNodeId(String nodeId) { this.nodeId = nodeId; }

    public NodeType getNodeType() { return nodeType; }
    public void setNodeType(NodeType nodeType) { this.nodeType = nodeType; }

    public String getText() { return text; }
    public void setText(String text) { this.text = text; }

    public SupportLevel getSupportLevel() { return supportLevel; }
    public void setSupportLevel(SupportLevel supportLevel) { this.supportLevel = supportLevel; }

    public List<DocumentNode> getChildren() { return children; }
    public void setChildren(List<DocumentNode> children) { this.children = children; }

    public Map<String, Object> getAttributes() { return attributes; }
    public void setAttributes(Map<String, Object> attributes) { this.attributes = attributes; }

    public String getStyleId() { return styleId; }
    public void setStyleId(String styleId) { this.styleId = styleId; }

    public ParagraphFormat getParagraphFormat() { return paragraphFormat; }
    public void setParagraphFormat(ParagraphFormat paragraphFormat) { this.paragraphFormat = paragraphFormat; }

    public RunFormat getRunFormat() { return runFormat; }
    public void setRunFormat(RunFormat runFormat) { this.runFormat = runFormat; }

    public TableFormat getTableFormat() { return tableFormat; }
    public void setTableFormat(TableFormat tableFormat) { this.tableFormat = tableFormat; }

    public TextBoxFormat getTextBoxFormat() { return textBoxFormat; }
    public void setTextBoxFormat(TextBoxFormat textBoxFormat) { this.textBoxFormat = textBoxFormat; }

    public List<FontSlot> getFontSlots() { return fontSlots; }
    public void setFontSlots(List<FontSlot> fontSlots) { this.fontSlots = fontSlots; }

    public Boolean getRtl() { return rtl; }
    public void setRtl(Boolean rtl) { this.rtl = rtl; }

    public TextDirection getTextDirection() { return textDirection; }
    public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }

    public LanguageTag getLanguageTag() { return languageTag; }
    public void setLanguageTag(LanguageTag languageTag) { this.languageTag = languageTag; }

    public LanguageTag getEastAsianLanguageTag() { return eastAsianLanguageTag; }
    public void setEastAsianLanguageTag(LanguageTag eastAsianLanguageTag) { this.eastAsianLanguageTag = eastAsianLanguageTag; }

    public LanguageTag getComplexScriptLanguageTag() { return complexScriptLanguageTag; }
    public void setComplexScriptLanguageTag(LanguageTag complexScriptLanguageTag) { this.complexScriptLanguageTag = complexScriptLanguageTag; }

    public List<RevisionModel> getRevisions() { return revisions; }
    public void setRevisions(List<RevisionModel> revisions) { this.revisions = revisions; }

    public ContentControl getContentControl() { return contentControl; }
    public void setContentControl(ContentControl contentControl) { this.contentControl = contentControl; }

    public FieldModel getField() { return field; }
    public void setField(FieldModel field) { this.field = field; }

    public WebEditingProjection.Hyperlink getHyperlink() { return hyperlink; }
    public void setHyperlink(WebEditingProjection.Hyperlink hyperlink) { this.hyperlink = hyperlink; }

    public WebEditingProjection.Bookmark getBookmark() { return bookmark; }
    public void setBookmark(WebEditingProjection.Bookmark bookmark) { this.bookmark = bookmark; }

    public String getCommentReference() { return commentReference; }
    public void setCommentReference(String commentReference) { this.commentReference = commentReference; }

    public String getFootnoteReference() { return footnoteReference; }
    public void setFootnoteReference(String footnoteReference) { this.footnoteReference = footnoteReference; }

    public WebEditingProjection.ImageResource getImage() { return image; }
    public void setImage(WebEditingProjection.ImageResource image) { this.image = image; }

    public WebEditingProjection.Equation getEquation() { return equation; }
    public void setEquation(WebEditingProjection.Equation equation) { this.equation = equation; }

    public WebEditingProjection.Chart getChart() { return chart; }
    public void setChart(WebEditingProjection.Chart chart) { this.chart = chart; }

    public WebEditingProjection.OleObject getOleObject() { return oleObject; }
    public void setOleObject(WebEditingProjection.OleObject oleObject) { this.oleObject = oleObject; }

    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    public NodeAnchor getAnchor() { return anchor; }
    public void setAnchor(NodeAnchor anchor) { this.anchor = anchor; }

    // ======== 便捷方法 ========

    /**
     * 添加子节点。
     */
    public void addChild(DocumentNode child) {
        if (child != null) {
            this.children.add(child);
        }
    }

    /**
     * 添加修订。
     */
    public void addRevision(RevisionModel revision) {
        if (revision != null) {
            this.revisions.add(revision);
        }
    }

    /**
     * 添加字体槽。
     */
    public void addFontSlot(FontSlot slot) {
        if (slot != null) {
            this.fontSlots.add(slot);
        }
    }

    /**
     * 获取指定脚本类型的字体槽。
     */
    public FontSlot getFontSlot(ScriptType scriptType) {
        if (scriptType == null || scriptType == ScriptType.ALL) {
            return fontSlots.isEmpty() ? null : fontSlots.get(0);
        }
        for (FontSlot slot : fontSlots) {
            if (slot.getScriptType() == scriptType) {
                return slot;
            }
        }
        return null;
    }

    /**
     * 判断是否为 RTL 段落/运行。
     */
    public boolean isRtl() {
        return Boolean.TRUE.equals(rtl)
                || (textDirection != null && textDirection.isRtl())
                || (languageTag != null && languageTag.isRtl())
                || (complexScriptLanguageTag != null && complexScriptLanguageTag.isRtl());
    }

    /**
     * 返回直接子节点的 ID 列表（顺序与 getChildren() 一致）。
     */
    public List<String> getChildrenIds() {
        List<String> ids = new ArrayList<>();
        if (children != null) {
            for (DocumentNode child : children) {
                ids.add(child.getNodeId());
            }
        }
        return ids;
    }

    /**
     * 返回节点文本（聚合所有后代文本）。若节点自身 text 非空则直接返回，
     * 否则递归拼接子节点文本，便于表格单元等容器节点获取纯文本内容。
     */
    public String getTextContent() {
        if (text != null && !text.isEmpty()) {
            return text;
        }
        StringBuilder sb = new StringBuilder();
        if (children != null) {
            for (DocumentNode child : children) {
                String tc = child.getTextContent();
                if (tc != null && !tc.isEmpty()) {
                    sb.append(tc);
                }
            }
        }
        return sb.toString();
    }
}