package com.subtlesight.word.model.formatting;

import com.subtlesight.word.model.i18n.LanguageTag;
import com.subtlesight.word.model.i18n.RtlConfiguration;
import com.subtlesight.word.model.i18n.TextDirection;

import java.util.*;

/**
 * 文档级属性模型。
 * <p>
 * 支持：
 * <ul>
 *   <li>lang（latin/ea/cs - 每个脚本的语言）</li>
 *   <li>direction=rtl（文档方向）</li>
 *   <li>rtlGutter（RTL 装订线）</li>
 *   <li>pgBorders（页面边框）</li>
 *   <li>页面背景色</li>
 *   <li>文档默认格式</li>
 * </ul>
 * </p>
 */
public class DocumentProperties {

    // ======== 语言设置 ========
    private LanguageTag latinLanguage;       // w:lang/@val
    private LanguageTag eastAsianLanguage;   // w:lang/@eastAsia
    private LanguageTag complexScriptLanguage; // w:lang/@bidi

    // ======== 文档方向 ========
    private TextDirection textDirection;
    private Boolean rtl;
    private Boolean rtlGutter;

    // ======== 页面背景 ========
    private String pageBackgroundColor;

    // ======== 页面边框 ========
    private List<PageBorder> pageBorders;

    // ======== 文档默认格式 ========
    private DocumentDefaults defaults;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public DocumentProperties() {
        this.textDirection = TextDirection.LEFT_TO_RIGHT;
        this.pageBorders = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    public LanguageTag getLatinLanguage() { return latinLanguage; }
    public void setLatinLanguage(LanguageTag latinLanguage) { this.latinLanguage = latinLanguage; }
    public LanguageTag getEastAsianLanguage() { return eastAsianLanguage; }
    public void setEastAsianLanguage(LanguageTag eastAsianLanguage) { this.eastAsianLanguage = eastAsianLanguage; }
    public LanguageTag getComplexScriptLanguage() { return complexScriptLanguage; }
    public void setComplexScriptLanguage(LanguageTag complexScriptLanguage) { this.complexScriptLanguage = complexScriptLanguage; }
    public TextDirection getTextDirection() { return textDirection; }
    public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }
    public Boolean getRtl() { return rtl; }
    public void setRtl(Boolean rtl) { this.rtl = rtl; }
    public Boolean getRtlGutter() { return rtlGutter; }
    public void setRtlGutter(Boolean rtlGutter) { this.rtlGutter = rtlGutter; }
    public String getPageBackgroundColor() { return pageBackgroundColor; }
    public void setPageBackgroundColor(String pageBackgroundColor) { this.pageBackgroundColor = pageBackgroundColor; }
    public List<PageBorder> getPageBorders() { return pageBorders; }
    public void setPageBorders(List<PageBorder> pageBorders) { this.pageBorders = pageBorders; }
    public DocumentDefaults getDefaults() { return defaults; }
    public void setDefaults(DocumentDefaults defaults) { this.defaults = defaults; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    /**
     * 页面边框。
     */
    public static class PageBorder {
        private String side;        // "top", "bottom", "left", "right"
        private String style;       // "single", "double", "dotted", "dashed", "threeD", "inset", "outset"
        private double size;
        private String color;
        private String space;
        private Boolean shadow;

        public String getSide() { return side; }
        public void setSide(String side) { this.side = side; }
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public double getSize() { return size; }
        public void setSize(double size) { this.size = size; }
        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public String getSpace() { return space; }
        public void setSpace(String space) { this.space = space; }
        public Boolean getShadow() { return shadow; }
        public void setShadow(Boolean shadow) { this.shadow = shadow; }
    }

    /**
     * 文档默认格式。
     */
    public static class DocumentDefaults {
        // 默认运行属性
        private String defaultRunFont;
        private String defaultRunFontEastAsia;
        private String defaultRunFontComplex;
        private Double defaultRunFontSize;
        private Double defaultRunFontSizeComplex;
        private Boolean defaultRunBold;
        private Boolean defaultRunItalic;
        private String defaultRunColor;
        private LanguageTag defaultRunLanguage;
        private LanguageTag defaultRunEastAsianLanguage;
        private LanguageTag defaultRunComplexScriptLanguage;

        // 默认段落属性
        private Boolean defaultParagraphRtl;
        private String defaultParagraphStyle;

        public String getDefaultRunFont() { return defaultRunFont; }
        public void setDefaultRunFont(String defaultRunFont) { this.defaultRunFont = defaultRunFont; }
        public String getDefaultRunFontEastAsia() { return defaultRunFontEastAsia; }
        public void setDefaultRunFontEastAsia(String defaultRunFontEastAsia) { this.defaultRunFontEastAsia = defaultRunFontEastAsia; }
        public String getDefaultRunFontComplex() { return defaultRunFontComplex; }
        public void setDefaultRunFontComplex(String defaultRunFontComplex) { this.defaultRunFontComplex = defaultRunFontComplex; }
        public Double getDefaultRunFontSize() { return defaultRunFontSize; }
        public void setDefaultRunFontSize(Double defaultRunFontSize) { this.defaultRunFontSize = defaultRunFontSize; }
        public Double getDefaultRunFontSizeComplex() { return defaultRunFontSizeComplex; }
        public void setDefaultRunFontSizeComplex(Double defaultRunFontSizeComplex) { this.defaultRunFontSizeComplex = defaultRunFontSizeComplex; }
        public Boolean getDefaultRunBold() { return defaultRunBold; }
        public void setDefaultRunBold(Boolean defaultRunBold) { this.defaultRunBold = defaultRunBold; }
        public Boolean getDefaultRunItalic() { return defaultRunItalic; }
        public void setDefaultRunItalic(Boolean defaultRunItalic) { this.defaultRunItalic = defaultRunItalic; }
        public String getDefaultRunColor() { return defaultRunColor; }
        public void setDefaultRunColor(String defaultRunColor) { this.defaultRunColor = defaultRunColor; }
        public LanguageTag getDefaultRunLanguage() { return defaultRunLanguage; }
        public void setDefaultRunLanguage(LanguageTag defaultRunLanguage) { this.defaultRunLanguage = defaultRunLanguage; }
        public LanguageTag getDefaultRunEastAsianLanguage() { return defaultRunEastAsianLanguage; }
        public void setDefaultRunEastAsianLanguage(LanguageTag defaultRunEastAsianLanguage) { this.defaultRunEastAsianLanguage = defaultRunEastAsianLanguage; }
        public LanguageTag getDefaultRunComplexScriptLanguage() { return defaultRunComplexScriptLanguage; }
        public void setDefaultRunComplexScriptLanguage(LanguageTag defaultRunComplexScriptLanguage) { this.defaultRunComplexScriptLanguage = defaultRunComplexScriptLanguage; }
        public Boolean getDefaultParagraphRtl() { return defaultParagraphRtl; }
        public void setDefaultParagraphRtl(Boolean defaultParagraphRtl) { this.defaultParagraphRtl = defaultParagraphRtl; }
        public String getDefaultParagraphStyle() { return defaultParagraphStyle; }
        public void setDefaultParagraphStyle(String defaultParagraphStyle) { this.defaultParagraphStyle = defaultParagraphStyle; }
    }
}