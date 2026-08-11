package com.subtlesight.word.model.i18n;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文档级 RTL 配置。
 * <p>
 * 控制整个文档的阅读顺序、装订线方向、页面边框方向等。
 * 对应 OOXML 中的 {@code <w:document>} 和 {@code <w:sectPr>} 属性。
 * </p>
 */
public class RtlConfiguration {

    /** 自动启用 RTL（基于文档语言） */
    private boolean autoEnableRtl;

    /** 文档级文本方向 */
    private TextDirection textDirection;

    /** 段落默认方向是否为 RTL */
    private Boolean rtl;

    /** 装订线在右侧（RTL 文档） */
    private Boolean rtlGutter;

    /** 页面边框跟随 RTL */
    private Boolean rtlPgBorders;

    /** 拉丁文字语言标签 */
    private LanguageTag latinLanguage;

    /** 东亚文字语言标签 */
    private LanguageTag eastAsianLanguage;

    /** 复杂文字语言标签（RTL scripts） */
    private LanguageTag complexScriptLanguage;

    /** 文档默认 RTL 段落 */
    private Boolean defaultParagraphRtl;

    /** 是否启用复杂文字排版 */
    private Boolean complexScriptEnabled;

    /** 额外属性 */
    private Map<String, String> additionalProperties;

    public RtlConfiguration() {
        this.textDirection = TextDirection.LEFT_TO_RIGHT;
        this.autoEnableRtl = false;
        this.complexScriptEnabled = true;
        this.additionalProperties = new LinkedHashMap<>();
    }

    public boolean isAutoEnableRtl() { return autoEnableRtl; }
    public void setAutoEnableRtl(boolean autoEnableRtl) { this.autoEnableRtl = autoEnableRtl; }

    public TextDirection getTextDirection() { return textDirection; }
    public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }

    public Boolean getRtl() { return rtl; }
    public void setRtl(Boolean rtl) { this.rtl = rtl; }

    public Boolean getRtlGutter() { return rtlGutter; }
    public void setRtlGutter(Boolean rtlGutter) { this.rtlGutter = rtlGutter; }

    public Boolean getRtlPgBorders() { return rtlPgBorders; }
    public void setRtlPgBorders(Boolean rtlPgBorders) { this.rtlPgBorders = rtlPgBorders; }

    public LanguageTag getLatinLanguage() { return latinLanguage; }
    public void setLatinLanguage(LanguageTag latinLanguage) { this.latinLanguage = latinLanguage; }

    public LanguageTag getEastAsianLanguage() { return eastAsianLanguage; }
    public void setEastAsianLanguage(LanguageTag eastAsianLanguage) { this.eastAsianLanguage = eastAsianLanguage; }

    public LanguageTag getComplexScriptLanguage() { return complexScriptLanguage; }
    public void setComplexScriptLanguage(LanguageTag complexScriptLanguage) { this.complexScriptLanguage = complexScriptLanguage; }

    public Boolean getDefaultParagraphRtl() { return defaultParagraphRtl; }
    public void setDefaultParagraphRtl(Boolean defaultParagraphRtl) { this.defaultParagraphRtl = defaultParagraphRtl; }

    public Boolean getComplexScriptEnabled() { return complexScriptEnabled; }
    public void setComplexScriptEnabled(Boolean complexScriptEnabled) { this.complexScriptEnabled = complexScriptEnabled; }

    public Map<String, String> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, String> additionalProperties) { this.additionalProperties = additionalProperties; }

    /**
     * 根据语言自动推断 RTL 设置。
     */
    public void autoDetectFromLanguage(LanguageTag docLang) {
        if (docLang != null && docLang.isRtl()) {
            this.autoEnableRtl = true;
            this.rtl = true;
            this.textDirection = TextDirection.RIGHT_TO_LEFT;
            this.rtlGutter = true;
            this.rtlPgBorders = true;
            this.complexScriptLanguage = docLang;
        }
    }
}