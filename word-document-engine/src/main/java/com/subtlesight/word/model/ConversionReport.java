package com.subtlesight.word.model;

import com.subtlesight.word.model.enums.ErrorCode;
import com.subtlesight.word.model.enums.SupportLevel;

import java.time.Instant;
import java.util.*;

/**
 * 导入/导出转换报告，对应 PRD 7.1 节 ConversionReport 对象。
 * <p>
 * 记录文档转换过程中的支持、降级、只读元素和失败原因。
 * </p>
 */
public class ConversionReport {

    private final String reportId;
    private String documentId;
    private ConversionType conversionType;
    private boolean success;
    private long totalElements;
    private long editableCount;
    private long readOnlyCount;
    private long degradedCount;
    private long unsupportedCount;
    private List<Item> items;
    private List<Issue> issues;
    private Instant createdAt;

    public ConversionReport() {
        this.reportId = UUID.randomUUID().toString();
        this.items = new ArrayList<>();
        this.issues = new ArrayList<>();
        this.createdAt = Instant.now();
    }

    public ConversionReport(ConversionType conversionType) {
        this();
        this.conversionType = conversionType;
    }

    public String getReportId() {
        return reportId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public ConversionType getConversionType() {
        return conversionType;
    }

    public void setConversionType(ConversionType conversionType) {
        this.conversionType = conversionType;
    }

    public boolean isSuccess() {
        return success;
    }

    public void setSuccess(boolean success) {
        this.success = success;
    }

    public long getTotalElements() {
        return totalElements;
    }

    public void setTotalElements(long totalElements) {
        this.totalElements = totalElements;
    }

    public long getEditableCount() {
        return editableCount;
    }

    public void setEditableCount(long editableCount) {
        this.editableCount = editableCount;
    }

    public long getReadOnlyCount() {
        return readOnlyCount;
    }

    public void setReadOnlyCount(long readOnlyCount) {
        this.readOnlyCount = readOnlyCount;
    }

    public long getDegradedCount() {
        return degradedCount;
    }

    public void setDegradedCount(long degradedCount) {
        this.degradedCount = degradedCount;
    }

    public long getUnsupportedCount() {
        return unsupportedCount;
    }

    public void setUnsupportedCount(long unsupportedCount) {
        this.unsupportedCount = unsupportedCount;
    }

    public List<Item> getItems() {
        return items;
    }

    public void setItems(List<Item> items) {
        this.items = items != null ? items : new ArrayList<>();
    }

    public void addItem(Item item) {
        this.items.add(item);
    }

    public List<Issue> getIssues() {
        return issues;
    }

    public void setIssues(List<Issue> issues) {
        this.issues = issues != null ? issues : new ArrayList<>();
    }

    public void addIssue(Issue issue) {
        this.issues.add(issue);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    /**
     * 单个元素的支持级别记录。
     */
    public static class Item {
        private String elementType;
        private String elementName;
        private SupportLevel supportLevel;
        private String description;
        private String nodeId;

        public Item() {
        }

        public Item(String elementType, String elementName, SupportLevel supportLevel) {
            this.elementType = elementType;
            this.elementName = elementName;
            this.supportLevel = supportLevel;
        }

        public String getElementType() {
            return elementType;
        }

        public void setElementType(String elementType) {
            this.elementType = elementType;
        }

        public String getElementName() {
            return elementName;
        }

        public void setElementName(String elementName) {
            this.elementName = elementName;
        }

        public SupportLevel getSupportLevel() {
            return supportLevel;
        }

        public void setSupportLevel(SupportLevel supportLevel) {
            this.supportLevel = supportLevel;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }
    }

    /**
     * 转换过程中发现的问题/错误。
     */
    public static class Issue {
        private String issueId;
        private ErrorCode errorCode;
        private String message;
        private String suggestion;
        private String elementPath;
        private Severity severity;

        public Issue() {
            this.issueId = UUID.randomUUID().toString();
        }

        public String getIssueId() {
            return issueId;
        }

        public ErrorCode getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(ErrorCode errorCode) {
            this.errorCode = errorCode;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }

        public String getSuggestion() {
            return suggestion;
        }

        public void setSuggestion(String suggestion) {
            this.suggestion = suggestion;
        }

        public String getElementPath() {
            return elementPath;
        }

        public void setElementPath(String elementPath) {
            this.elementPath = elementPath;
        }

        public Severity getSeverity() {
            return severity;
        }

        public void setSeverity(Severity severity) {
            this.severity = severity;
        }

        public enum Severity {
            INFO,
            WARNING,
            ERROR
        }
    }

    public enum ConversionType {
        IMPORT,
        EXPORT
    }
}