package com.subtlesight.word.model.formatting;

import java.util.*;

/**
 * Word 字段代码模型。
 * <p>
 * 支持 22 种零参数类型 + 参数化类型：
 * <ul>
 *   <li>零参数：AUTHOR, COMMENTS, DATE, CREATEDATE, DOCVARIABLE, FILENAME, FILESIZE, FILLIN, INFO, KEYWORDS, LASTSAVEDBY, NUMCHARS, NUMPAGES, NUMWORDS, PRINTDATE, PRINTTIME, SAVEDATE, SUBJECT, TEMPLATE, TIME, TITLE, DOCPROPERTY</li>
 *   <li>参数化：MERGEFIELD, REF, PAGEREF, SEQ, STYLEREF, DOCPROPERTY, IF</li>
 * </ul>
 * </p>
 */
public class FieldModel {

    private String fieldCode;
    private FieldType fieldType;
    private String fieldResult;
    private boolean dirty;
    private boolean locked;

    // ======== 参数 ========
    private Map<String, String> parameters;

    // ======== MERGEFIELD 专用 ========
    private String mergeFieldName;

    // ======== REF/PAGEREF 专用 ========
    private String referenceBookmark;
    private Boolean refRelativePosition;

    // ======== SEQ 专用 ========
    private String seqIdentifier;
    private String seqFormat;
    private Integer seqStartValue;

    // ======== STYLEREF 专用 ========
    private String styleRefStyle;
    private Boolean styleRefSearchFromBottom;

    // ======== IF 专用 ========
    private String ifExpression;
    private String ifTrueText;
    private String ifFalseText;

    // ======== DOCPROPERTY 专用 ========
    private String docPropertyName;

    // ======== 额外属性 ========
    private Map<String, Object> additionalProperties;

    public FieldModel() {
        this.parameters = new LinkedHashMap<>();
        this.additionalProperties = new LinkedHashMap<>();
    }

    // Getters & Setters
    public String getFieldCode() { return fieldCode; }
    public void setFieldCode(String fieldCode) { this.fieldCode = fieldCode; }
    public FieldType getFieldType() { return fieldType; }
    public void setFieldType(FieldType fieldType) { this.fieldType = fieldType; }
    public String getFieldResult() { return fieldResult; }
    public void setFieldResult(String fieldResult) { this.fieldResult = fieldResult; }
    public boolean isDirty() { return dirty; }
    public void setDirty(boolean dirty) { this.dirty = dirty; }
    public boolean isLocked() { return locked; }
    public void setLocked(boolean locked) { this.locked = locked; }
    public Map<String, String> getParameters() { return parameters; }
    public void setParameters(Map<String, String> parameters) { this.parameters = parameters; }
    public String getMergeFieldName() { return mergeFieldName; }
    public void setMergeFieldName(String mergeFieldName) { this.mergeFieldName = mergeFieldName; }
    public String getReferenceBookmark() { return referenceBookmark; }
    public void setReferenceBookmark(String referenceBookmark) { this.referenceBookmark = referenceBookmark; }
    public Boolean getRefRelativePosition() { return refRelativePosition; }
    public void setRefRelativePosition(Boolean refRelativePosition) { this.refRelativePosition = refRelativePosition; }
    public String getSeqIdentifier() { return seqIdentifier; }
    public void setSeqIdentifier(String seqIdentifier) { this.seqIdentifier = seqIdentifier; }
    public String getSeqFormat() { return seqFormat; }
    public void setSeqFormat(String seqFormat) { this.seqFormat = seqFormat; }
    public Integer getSeqStartValue() { return seqStartValue; }
    public void setSeqStartValue(Integer seqStartValue) { this.seqStartValue = seqStartValue; }
    public String getStyleRefStyle() { return styleRefStyle; }
    public void setStyleRefStyle(String styleRefStyle) { this.styleRefStyle = styleRefStyle; }
    public Boolean getStyleRefSearchFromBottom() { return styleRefSearchFromBottom; }
    public void setStyleRefSearchFromBottom(Boolean styleRefSearchFromBottom) { this.styleRefSearchFromBottom = styleRefSearchFromBottom; }
    public String getIfExpression() { return ifExpression; }
    public void setIfExpression(String ifExpression) { this.ifExpression = ifExpression; }
    public String getIfTrueText() { return ifTrueText; }
    public void setIfTrueText(String ifTrueText) { this.ifTrueText = ifTrueText; }
    public String getIfFalseText() { return ifFalseText; }
    public void setIfFalseText(String ifFalseText) { this.ifFalseText = ifFalseText; }
    public String getDocPropertyName() { return docPropertyName; }
    public void setDocPropertyName(String docPropertyName) { this.docPropertyName = docPropertyName; }
    public Map<String, Object> getAdditionalProperties() { return additionalProperties; }
    public void setAdditionalProperties(Map<String, Object> additionalProperties) { this.additionalProperties = additionalProperties; }

    /**
     * 字段类型。
     */
    public enum FieldType {
        // 零参数类型
        AUTHOR("AUTHOR"),
        COMMENTS("COMMENTS"),
        DATE("DATE"),
        CREATEDATE("CREATEDATE"),
        DOCVARIABLE("DOCVARIABLE"),
        FILENAME("FILENAME"),
        FILESIZE("FILESIZE"),
        FILLIN("FILLIN"),
        INFO("INFO"),
        KEYWORDS("KEYWORDS"),
        LASTSAVEDBY("LASTSAVEDBY"),
        NUMCHARS("NUMCHARS"),
        NUMPAGES("NUMPAGES"),
        NUMWORDS("NUMWORDS"),
        PRINTDATE("PRINTDATE"),
        PRINTTIME("PRINTTIME"),
        SAVEDATE("SAVEDATE"),
        SUBJECT("SUBJECT"),
        TEMPLATE("TEMPLATE"),
        TIME("TIME"),
        TITLE("TITLE"),

        // 参数化类型
        MERGEFIELD("MERGEFIELD"),
        REF("REF"),
        PAGEREF("PAGEREF"),
        SEQ("SEQ"),
        STYLEREF("STYLEREF"),
        DOCPROPERTY("DOCPROPERTY"),
        IF("IF");

        private final String fieldName;

        FieldType(String fieldName) { this.fieldName = fieldName; }

        public String getFieldName() { return fieldName; }

        public boolean isZeroParameter() {
            return this.ordinal() <= TITLE.ordinal();
        }

        public static FieldType fromFieldName(String name) {
            if (name == null) return null;
            String upper = name.toUpperCase().trim();
            // 处理可能的格式后缀
            if (upper.contains(" ")) {
                upper = upper.split("\\s+")[0];
            }
            for (FieldType t : values()) {
                if (t.fieldName.equals(upper)) return t;
            }
            return null;
        }
    }

    /**
     * 从字段代码字符串解析字段模型。
     */
    public static FieldModel parse(String fieldCode) {
        FieldModel model = new FieldModel();
        model.setFieldCode(fieldCode);

        if (fieldCode == null || fieldCode.isEmpty()) return model;

        String upper = fieldCode.toUpperCase().trim();
        FieldType type = FieldType.fromFieldName(upper);

        if (type == null) {
            // 尝试从空格分割
            String[] parts = upper.split("\\s+", 2);
            type = FieldType.fromFieldName(parts[0]);
            if (type != null && parts.length > 1) {
                model.setParameters(parseParameters(parts[1]));
            }
        }

        if (type == FieldType.MERGEFIELD) {
            Map<String, String> params = model.getParameters();
            model.setMergeFieldName(params.getOrDefault("", fieldCode.replaceAll("MERGEFIELD\\s+", "")));
        } else if (type == FieldType.REF || type == FieldType.PAGEREF) {
            model.setReferenceBookmark(model.getParameters().getOrDefault("", ""));
        } else if (type == FieldType.SEQ) {
            model.setSeqIdentifier(model.getParameters().getOrDefault("", ""));
        } else if (type == FieldType.STYLEREF) {
            model.setStyleRefStyle(model.getParameters().getOrDefault("", ""));
        } else if (type == FieldType.DOCPROPERTY) {
            model.setDocPropertyName(model.getParameters().getOrDefault("", ""));
        }

        model.setFieldType(type != null ? type : FieldType.AUTHOR);
        return model;
    }

    private static Map<String, String> parseParameters(String paramStr) {
        Map<String, String> params = new LinkedHashMap<>();
        if (paramStr == null || paramStr.isEmpty()) return params;

        // 简单的参数解析
        String[] parts = paramStr.split("\\s+");
        if (parts.length > 0) {
            String firstKey = "";
            StringBuilder firstValue = new StringBuilder();
            for (String part : parts) {
                if (part.startsWith("\\")) {
                    if (firstValue.length() > 0) {
                        params.put(firstKey, firstValue.toString().trim());
                    }
                    firstKey = part;
                    firstValue = new StringBuilder();
                } else {
                    if (firstValue.length() > 0) firstValue.append(" ");
                    firstValue.append(part);
                }
            }
            params.put(firstKey, firstValue.toString().trim());
        }
        return params;
    }
}