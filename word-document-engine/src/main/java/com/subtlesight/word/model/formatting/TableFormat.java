package com.subtlesight.word.model.formatting;

import java.util.*;

/**
 * 表格格式模型。
 * <p>
 * 支持：
 * <ul>
 *   <li>虚拟列操作（添加/删除/移动/复制）</li>
 *   <li>hMerge（水平合并单元格）</li>
 *   <li>RTL 表格方向</li>
 * </ul>
 * </p>
 */
public class TableFormat {

    // ======== 表格基本属性 ========
    private Double width;
    private String widthType;           // "auto", "dxa", "pct", "nil"
    private Alignment alignment;

    // ======== 表格方向 ========
    private Boolean rtl;

    // ======== 缩进 ========
    private Double indent;

    // ======== 边框和底纹 ========
    private List<Border> borders;
    private Shading shading;

    // ======== 列信息 ========
    private List<Column> columns;
    private int columnCount;

    // ======== 单元格间距 ========
    private Double cellSpacing;
    private Double cellMarginTop;
    private Double cellMarginBottom;
    private Double cellMarginLeft;
    private Double cellMarginRight;

    // ======== 行属性 ========
    private List<RowProperties> rows;

    // ======== 虚拟列操作 ========
    private VirtualColumnOperation pendingColumnOp;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public TableFormat() {
        this.columns = new ArrayList<>();
        this.rows = new ArrayList<>();
        this.borders = new ArrayList<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public Double getWidth() { return width; }
    public void setWidth(Double width) { this.width = width; }
    public String getWidthType() { return widthType; }
    public void setWidthType(String widthType) { this.widthType = widthType; }
    public Alignment getAlignment() { return alignment; }
    public void setAlignment(Alignment alignment) { this.alignment = alignment; }
    public Boolean getRtl() { return rtl; }
    public void setRtl(Boolean rtl) { this.rtl = rtl; }
    public Double getIndent() { return indent; }
    public void setIndent(Double indent) { this.indent = indent; }
    public List<Border> getBorders() { return borders; }
    public void setBorders(List<Border> borders) { this.borders = borders; }
    public Shading getShading() { return shading; }
    public void setShading(Shading shading) { this.shading = shading; }
    public List<Column> getColumns() { return columns; }
    public void setColumns(List<Column> columns) { this.columns = columns; }
    public int getColumnCount() { return columnCount; }
    public void setColumnCount(int columnCount) { this.columnCount = columnCount; }
    public Double getCellSpacing() { return cellSpacing; }
    public void setCellSpacing(Double cellSpacing) { this.cellSpacing = cellSpacing; }
    public Double getCellMarginTop() { return cellMarginTop; }
    public void setCellMarginTop(Double cellMarginTop) { this.cellMarginTop = cellMarginTop; }
    public Double getCellMarginBottom() { return cellMarginBottom; }
    public void setCellMarginBottom(Double cellMarginBottom) { this.cellMarginBottom = cellMarginBottom; }
    public Double getCellMarginLeft() { return cellMarginLeft; }
    public void setCellMarginLeft(Double cellMarginLeft) { this.cellMarginLeft = cellMarginLeft; }
    public Double getCellMarginRight() { return cellMarginRight; }
    public void setCellMarginRight(Double cellMarginRight) { this.cellMarginRight = cellMarginRight; }
    public List<RowProperties> getRows() { return rows; }
    public void setRows(List<RowProperties> rows) { this.rows = rows; }
    public VirtualColumnOperation getPendingColumnOp() { return pendingColumnOp; }
    public void setPendingColumnOp(VirtualColumnOperation pendingColumnOp) { this.pendingColumnOp = pendingColumnOp; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    // ======== 内部类型 ========

    public enum Alignment { LEFT, CENTER, RIGHT }

    public static class Border {
        private String side;
        private String style;
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

    public static class Shading {
        private String fill;
        private String pattern;      // "clear", "solid", "pct5", ... "pct95"
        private String patternColor;

        public String getFill() { return fill; }
        public void setFill(String fill) { this.fill = fill; }
        public String getPattern() { return pattern; }
        public void setPattern(String pattern) { this.pattern = pattern; }
        public String getPatternColor() { return patternColor; }
        public void setPatternColor(String patternColor) { this.patternColor = patternColor; }
    }

    /**
     * 列定义。
     */
    public static class Column {
        private int index;
        private double width;
        private String widthType;   // "dxa", "pct", "auto"

        public int getIndex() { return index; }
        public void setIndex(int index) { this.index = index; }
        public double getWidth() { return width; }
        public void setWidth(double width) { this.width = width; }
        public String getWidthType() { return widthType; }
        public void setWidthType(String widthType) { this.widthType = widthType; }
    }

    /**
     * 行属性。
     */
    public static class RowProperties {
        private int rowIndex;
        private Double height;
        private String heightRule;       // "auto", "exact", "atLeast"
        private Boolean headerRow;        // 标题行重复
        private Boolean cantSplit;        // 禁止跨页分断
        private List<CellProperties> cells;

        public int getRowIndex() { return rowIndex; }
        public void setRowIndex(int rowIndex) { this.rowIndex = rowIndex; }
        public Double getHeight() { return height; }
        public void setHeight(Double height) { this.height = height; }
        public String getHeightRule() { return heightRule; }
        public void setHeightRule(String heightRule) { this.heightRule = heightRule; }
        public Boolean getHeaderRow() { return headerRow; }
        public void setHeaderRow(Boolean headerRow) { this.headerRow = headerRow; }
        public Boolean getCantSplit() { return cantSplit; }
        public void setCantSplit(Boolean cantSplit) { this.cantSplit = cantSplit; }
        public List<CellProperties> getCells() { return cells; }
        public void setCells(List<CellProperties> cells) { this.cells = cells; }
    }

    /**
     * 单元格属性。
     */
    public static class CellProperties {
        private int columnIndex;
        private int colSpan;            // 水平合并（gridSpan）
        private int rowSpan;            // 垂直合并（vMerge）
        private Boolean hMerge;         // 水平合并继续
        private Boolean vMerge;         // 垂直合并继续
        private String verticalAlign;   // "top", "center", "bottom"
        private TextDirection textDirection;
        private Double width;
        private Shading shading;
        private List<Border> borders;
        private Double marginTop;
        private Double marginBottom;
        private Double marginLeft;
        private Double marginRight;

        public int getColumnIndex() { return columnIndex; }
        public void setColumnIndex(int columnIndex) { this.columnIndex = columnIndex; }
        public int getColSpan() { return colSpan; }
        public void setColSpan(int colSpan) { this.colSpan = colSpan; }
        public int getRowSpan() { return rowSpan; }
        public void setRowSpan(int rowSpan) { this.rowSpan = rowSpan; }
        public Boolean getHMerge() { return hMerge; }
        public void setHMerge(Boolean hMerge) { this.hMerge = hMerge; }
        public Boolean getVMerge() { return vMerge; }
        public void setVMerge(Boolean vMerge) { this.vMerge = vMerge; }
        public String getVerticalAlign() { return verticalAlign; }
        public void setVerticalAlign(String verticalAlign) { this.verticalAlign = verticalAlign; }
        public TextDirection getTextDirection() { return textDirection; }
        public void setTextDirection(TextDirection textDirection) { this.textDirection = textDirection; }
        public Double getWidth() { return width; }
        public void setWidth(Double width) { this.width = width; }
        public Shading getShading() { return shading; }
        public void setShading(Shading shading) { this.shading = shading; }
        public List<Border> getBorders() { return borders; }
        public void setBorders(List<Border> borders) { this.borders = borders; }
        public Double getMarginTop() { return marginTop; }
        public void setMarginTop(Double marginTop) { this.marginTop = marginTop; }
        public Double getMarginBottom() { return marginBottom; }
        public void setMarginBottom(Double marginBottom) { this.marginBottom = marginBottom; }
        public Double getMarginLeft() { return marginLeft; }
        public void setMarginLeft(Double marginLeft) { this.marginLeft = marginLeft; }
        public Double getMarginRight() { return marginRight; }
        public void setMarginRight(Double marginRight) { this.marginRight = marginRight; }

        public enum TextDirection { LTR, RTL, TB_LR, BT_LR }
    }

    /**
     * 虚拟列操作。
     */
    public static class VirtualColumnOperation {
        public enum OperationType { ADD, DELETE, MOVE, COPY }

        private OperationType operationType;
        private int sourceColumnIndex;
        private int targetColumnIndex;
        private String columnName;
        private Double columnWidth;

        public OperationType getOperationType() { return operationType; }
        public void setOperationType(OperationType operationType) { this.operationType = operationType; }
        public int getSourceColumnIndex() { return sourceColumnIndex; }
        public void setSourceColumnIndex(int sourceColumnIndex) { this.sourceColumnIndex = sourceColumnIndex; }
        public int getTargetColumnIndex() { return targetColumnIndex; }
        public void setTargetColumnIndex(int targetColumnIndex) { this.targetColumnIndex = targetColumnIndex; }
        public String getColumnName() { return columnName; }
        public void setColumnName(String columnName) { this.columnName = columnName; }
        public Double getColumnWidth() { return columnWidth; }
        public void setColumnWidth(Double columnWidth) { this.columnWidth = columnWidth; }
    }
}