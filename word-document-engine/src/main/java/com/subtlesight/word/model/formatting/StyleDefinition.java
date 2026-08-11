package com.subtlesight.word.model.formatting;

import com.subtlesight.word.model.i18n.FontSlot;
import com.subtlesight.word.model.i18n.LanguageTag;

import java.util.*;

/**
 * Word 样式定义。
 * <p>
 * 支持段落样式、字符样式、表格样式、列表样式、编号样式。
 * 每个样式包含段落属性、运行属性、表格属性等。
 * </p>
 */
public class StyleDefinition {

    private String styleId;
    private String name;
    private StyleType type;
    private String basedOn;
    private String nextStyle;
    private String linkStyle;
    private Boolean autoRedefine;
    private Boolean hidden;
    private Boolean primaryStyle;
    private Boolean customStyle;
    private Boolean semiHidden;
    private Boolean userClear;
    private Boolean locked;
    private Boolean personal;
    private Boolean personalCompose;
    private Boolean personalReply;
    private Integer priority;
    private String defaultStyleId;

    // ======== 段落格式 ========
    private ParagraphFormat paragraphFormat;

    // ======== 运行格式 ========
    private RunFormat runFormat;

    // ======== 表格格式 ========
    private TableFormat tableFormat;

    // ======== 编号格式 ========
    private NumberingFormat numberingFormat;

    // ======== 字体槽 ========
    private List<FontSlot> fontSlots;

    // ======== 语言标签 ========
    private LanguageTag languageTag;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public StyleDefinition() {
        this.fontSlots = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public String getStyleId() { return styleId; }
    public void setStyleId(String styleId) { this.styleId = styleId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public StyleType getType() { return type; }
    public void setType(StyleType type) { this.type = type; }
    public String getBasedOn() { return basedOn; }
    public void setBasedOn(String basedOn) { this.basedOn = basedOn; }
    public String getNextStyle() { return nextStyle; }
    public void setNextStyle(String nextStyle) { this.nextStyle = nextStyle; }
    public String getLinkStyle() { return linkStyle; }
    public void setLinkStyle(String linkStyle) { this.linkStyle = linkStyle; }
    public Boolean getAutoRedefine() { return autoRedefine; }
    public void setAutoRedefine(Boolean autoRedefine) { this.autoRedefine = autoRedefine; }
    public Boolean getHidden() { return hidden; }
    public void setHidden(Boolean hidden) { this.hidden = hidden; }
    public Boolean getPrimaryStyle() { return primaryStyle; }
    public void setPrimaryStyle(Boolean primaryStyle) { this.primaryStyle = primaryStyle; }
    public Boolean getCustomStyle() { return customStyle; }
    public void setCustomStyle(Boolean customStyle) { this.customStyle = customStyle; }
    public Boolean getSemiHidden() { return semiHidden; }
    public void setSemiHidden(Boolean semiHidden) { this.semiHidden = semiHidden; }
    public Boolean getUserClear() { return userClear; }
    public void setUserClear(Boolean userClear) { this.userClear = userClear; }
    public Boolean getLocked() { return locked; }
    public void setLocked(Boolean locked) { this.locked = locked; }
    public Boolean getPersonal() { return personal; }
    public void setPersonal(Boolean personal) { this.personal = personal; }
    public Boolean getPersonalCompose() { return personalCompose; }
    public void setPersonalCompose(Boolean personalCompose) { this.personalCompose = personalCompose; }
    public Boolean getPersonalReply() { return personalReply; }
    public void setPersonalReply(Boolean personalReply) { this.personalReply = personalReply; }
    public Integer getPriority() { return priority; }
    public void setPriority(Integer priority) { this.priority = priority; }
    public String getDefaultStyleId() { return defaultStyleId; }
    public void setDefaultStyleId(String defaultStyleId) { this.defaultStyleId = defaultStyleId; }
    public ParagraphFormat getParagraphFormat() { return paragraphFormat; }
    public void setParagraphFormat(ParagraphFormat paragraphFormat) { this.paragraphFormat = paragraphFormat; }
    public RunFormat getRunFormat() { return runFormat; }
    public void setRunFormat(RunFormat runFormat) { this.runFormat = runFormat; }
    public TableFormat getTableFormat() { return tableFormat; }
    public void setTableFormat(TableFormat tableFormat) { this.tableFormat = tableFormat; }
    public NumberingFormat getNumberingFormat() { return numberingFormat; }
    public void setNumberingFormat(NumberingFormat numberingFormat) { this.numberingFormat = numberingFormat; }
    public List<FontSlot> getFontSlots() { return fontSlots; }
    public void setFontSlots(List<FontSlot> fontSlots) { this.fontSlots = fontSlots; }
    public LanguageTag getLanguageTag() { return languageTag; }
    public void setLanguageTag(LanguageTag languageTag) { this.languageTag = languageTag; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    public enum StyleType {
        PARAGRAPH,
        CHARACTER,
        TABLE,
        NUMBERING,
        LIST,
        DEFAULT_PARAGRAPH,
        DEFAULT_RUN,
        DEFAULT_TABLE
    }

    /**
     * 编号格式。
     */
    public static class NumberingFormat {
        private String numberingId;
        private String numberFormat;        // "decimal", "upperRoman", "lowerRoman", "upperLetter", "lowerLetter", "ordinal", "bullet", "none"
        private String numberText;          // 编号文本（如 "%1."）
        private Integer numberLevel;        // 级别
        private Integer startValue;         // 起始值
        private String fontName;
        private String alignment;           // "left", "center", "right"
        private Double indentLeft;
        private Double indentHanging;

        public String getNumberingId() { return numberingId; }
        public void setNumberingId(String numberingId) { this.numberingId = numberingId; }
        public String getNumberFormat() { return numberFormat; }
        public void setNumberFormat(String numberFormat) { this.numberFormat = numberFormat; }
        public String getNumberText() { return numberText; }
        public void setNumberText(String numberText) { this.numberText = numberText; }
        public Integer getNumberLevel() { return numberLevel; }
        public void setNumberLevel(Integer numberLevel) { this.numberLevel = numberLevel; }
        public Integer getStartValue() { return startValue; }
        public void setStartValue(Integer startValue) { this.startValue = startValue; }
        public String getFontName() { return fontName; }
        public void setFontName(String fontName) { this.fontName = fontName; }
        public String getAlignment() { return alignment; }
        public void setAlignment(String alignment) { this.alignment = alignment; }
        public Double getIndentLeft() { return indentLeft; }
        public void setIndentLeft(Double indentLeft) { this.indentLeft = indentLeft; }
        public Double getIndentHanging() { return indentHanging; }
        public void setIndentHanging(Double indentHanging) { this.indentHanging = indentHanging; }
    }
}