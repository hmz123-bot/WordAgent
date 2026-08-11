package com.subtlesight.word.model.formatting;

import com.subtlesight.word.model.i18n.FontSlot;
import com.subtlesight.word.model.i18n.LanguageTag;

import java.util.*;

/**
 * 运行（Run）格式模型。
 * <p>
 * 支持：
 * <ul>
 *   <li>下划线颜色（underline.color）</li>
 *   <li>半点位置（position half-point）</li>
 *   <li>每个脚本槽的字体/粗体/斜体/大小</li>
 * </ul>
 * </p>
 */
public class RunFormat {

    // ======== 字体（每个脚本槽） ========
    private List<FontSlot> fontSlots;

    // ======== 字号 ========
    private Double fontSize;            // 半磅（half-point）
    private Double fontSizeComplex;     // 复杂文字字号

    // ======== 字形 ========
    private Boolean bold;
    private Boolean italic;
    private Boolean boldComplex;
    private Boolean italicComplex;
    private Boolean strike;
    private Boolean doubleStrike;
    private Boolean smallCaps;
    private Boolean allCaps;
    private Boolean shadow;
    private Boolean outline;
    private Boolean emboss;
    private Boolean imprint;

    // ======== 下划线 ========
    private UnderlineStyle underlineStyle;
    private String underlineColor;      // 下划线颜色（十六进制 #RRGGBB 或 "auto"）

    // ======== 位置（半点） ========
    private Integer position;           // 半点位置（half-point），正数=上标，负数=下标

    // ======== 缩放和间距 ========
    private Integer characterSpacing;   // 字符间距（1/20磅）
    private Integer scaling;            // 字符缩放百分比

    // ======== 颜色 ========
    private String color;               // 前景色
    private String highlightColor;      // 高亮色
    private Shading shading;            // 底纹（复用段落格式中的 Shading）

    // ======== 语言 ========
    private LanguageTag languageTag;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public RunFormat() {
        this.fontSlots = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    public List<FontSlot> getFontSlots() { return fontSlots; }
    public void setFontSlots(List<FontSlot> fontSlots) { this.fontSlots = fontSlots; }

    public Double getFontSize() { return fontSize; }
    public void setFontSize(Double fontSize) { this.fontSize = fontSize; }

    public Double getFontSizeComplex() { return fontSizeComplex; }
    public void setFontSizeComplex(Double fontSizeComplex) { this.fontSizeComplex = fontSizeComplex; }

    public Boolean getBold() { return bold; }
    public void setBold(Boolean bold) { this.bold = bold; }

    public Boolean getItalic() { return italic; }
    public void setItalic(Boolean italic) { this.italic = italic; }

    public Boolean getBoldComplex() { return boldComplex; }
    public void setBoldComplex(Boolean boldComplex) { this.boldComplex = boldComplex; }

    public Boolean getItalicComplex() { return italicComplex; }
    public void setItalicComplex(Boolean italicComplex) { this.italicComplex = italicComplex; }

    public Boolean getStrike() { return strike; }
    public void setStrike(Boolean strike) { this.strike = strike; }

    public Boolean getDoubleStrike() { return doubleStrike; }
    public void setDoubleStrike(Boolean doubleStrike) { this.doubleStrike = doubleStrike; }

    public Boolean getSmallCaps() { return smallCaps; }
    public void setSmallCaps(Boolean smallCaps) { this.smallCaps = smallCaps; }

    public Boolean getAllCaps() { return allCaps; }
    public void setAllCaps(Boolean allCaps) { this.allCaps = allCaps; }

    public Boolean getShadow() { return shadow; }
    public void setShadow(Boolean shadow) { this.shadow = shadow; }

    public Boolean getOutline() { return outline; }
    public void setOutline(Boolean outline) { this.outline = outline; }

    public Boolean getEmboss() { return emboss; }
    public void setEmboss(Boolean emboss) { this.emboss = emboss; }

    public Boolean getImprint() { return imprint; }
    public void setImprint(Boolean imprint) { this.imprint = imprint; }

    public UnderlineStyle getUnderlineStyle() { return underlineStyle; }
    public void setUnderlineStyle(UnderlineStyle underlineStyle) { this.underlineStyle = underlineStyle; }

    public String getUnderlineColor() { return underlineColor; }
    public void setUnderlineColor(String underlineColor) { this.underlineColor = underlineColor; }

    public Integer getPosition() { return position; }
    public void setPosition(Integer position) { this.position = position; }

    public Integer getCharacterSpacing() { return characterSpacing; }
    public void setCharacterSpacing(Integer characterSpacing) { this.characterSpacing = characterSpacing; }

    public Integer getScaling() { return scaling; }
    public void setScaling(Integer scaling) { this.scaling = scaling; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getHighlightColor() { return highlightColor; }
    public void setHighlightColor(String highlightColor) { this.highlightColor = highlightColor; }

    public Shading getShading() { return shading; }
    public void setShading(Shading shading) { this.shading = shading; }

    public LanguageTag getLanguageTag() { return languageTag; }
    public void setLanguageTag(LanguageTag languageTag) { this.languageTag = languageTag; }

    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    // ======== 下划线样式 ========
    public enum UnderlineStyle {
        NONE("none"),
        SINGLE("single"),
        DOUBLE("double"),
        WORD("word"),
        DOTTED("dotted"),
        DOTTED_HEAVY("dottedHeavy"),
        DASH("dash"),
        DASHED_HEAVY("dashedHeavy"),
        DOT_DASH("dotDash"),
        DOT_DASH_HEAVY("dotDashHeavy"),
        DOT_DOT_DASH("dotDotDash"),
        DOT_DOT_DASH_HEAVY("dotDotDashHeavy"),
        WAVE("wave"),
        WAVY_HEAVY("wavyHeavy"),
        WAVY_DOUBLE("wavyDouble"),
        THICK("thick");

        private final String ooxmlValue;

        UnderlineStyle(String ooxmlValue) { this.ooxmlValue = ooxmlValue; }
        public String getOoxmlValue() { return ooxmlValue; }

        public static UnderlineStyle fromOoxml(String value) {
            if (value == null) return NONE;
            for (UnderlineStyle s : values()) {
                if (s.ooxmlValue.equals(value)) return s;
            }
            return SINGLE;
        }
    }

    /**
     * 底纹（运行时级别）。
     */
    public static class Shading {
        private String fill;
        private String pattern;
        private String patternColor;

        public String getFill() { return fill; }
        public void setFill(String fill) { this.fill = fill; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getPatternColor() { return patternColor; }
        public void setPatternColor(String patternColor) { this.patternColor = patternColor; }
    }
}