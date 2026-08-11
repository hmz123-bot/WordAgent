package com.subtlesight.word.model.i18n;

import java.util.Objects;

/**
 * 每个脚本槽的字体配置。
 * <p>
 * 对应 OOXML {@code <w:rFonts>} 元素，每个 run 可以为不同脚本指定不同字体。
 * 支持复杂文字的加粗、倾斜、大小独立控制。
 * </p>
 */
public class FontSlot {

    private ScriptType scriptType;
    private String fontName;
    private String themeFont;
    private Double fontSize;        // 半磅单位（half-point）
    private Boolean bold;
    private Boolean italic;
    private Boolean complexScriptBold;
    private Boolean complexScriptItalic;
    private LanguageTag languageTag;

    public FontSlot() {
    }

    public FontSlot(ScriptType scriptType, String fontName) {
        this.scriptType = scriptType;
        this.fontName = fontName;
    }

    public ScriptType getScriptType() { return scriptType; }
    public void setScriptType(ScriptType scriptType) { this.scriptType = scriptType; }

    public String getFontName() { return fontName; }
    public void setFontName(String fontName) { this.fontName = fontName; }

    public String getThemeFont() { return themeFont; }
    public void setThemeFont(String themeFont) { this.themeFont = themeFont; }

    public Double getFontSize() { return fontSize; }
    public void setFontSize(Double fontSize) { this.fontSize = fontSize; }

    public Boolean getBold() { return bold; }
    public void setBold(Boolean bold) { this.bold = bold; }

    public Boolean getItalic() { return italic; }
    public void setItalic(Boolean italic) { this.italic = italic; }

    public Boolean getComplexScriptBold() { return complexScriptBold; }
    public void setComplexScriptBold(Boolean complexScriptBold) { this.complexScriptBold = complexScriptBold; }

    public Boolean getComplexScriptItalic() { return complexScriptItalic; }
    public void setComplexScriptItalic(Boolean complexScriptItalic) { this.complexScriptItalic = complexScriptItalic; }

    public LanguageTag getLanguageTag() { return languageTag; }
    public void setLanguageTag(LanguageTag languageTag) { this.languageTag = languageTag; }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FontSlot fontSlot = (FontSlot) o;
        return scriptType == fontSlot.scriptType &&
                Objects.equals(fontName, fontSlot.fontName) &&
                Objects.equals(themeFont, fontSlot.themeFont);
    }

    @Override
    public int hashCode() {
        return Objects.hash(scriptType, fontName, themeFont);
    }
}