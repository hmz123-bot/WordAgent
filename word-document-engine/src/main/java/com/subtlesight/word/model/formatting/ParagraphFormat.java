package com.subtlesight.word.model.formatting;

import com.subtlesight.word.model.i18n.FontSlot;
import com.subtlesight.word.model.i18n.LanguageTag;
import com.subtlesight.word.model.i18n.TextDirection;

import java.util.*;

/**
 * 段落格式模型。
 * <p>
 * 支持：
 * <ul>
 *   <li>framePr（文本框框架属性）</li>
 *   <li>制表表速记（tab stops shorthand）</li>
 *   <li>基于字符的缩进</li>
 *   <li>RTL/BiDi</li>
 * </ul>
 * </p>
 */
public class ParagraphFormat {

    // ======== 基本对齐 ========
    private Alignment alignment;

    // ======== 缩进（支持基于字符的缩进） ========
    private Double indentLeft;          // 单位：英寸或字符数
    private Double indentRight;
    private Double indentFirstLine;
    private Double indentHanging;       // 悬挂缩进
    private String indentLeftUnit;      // "char" 或 "inch"
    private String indentRightUnit;
    private String indentFirstLineUnit;

    // ======== 间距 ========
    private Double spacingBefore;       // 段前间距（磅）
    private Double spacingAfter;        // 段后间距（磅）
    private Double lineSpacing;         // 行距
    private Integer lineSpacingRule;    // 0=auto, 1=exact, 2=atLeast, 3=multiple

    // ======== 框架属性 (framePr) ========
    private FrameProperties framePr;

    // ======== 制表位 ========
    private List<TabStop> tabStops;

    // ======== 文本方向 ========
    private TextDirection textDirection;

    // ======== 段落方向 ========
    private Boolean rtl;

    // ======== 段落标记字体 ========
    private List<FontSlot> fontSlots;

    // ======== 段落标记语言 ========
    private LanguageTag languageTag;

    // ======== 边框和底纹 ========
    private List<Border> borders;
    private Shading shading;

    // ======== 编号/列表 ========
    private String numberingId;
    private Integer numberingLevel;

    // ======== 段落样式 ========
    private String styleId;

    // ======== 分页控制 ========
    private Boolean keepWithNext;
    private Boolean keepLines;
    private Boolean pageBreakBefore;
    private Boolean widowControl;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public ParagraphFormat() {
        this.tabStops = new ArrayList<>();
        this.fontSlots = new ArrayList<>();
        this.borders = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public Alignment getAlignment() { return alignment; }
    public void setAlignment(Alignment alignment) { this.alignment = alignment; }

    public Double getIndentLeft() { return indentLeft; }
    public void setIndentLeft(Double indentLeft) { this.indentLeft = indentLeft; }

    public Double getIndentRight() { return indentRight; }
    public void setIndentRight(Double indentRight) { this.indentRight = indentRight; }

    public Double getIndentFirstLine() { return indentFirstLine; }
    public void setIndentFirstLine(Double indentFirstLine) { this.indentFirstLine = indentFirstLine; }

    public Double getIndentHanging() { return indentHanging; }
    public void setIndentHanging(Double indentHanging) { this.indentHanging = indentHanging; }

    public String getIndentLeftUnit() { return indentLeftUnit; }
    public void setIndentLeftUnit(String indentLeftUnit) { this.indentLeftUnit = indentLeftUnit; }

    public String getIndentRightUnit() { return indentRightUnit; }
    public void setIndentRightUnit(String indentRightUnit) { this.indentRightUnit = indentRightUnit; }

    public String getIndentFirstLineUnit() { return indentFirstLineUnit; }
    public void setIndentFirstLineUnit(String indentFirstLineUnit) { this.indentFirstLineUnit = indentFirstLineUnit; }

    public Double getSpacingBefore() { return spacingBefore; }
    public void setSpacingBefore(Double spacingBefore) { this.spacingBefore = spacingBefore; }

    public Double getSpacingAfter() { return spacingAfter; }
    public void setSpacingAfter(Double spacingAfter) { this.spacingAfter = spacingAfter; }

    public Double getLineSpacing() { return lineSpacing; }
    public void setLineSpacing(Double lineSpacing) { this.lineSpacing = lineSpacing; }

    public Integer getLineSpacingRule() { return lineSpacingRule; }
    public void setLineSpacingRule(Integer lineSpacingRule) { this.lineSpacingRule = lineSpacingRule; }

    public FrameProperties getFramePr() { return framePr; }
    public void setFramePr(FrameProperties framePr) { this.framePr = framePr; }

    public List<TabStop> getTabStops() { return tabStops; }
    public void setTabStops(List<TabStop> tabStops) { this.tabStops = tabStops; }

    public TextDirection getTextDirection() { return textDirection; }
    public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }

    public Boolean getRtl() { return rtl; }
    public void setRtl(Boolean rtl) { this.rtl = rtl; }

    public List<FontSlot> getFontSlots() { return fontSlots; }
    public void setFontSlots(List<FontSlot> fontSlots) { this.fontSlots = fontSlots; }

    public LanguageTag getLanguageTag() { return languageTag; }
    public void setLanguageTag(LanguageTag languageTag) { this.languageTag = languageTag; }

    public List<Border> getBorders() { return borders; }
    public void setBorders(List<Border> borders) { this.borders = borders; }

    public Shading getShading() { return shading; }
    public void setShading(Shading shading) { this.shading = shading; }

    public String getNumberingId() { return numberingId; }
    public void setNumberingId(String numberingId) { this.numberingId = numberingId; }

    public Integer getNumberingLevel() { return numberingLevel; }
    public void setNumberingLevel(Integer numberingLevel) { this.numberingLevel = numberingLevel; }

    public String getStyleId() { return styleId; }
    public void setStyleId(String styleId) { this.styleId = styleId; }

    public Boolean getKeepWithNext() { return keepWithNext; }
    public void setKeepWithNext(Boolean keepWithNext) { this.keepWithNext = keepWithNext; }

    public Boolean getKeepLines() { return keepLines; }
    public void setKeepLines(Boolean keepLines) { this.keepLines = keepLines; }

    public Boolean getPageBreakBefore() { return pageBreakBefore; }
    public void setPageBreakBefore(Boolean pageBreakBefore) { this.pageBreakBefore = pageBreakBefore; }

    public Boolean getWidowControl() { return widowControl; }
    public void setWidowControl(Boolean widowControl) { this.widowControl = widowControl; }

    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    // ======== 内部类型 ========

    public enum Alignment {
        LEFT, CENTER, RIGHT, BOTH, DISTRIBUTE, JUSTIFIED_MED, JUSTIFIED_HIGH, JUSTIFIED_LOW
    }

    /**
     * 框架属性 (framePr)。
     */
    public static class FrameProperties {
        private Double x;           // 水平位置
        private Double y;           // 垂直位置
        private Double width;       // 宽度
        private Double height;      // 高度
        private String xAlign;      // "left", "center", "right", "inside", "outside"
        private String yAlign;      // "top", "center", "bottom", "inside", "outside"
        private String hAnchor;     // "text", "margin", "page"
        private String vAnchor;     // "text", "margin", "page"
        private Boolean dropCap;    // 首字下沉
        private String wrap;        // "around", "auto", "none"

        public Double getX() { return x; }
        public void setX(Double x) { this.x = x; }
        public Double getY() { return y; }
        public void setY(Double y) { this.y = y; }
        public Double getWidth() { return width; }
        public void setWidth(Double width) { this.width = width; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
        public String getXAlign() { return xAlign; }
        public void setXAlign(String xAlign) { this.xAlign = xAlign; }
        public String getYAlign() { return yAlign; }
        public void setYAlign(String yAlign) { this.yAlign = yAlign; }
        public String getHAnchor() { return hAnchor; }
        public void setHAnchor(String hAnchor) { this.hAnchor = hAnchor; }
        public String getVAnchor() { return vAnchor; }
        public void setVAnchor(String vAnchor) { this.vAnchor = vAnchor; }
        public Boolean getDropCap() { return dropCap; }
        public void setDropCap(Boolean dropCap) { this.dropCap = dropCap; }
        public String getWrap() { return wrap; }
        public void setWrap(String wrap) { this.wrap = wrap; }
    }

    /**
     * 制表位。
     */
    public static class TabStop {
        private double position;    // 位置（英寸）
        private String value;       // "left", "center", "right", "decimal", "bar", "clear", "num"
        private String leader;      // "none", "dot", "hyphen", "underscore", "heavy", "middleDot"

        public double getPosition() { return position; }
        public void setPosition(double position) { this.position = position; }
        public String getValue() { return value; }
        public void setValue(String value) { this.value = value; }
        public String getLeader() { return leader; }
        public void setLeader(String leader) { this.leader = leader; }
    }

    /**
     * 边框。
     */
    public static class Border {
        private String side;        // "top", "bottom", "left", "right", "between", "bar"
        private String style;       // "single", "double", "dotted", "dashed", "inset", "outset", "threeD", "none"
        private double size;        // 大小（磅）
        private String color;       // 颜色（十六进制或自动）
        private String space;       // 间距
        private Boolean shadow;     // 阴影

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
     * 底纹。
     */
    public static class Shading {
        private String fill;        // 填充色
        private String pattern;     // 图案
        private String patternColor;

        public String getFill() { return fill; }
        public void setFill(String fill) { this.fill = fill; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getPatternColor() { return patternColor; }
        public void setPatternColor(String patternColor) { this.patternColor = patternColor; }
    }
}