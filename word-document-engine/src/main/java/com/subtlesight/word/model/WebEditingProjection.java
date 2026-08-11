package com.subtlesight.word.model;

import com.subtlesight.word.model.formatting.*;
import com.subtlesight.word.model.i18n.*;

import java.util.*;

/**
 * Web 编辑投影 - 面向 Web 编辑器的扁平化文档视图。
 * <p>
 * 将层级化的 OOXML 文档结构转换为适合前端编辑的扁平结构，
 * 同时保留完整的 i18n/RTL 和高级排版信息。
 * </p>
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
public class WebEditingProjection {

    // ======== 文档元数据 ========
    private String documentId;
    private String title;
    private String htmlContent = "";
    private Map<String, String> metadata;
    private String version;

    // ======== 文档属性（含 i18n/RTL） ========
    private DocumentProperties documentProperties;

    // ======== RTL 配置 ========
    private RtlConfiguration rtlConfig;

    // ======== 页码配置 ========
    private PageNumberingConfig pageNumberingConfig;

    // ======== 文档默认格式 ========
    private DocumentProperties.DocumentDefaults documentDefaults;

    // ======== 样式集合 ========
    private List<StyleDefinition> styles;

    // ======== 文档内容节点 ========
    private List<DocumentNode> content;

    // ======== 页眉/页脚 ========
    private List<HeaderFooter> headers;
    private List<HeaderFooter> footers;

    // ======== 注释 ========
    private List<Comment> comments;

    // ======== 脚注/尾注 ========
    private List<Footnote> footnotes;
    private List<Footnote> endnotes;

    // ======== 水印 ========
    private List<Watermark> watermarks;

    // ======== 书签 ========
    private List<Bookmark> bookmarks;

    // ======== 目录 ========
    private List<TableOfContents> tableOfContents;

    // ======== 超链接 ========
    private List<Hyperlink> hyperlinks;

    // ======== 章节 ========
    private List<Section> sections;

    // ======== 表单字段 ========
    private List<FormField> formFields;

    // ======== 内容控件 (SDT) ========
    private List<ContentControl> contentControls;

    // ======== 字段代码 ========
    private List<FieldModel> fields;

    // ======== OLE 对象 ========
    private List<OleObject> oleObjects;

    // ======== 修订/跟踪变更 ========
    private List<RevisionModel> revisions;

    // ======== 图片资源 ========
    private List<ImageResource> images;

    // ======== 方程 ========
    private List<Equation> equations;

    // ======== 图表 ========
    private List<Chart> charts;

    // ======== 文本框/形状 ========
    private List<TextBoxFormat> textBoxes;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public WebEditingProjection() {
        this.metadata = new LinkedHashMap<>();
        this.styles = new ArrayList<>();
        this.content = new ArrayList<>();
        this.headers = new ArrayList<>();
        this.footers = new ArrayList<>();
        this.comments = new ArrayList<>();
        this.footnotes = new ArrayList<>();
        this.endnotes = new ArrayList<>();
        this.watermarks = new ArrayList<>();
        this.bookmarks = new ArrayList<>();
        this.tableOfContents = new ArrayList<>();
        this.hyperlinks = new ArrayList<>();
        this.sections = new ArrayList<>();
        this.formFields = new ArrayList<>();
        this.contentControls = new ArrayList<>();
        this.fields = new ArrayList<>();
        this.oleObjects = new ArrayList<>();
        this.revisions = new ArrayList<>();
        this.images = new ArrayList<>();
        this.equations = new ArrayList<>();
        this.charts = new ArrayList<>();
        this.textBoxes = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // ======== Getters & Setters ========

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }

    public String getHtmlContent() { return htmlContent; }
    public void setHtmlContent(String htmlContent) { this.htmlContent = htmlContent; }

    public Map<String, String> getMetadata() { return metadata; }
    public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }

    public String getVersion() { return version; }
    public void setVersion(String version) { this.version = version; }

    public DocumentProperties getDocumentProperties() { return documentProperties; }
    public void setDocumentProperties(DocumentProperties documentProperties) { this.documentProperties = documentProperties; }

    public RtlConfiguration getRtlConfig() { return rtlConfig; }
    public void setRtlConfig(RtlConfiguration rtlConfig) { this.rtlConfig = rtlConfig; }

    public PageNumberingConfig getPageNumberingConfig() { return pageNumberingConfig; }
    public void setPageNumberingConfig(PageNumberingConfig pageNumberingConfig) { this.pageNumberingConfig = pageNumberingConfig; }

    public DocumentProperties.DocumentDefaults getDocumentDefaults() { return documentDefaults; }
    public void setDocumentDefaults(DocumentProperties.DocumentDefaults documentDefaults) { this.documentDefaults = documentDefaults; }

    public List<StyleDefinition> getStyles() { return styles; }
    public void setStyles(List<StyleDefinition> styles) { this.styles = styles; }

    public List<DocumentNode> getContent() { return content; }
    public void setContent(List<DocumentNode> content) { this.content = content; }

    public List<HeaderFooter> getHeaders() { return headers; }
    public void setHeaders(List<HeaderFooter> headers) { this.headers = headers; }

    public List<HeaderFooter> getFooters() { return footers; }
    public void setFooters(List<HeaderFooter> footers) { this.footers = footers; }

    public List<Comment> getComments() { return comments; }
    public void setComments(List<Comment> comments) { this.comments = comments; }

    public List<Footnote> getFootnotes() { return footnotes; }
    public void setFootnotes(List<Footnote> footnotes) { this.footnotes = footnotes; }

    public List<Footnote> getEndnotes() { return endnotes; }
    public void setEndnotes(List<Footnote> endnotes) { this.endnotes = endnotes; }

    public List<Watermark> getWatermarks() { return watermarks; }
    public void setWatermarks(List<Watermark> watermarks) { this.watermarks = watermarks; }

    public List<Bookmark> getBookmarks() { return bookmarks; }
    public void setBookmarks(List<Bookmark> bookmarks) { this.bookmarks = bookmarks; }

    public List<TableOfContents> getTableOfContents() { return tableOfContents; }
    public void setTableOfContents(List<TableOfContents> tableOfContents) { this.tableOfContents = tableOfContents; }

    public List<Hyperlink> getHyperlinks() { return hyperlinks; }
    public void setHyperlinks(List<Hyperlink> hyperlinks) { this.hyperlinks = hyperlinks; }

    public List<Section> getSections() { return sections; }
    public void setSections(List<Section> sections) { this.sections = sections; }

    public List<FormField> getFormFields() { return formFields; }
    public void setFormFields(List<FormField> formFields) { this.formFields = formFields; }

    public List<ContentControl> getContentControls() { return contentControls; }
    public void setContentControls(List<ContentControl> contentControls) { this.contentControls = contentControls; }

    public List<FieldModel> getFields() { return fields; }
    public void setFields(List<FieldModel> fields) { this.fields = fields; }

    public List<OleObject> getOleObjects() { return oleObjects; }
    public void setOleObjects(List<OleObject> oleObjects) { this.oleObjects = oleObjects; }

    public List<RevisionModel> getRevisions() { return revisions; }
    public void setRevisions(List<RevisionModel> revisions) { this.revisions = revisions; }

    public List<ImageResource> getImages() { return images; }
    public void setImages(List<ImageResource> images) { this.images = images; }

    public List<Equation> getEquations() { return equations; }
    public void setEquations(List<Equation> equations) { this.equations = equations; }

    public List<Chart> getCharts() { return charts; }
    public void setCharts(List<Chart> charts) { this.charts = charts; }

    public List<TextBoxFormat> getTextBoxes() { return textBoxes; }
    public void setTextBoxes(List<TextBoxFormat> textBoxes) { this.textBoxes = textBoxes; }

    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    // ========================================================================
    //  内部类型
    // ========================================================================

    /**
     * 页眉/页脚。
     */
    public static class HeaderFooter {
        private String id;
        private String type;           // "default", "first", "even"
        private List<DocumentNode> content;
        private Boolean rtl;
        private TextDirection textDirection;
        private LanguageTag languageTag;
        private Boolean linkedToPrevious;

        public HeaderFooter() { this.content = new ArrayList<>(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<DocumentNode> getContent() { return content; }
        public void setContent(List<DocumentNode> content) { this.content = content; }
        public Boolean getRtl() { return rtl; }
        public void setRtl(Boolean rtl) { this.rtl = rtl; }
        public TextDirection getTextDirection() { return textDirection; }
        public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }
        public LanguageTag getLanguageTag() { return languageTag; }
        public void setLanguageTag(LanguageTag languageTag) { this.languageTag = languageTag; }
        public Boolean getLinkedToPrevious() { return linkedToPrevious; }
        public void setLinkedToPrevious(Boolean linkedToPrevious) { this.linkedToPrevious = linkedToPrevious; }
    }

    /**
     * 注释。
     */
    public static class Comment {
        private String id;
        private String author;
        private Date date;
        private String text;
        private String initials;
        private String resolvedBy;
        private Boolean resolved;
        private String parentCommentId;
        private List<DocumentNode> content;

        public Comment() { this.content = new ArrayList<>(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public Date getDate() { return date; }
        public void setDate(Date date) { this.date = date; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getInitials() { return initials; }
        public void setInitials(String initials) { this.initials = initials; }
        public String getResolvedBy() { return resolvedBy; }
        public void setResolvedBy(String resolvedBy) { this.resolvedBy = resolvedBy; }
        public Boolean getResolved() { return resolved; }
        public void setResolved(Boolean resolved) { this.resolved = resolved; }
        public String getParentCommentId() { return parentCommentId; }
        public void setParentCommentId(String parentCommentId) { this.parentCommentId = parentCommentId; }
        public List<DocumentNode> getContent() { return content; }
        public void setContent(List<DocumentNode> content) { this.content = content; }
    }

    /**
     * 脚注/尾注。
     */
    public static class Footnote {
        private String id;
        private String type;          // "footnote" 或 "endnote"
        private List<DocumentNode> content;

        public Footnote() { this.content = new ArrayList<>(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public List<DocumentNode> getContent() { return content; }
        public void setContent(List<DocumentNode> content) { this.content = content; }
    }

    /**
     * 水印。
     */
    public static class Watermark {
        private String id;
        private WatermarkType type;
        private String text;
        private String imageDataId;
        private Double rotation;
        private Double opacity;
        private String color;
        private String fontName;
        private Double fontSize;
        private Boolean semitransparent;

        public enum WatermarkType { TEXT, IMAGE, NONE }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public WatermarkType getType() { return type; }
        public void setType(WatermarkType type) { this.type = type; }
        public String getText() { return text; }
        public void setText(String text) { this.text = text; }
        public String getImageDataId() { return imageDataId; }
        public void setImageDataId(String imageDataId) { this.imageDataId = imageDataId; }
        public Double getRotation() { return rotation; }
        public void setRotation(Double rotation) { this.rotation = rotation; }
        public Double getOpacity() { return opacity; }
        public void setOpacity(Double opacity) { this.opacity = opacity; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getFontName() { return fontName; }
        public void setFontName(String fontName) { this.fontName = fontName; }
        public Double getFontSize() { return fontSize; }
        public void setFontSize(Double fontSize) { this.fontSize = fontSize; }
        public Boolean getSemitransparent() { return semitransparent; }
        public void setSemitransparent(Boolean semitransparent) { this.semitransparent = semitransparent; }
    }

    /**
     * 书签。
     */
    public static class Bookmark {
        private String id;
        private String name;
        private String startNodeId;
        private String endNodeId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getStartNodeId() { return startNodeId; }
        public void setStartNodeId(String startNodeId) { this.startNodeId = startNodeId; }
        public String getEndNodeId() { return endNodeId; }
        public void setEndNodeId(String endNodeId) { this.endNodeId = endNodeId; }
    }

    /**
     * 目录。
     */
    public static class TableOfContents {
        private String id;
        private String headingStyle;     // "1-3", "1-4" 等
        private String tabLeader;        // "dot", "hyphen", "underscore", "none"
        private boolean includePageNumbers;
        private boolean rightAlignPageNumbers;
        private boolean hyperlinkEntries;
        private String fieldCode;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getHeadingStyle() { return headingStyle; }
        public void setHeadingStyle(String headingStyle) { this.headingStyle = headingStyle; }
        public String getTabLeader() { return tabLeader; }
        public void setTabLeader(String tabLeader) { this.tabLeader = tabLeader; }
        public boolean isIncludePageNumbers() { return includePageNumbers; }
        public void setIncludePageNumbers(boolean includePageNumbers) { this.includePageNumbers = includePageNumbers; }
        public boolean isRightAlignPageNumbers() { return rightAlignPageNumbers; }
        public void setRightAlignPageNumbers(boolean rightAlignPageNumbers) { this.rightAlignPageNumbers = rightAlignPageNumbers; }
        public boolean isHyperlinkEntries() { return hyperlinkEntries; }
        public void setHyperlinkEntries(boolean hyperlinkEntries) { this.hyperlinkEntries = hyperlinkEntries; }
        public String getFieldCode() { return fieldCode; }
        public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    }

    /**
     * 超链接。
     */
    public static class Hyperlink {
        private String id;
        private String url;
        private String anchor;           // 书签锚点
        private String tooltip;
        private String target;           // "_blank", "_top", "_self"
        private boolean isExternal;
        private String displayText;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getAnchor() { return anchor; }
        public void setAnchor(String anchor) { this.anchor = anchor; }
        public String getTooltip() { return tooltip; }
        public void setTooltip(String tooltip) { this.tooltip = tooltip; }
        public String getTarget() { return target; }
        public void setTarget(String target) { this.target = target; }
        public boolean isExternal() { return isExternal; }
        public void setExternal(boolean external) { isExternal = external; }
        public String getDisplayText() { return displayText; }
        public void setDisplayText(String displayText) { this.displayText = displayText; }
    }

    /**
     * 章节。
     */
    public static class Section {
        private String id;
        private String type;                // "continuous", "nextPage", "oddPage", "evenPage", "newColumn"
        private Double pageWidth;
        private Double pageHeight;
        private Double marginTop;
        private Double marginBottom;
        private Double marginLeft;
        private Double marginRight;
        private Double gutterWidth;
        private String gutterPosition;      // "left", "right", "top"
        private Boolean rtlGutter;
        private String headerReference;
        private String footerReference;
        private String firstHeaderReference;
        private String firstFooterReference;
        private String evenHeaderReference;
        private String evenFooterReference;
        private Boolean titlePage;
        private TextDirection textDirection;
        private PageNumberingConfig pageNumbering;
        private Boolean rtl;
        private String pageBorderDisplay;   // "allPages", "firstPage", "notFirstPage"
        private Map<String, Object> additionalProperties;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
        public Double getPageWidth() { return pageWidth; }
        public void setPageWidth(Double pageWidth) { this.pageWidth = pageWidth; }
        public Double getPageHeight() { return pageHeight; }
        public void setPageHeight(Double pageHeight) { this.pageHeight = pageHeight; }
        public Double getMarginTop() { return marginTop; }
        public void setMarginTop(Double marginTop) { this.marginTop = marginTop; }
        public Double getMarginBottom() { return marginBottom; }
        public void setMarginBottom(Double marginBottom) { this.marginBottom = marginBottom; }
        public Double getMarginLeft() { return marginLeft; }
        public void setMarginLeft(Double marginLeft) { this.marginLeft = marginLeft; }
        public Double getMarginRight() { return marginRight; }
        public void setMarginRight(Double marginRight) { this.marginRight = marginRight; }
        public Double getGutterWidth() { return gutterWidth; }
        public void setGutterWidth(Double gutterWidth) { this.gutterWidth = gutterWidth; }
        public String getGutterPosition() { return gutterPosition; }
        public void setGutterPosition(String gutterPosition) { this.gutterPosition = gutterPosition; }
        public Boolean getRtlGutter() { return rtlGutter; }
        public void setRtlGutter(Boolean rtlGutter) { this.rtlGutter = rtlGutter; }
        public String getHeaderReference() { return headerReference; }
        public void setHeaderReference(String headerReference) { this.headerReference = headerReference; }
        public String getFooterReference() { return footerReference; }
        public void setFooterReference(String footerReference) { this.footerReference = footerReference; }
        public String getFirstHeaderReference() { return firstHeaderReference; }
        public void setFirstHeaderReference(String firstHeaderReference) { this.firstHeaderReference = firstHeaderReference; }
        public String getFirstFooterReference() { return firstFooterReference; }
        public void setFirstFooterReference(String firstFooterReference) { this.firstFooterReference = firstFooterReference; }
        public String getEvenHeaderReference() { return evenHeaderReference; }
        public void setEvenHeaderReference(String evenHeaderReference) { this.evenHeaderReference = evenHeaderReference; }
        public String getEvenFooterReference() { return evenFooterReference; }
        public void setEvenFooterReference(String evenFooterReference) { this.evenFooterReference = evenFooterReference; }
        public Boolean getTitlePage() { return titlePage; }
        public void setTitlePage(Boolean titlePage) { this.titlePage = titlePage; }
        public TextDirection getTextDirection() { return textDirection; }
        public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }
        public PageNumberingConfig getPageNumbering() { return pageNumbering; }
        public void setPageNumbering(PageNumberingConfig pageNumbering) { this.pageNumbering = pageNumbering; }
        public Boolean getRtl() { return rtl; }
        public void setRtl(Boolean rtl) { this.rtl = rtl; }
        public String getPageBorderDisplay() { return pageBorderDisplay; }
        public void setPageBorderDisplay(String pageBorderDisplay) { this.pageBorderDisplay = pageBorderDisplay; }
        public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
        public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }
    }

    /**
     * 表单字段。
     */
    public static class FormField {
        private String id;
        private String name;
        private FormFieldType type;
        private String defaultValue;
        private String helpText;
        private String statusBarText;
        private boolean enabled;
        private boolean entryMacro;
        private boolean exitMacro;
        private boolean bookmarkExists;
        private boolean calculated;

        // 文本字段
        private Integer maxLength;
        private String textFormat;       // "regular", "number", "date", "currentDate", "currentTime", "calculated"

        // 复选框字段
        private Boolean defaultChecked;
        private Double checkBoxSize;

        // 下拉字段
        private List<String> dropdownItems;

        public FormField() { this.dropdownItems = new ArrayList<>(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public FormFieldType getType() { return type; }
        public void setType(FormFieldType type) { this.type = type; }
        public String getDefaultValue() { return defaultValue; }
        public void setDefaultValue(String defaultValue) { this.defaultValue = defaultValue; }
        public String getHelpText() { return helpText; }
        public void setHelpText(String helpText) { this.helpText = helpText; }
        public String getStatusBarText() { return statusBarText; }
        public void setStatusBarText(String statusBarText) { this.statusBarText = statusBarText; }
        public boolean isEnabled() { return enabled; }
        public void setEnabled(boolean enabled) { this.enabled = enabled; }
        public boolean isEntryMacro() { return entryMacro; }
        public void setEntryMacro(boolean entryMacro) { this.entryMacro = entryMacro; }
        public boolean isExitMacro() { return exitMacro; }
        public void setExitMacro(boolean exitMacro) { this.exitMacro = exitMacro; }
        public boolean isBookmarkExists() { return bookmarkExists; }
        public void setBookmarkExists(boolean bookmarkExists) { this.bookmarkExists = bookmarkExists; }
        public boolean isCalculated() { return calculated; }
        public void setCalculated(boolean calculated) { this.calculated = calculated; }
        public Integer getMaxLength() { return maxLength; }
        public void setMaxLength(Integer maxLength) { this.maxLength = maxLength; }
        public String getTextFormat() { return textFormat; }
        public void setTextFormat(String textFormat) { this.textFormat = textFormat; }
        public Boolean getDefaultChecked() { return defaultChecked; }
        public void setDefaultChecked(Boolean defaultChecked) { this.defaultChecked = defaultChecked; }
        public Double getCheckBoxSize() { return checkBoxSize; }
        public void setCheckBoxSize(Double checkBoxSize) { this.checkBoxSize = checkBoxSize; }
        public List<String> getDropdownItems() { return dropdownItems; }
        public void setDropdownItems(List<String> dropdownItems) { this.dropdownItems = dropdownItems; }

        public enum FormFieldType { TEXT, CHECKBOX, DROPDOWN }
    }

    /**
     * 图片资源。
     */
    public static class ImageResource {
        private String id;
        private String name;
        private String mimeType;           // "image/png", "image/jpeg", "image/gif", "image/svg+xml"
        private String encoding;           // "base64" 或 "url"
        private String data;               // base64 编码或 URL
        private Double width;
        private Double height;
        private String altText;
        private String relationshipId;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getName() { return name; }
        public void setName(String name) { this.name = name; }
        public String getMimeType() { return mimeType; }
        public void setMimeType(String mimeType) { this.mimeType = mimeType; }
        public String getEncoding() { return encoding; }
        public void setEncoding(String encoding) { this.encoding = encoding; }
        public String getData() { return data; }
        public void setData(String data) { this.data = data; }
        public Double getWidth() { return width; }
        public void setWidth(Double width) { this.width = width; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
        public String getAltText() { return altText; }
        public void setAltText(String altText) { this.altText = altText; }
        public String getRelationshipId() { return relationshipId; }
        public void setRelationshipId(String relationshipId) { this.relationshipId = relationshipId; }
    }

    /**
     * 方程（LaTeX 输入）。
     */
    public static class Equation {
        private String id;
        private String latex;              // LaTeX 输入
        private String omath;              // OMML（Office Math Markup Language）
        private String displayType;        // "inline" 或 "display"

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getLatex() { return latex; }
        public void setLatex(String latex) { this.latex = latex; }
        public String getOmath() { return omath; }
        public void setOmath(String omath) { this.omath = omath; }
        public String getDisplayType() { return displayType; }
        public void setDisplayType(String displayType) { this.displayType = displayType; }
    }

    /**
     * 图表（Mermaid → 原生可编辑形状或全保真 PNG）。
     */
    public static class Chart {
        private String id;
        private String title;
        private ChartType type;
        private String mermaidSource;      // Mermaid 图表源
        private String mermaidPngData;     // 全保真 PNG 编码
        private String nativeShapeData;    // 原生可编辑形状数据
        private String categoryData;       // 分类数据（JSON）
        private String seriesData;         // 系列数据（JSON）
        private List<String> colors;
        private Boolean showLegend;
        private Boolean showDataLabels;

        public Chart() { this.colors = new ArrayList<>(); }

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public ChartType getType() { return type; }
        public void setType(ChartType type) { this.type = type; }
        public String getMermaidSource() { return mermaidSource; }
        public void setMermaidSource(String mermaidSource) { this.mermaidSource = mermaidSource; }
        public String getMermaidPngData() { return mermaidPngData; }
        public void setMermaidPngData(String mermaidPngData) { this.mermaidPngData = mermaidPngData; }
        public String getNativeShapeData() { return nativeShapeData; }
        public void setNativeShapeData(String nativeShapeData) { this.nativeShapeData = nativeShapeData; }
        public String getCategoryData() { return categoryData; }
        public void setCategoryData(String categoryData) { this.categoryData = categoryData; }
        public String getSeriesData() { return seriesData; }
        public void setSeriesData(String seriesData) { this.seriesData = seriesData; }
        public List<String> getColors() { return colors; }
        public void setColors(List<String> colors) { this.colors = colors; }
        public Boolean getShowLegend() { return showLegend; }
        public void setShowLegend(Boolean showLegend) { this.showLegend = showLegend; }
        public Boolean getShowDataLabels() { return showDataLabels; }
        public void setShowDataLabels(Boolean showDataLabels) { this.showDataLabels = showDataLabels; }

        public enum ChartType {
            BAR, COLUMN, LINE, PIE, DOUGHNUT, AREA, SCATTER, RADAR, GAUGE, FUNNEL, WATERFALL, MERMAID
        }
    }

    /**
     * OLE 对象。
     */
    public static class OleObject {
        private String id;
        private String progId;            // "Word.Document.12", "Excel.Sheet.12" 等
        private String oleData;           // OLE 二进制数据（base64）
        private String iconData;          // 图标数据
        private String displayName;
        private Double width;
        private Double height;

        public String getId() { return id; }
        public void setId(String id) { this.id = id; }
        public String getProgId() { return progId; }
        public void setProgId(String progId) { this.progId = progId; }
        public String getOleData() { return oleData; }
        public void setOleData(String oleData) { this.oleData = oleData; }
        public String getIconData() { return iconData; }
        public void setIconData(String iconData) { this.iconData = iconData; }
        public String getDisplayName() { return displayName; }
        public void setDisplayName(String displayName) { this.displayName = displayName; }
        public Double getWidth() { return width; }
        public void setWidth(Double width) { this.width = width; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
    }
}