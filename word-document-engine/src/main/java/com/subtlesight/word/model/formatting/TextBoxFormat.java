package com.subtlesight.word.model.formatting;

import java.util.*;

/**
 * 文本框/形状格式模型。
 * <p>
 * 支持：
 * <ul>
 *   <li>旋轉</li>
 *   <li>渐变</li>
 *   <li>阴影</li>
 *   <li>不透明度</li>
 *   <li>文本方向</li>
 *   <li>文本环绕</li>
 * </ul>
 * </p>
 */
public class TextBoxFormat {

    private String id;
    private String name;

    // ======== 位置和大小 ========
    private Double left;
    private Double top;
    private Double width;
    private Double height;
    private Double rotation;              // 旋转角度（度）

    // ======== 文本环绕 ========
    private WrapStyle wrapStyle;
    private String wrapSide;              // "both", "left", "right", "largest"

    // ======== 填充 ========
    private FillType fillType;
    private String solidColor;
    private Gradient gradient;
    private String imageFill;

    // ======== 线条 ========
    private LineFormat line;

    // ======== 阴影 ========
    private Shadow shadow;

    // ======== 不透明度 ========
    private Double opacity;               // 0.0 ~ 1.0

    // ======== 文本方向 ========
    private String textDirection;         // "lrtb", "rltb", "tbrl", "btlr"

    // ======== 内边距 ========
    private Double marginTop;
    private Double marginBottom;
    private Double marginLeft;
    private Double marginRight;

    // ======== 形状属性 ========
    private String shapeType;             // "rect", "roundRect", "ellipse", "diamond", "triangle", "pentagon", "hexagon", "octagon", "star", "arrow", "line", "freeform", "mermaid"
    private String mermaidSource;         // Mermaid 图表源文本

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public TextBoxFormat() {
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Double getLeft() { return left; }
    public void setLeft(Double left) { this.left = left; }
    public Double getTop() { return top; }
    public void setTop(Double top) { this.top = top; }
    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }
    public Double getHeight() { return height; }
    public void setHeight(Double height) { this.height = height; }
    public Double getRotation() { return rotation; }
    public void setRotation(Double rotation) { this.rotation = rotation; }
    public WrapStyle getWrapStyle() { return wrapStyle; }
    public void setWrapStyle(WrapStyle wrapStyle) { this.wrapStyle = wrapStyle; }
    public String getWrapSide() { return wrapSide; }
    public void setWrapSide(String wrapSide) { this.wrapSide = wrapSide; }
    public FillType getFillType() { return fillType; }
    public void setFillType(FillType fillType) { this.fillType = fillType; }
    public String getSolidColor() { return solidColor; }
    public void setSolidColor(String solidColor) { this.solidColor = solidColor; }
    public Gradient getGradient() { return gradient; }
    public void setGradient(Gradient gradient) { this.gradient = gradient; }
    public String getImageFill() { return imageFill; }
    public void setImageFill(String imageFill) { this.imageFill = imageFill; }
    public LineFormat getLine() { return line; }
    public void setLine(LineFormat line) { this.line = line; }
    public Shadow getShadow() { return shadow; }
    public void setShadow(Shadow shadow) { this.shadow = shadow; }
    public Double getOpacity() { return opacity; }
    public void setOpacity(Double opacity) { this.opacity = opacity; }
    public String getTextDirection() { return textDirection; }
    public void setTextDirection(String textDirection) { this.textDirection = textDirection; }
    public Double getMarginTop() { return marginTop; }
    public void setMarginTop(Double marginTop) { this.marginTop = marginTop; }
    public Double getMarginBottom() { return marginBottom; }
    public void setMarginBottom(Double marginBottom) { this.marginBottom = marginBottom; }
    public Double getMarginLeft() { return marginLeft; }
    public void setMarginLeft(Double marginLeft) { this.marginLeft = marginLeft; }
    public Double getMarginRight() { return marginRight; }
    public void setMarginRight(Double marginRight) { this.marginRight = marginRight; }
    public String getShapeType() { return shapeType; }
    public void setShapeType(String shapeType) { this.shapeType = shapeType; }
    public String getMermaidSource() { return mermaidSource; }
    public void setMermaidSource(String mermaidSource) { this.mermaidSource = mermaidSource; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    // ======== 内部类型 ========

    public enum WrapStyle { NONE, SQUARE, TIGHT, THROUGH, TOP_BOTTOM, BEHIND, IN_FRONT }

    public enum FillType { SOLID, GRADIENT, PATTERN, IMAGE, NONE }

    /**
     * 渐变。
     */
    public static class Gradient {
        private double angle;                       // 角度
        private List<GradientStop> stops;

        public Gradient() { this.stops = new ArrayList<>(); }

        public double getAngle() { return angle; }
        public void setAngle(double angle) { this.angle = angle; }
        public List<GradientStop> getStops() { return stops; }
        public void setStops(List<GradientStop> stops) { this.stops = stops; }

        public static class GradientStop {
            private double position;    // 0.0 ~ 1.0
            private String color;
            private Double opacity;

            public double getPosition() { return position; }
            public void setPosition(double position) { this.position = position; }
            public String getColor() { return color; }
            public void setColor(String color) { this.color = color; }
            public Double getOpacity() { return opacity; }
            public void setOpacity(Double opacity) { this.opacity = opacity; }
        }
    }

    /**
     * 线条格式。
     */
    public static class LineFormat {
        private String color;
        private double width;
        private String style;          // "single", "double", "dotted", "dashed", "none"
        private String capType;        // "flat", "round", "square"

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        public String getStyle() { return style; }
        public void setStyle(String style) { this.style = style; }
        public String getCapType() { return capType; }
        public void setCapType(String capType) { this.capType = capType; }
    }

    /**
     * 阴影。
     */
    public static class Shadow {
        private String color;
        private Double opacity;
        private Double blurRadius;
        private Double offsetX;
        private Double offsetY;
        private String type;            // "outer", "inner", "perspective"

        public String getColor() { return color; }
        public void setColor(String color) { this.color = color; }
        public Double getOpacity() { return opacity; }
        public void setOpacity(Double opacity) { this.opacity = opacity; }
        public Double getBlurRadius() { return blurRadius; }
        public void setBlurRadius(Double blurRadius) { this.blurRadius = blurRadius; }
        public Double getOffsetX() { return offsetX; }
        public void setOffsetX(Double offsetX) { this.offsetX = offsetX; }
        public Double getOffsetY() { return offsetY; }
        public void setOffsetY(Double offsetY) { this.offsetY = offsetY; }
        public String getType() { return type; }
        public void setType(String type) { this.type = type; }
    }
}