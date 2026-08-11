package com.subtlesight.word.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import com.subtlesight.word.model.DocumentChangeSet.Change;

import java.util.List;
import java.util.Map;

/**
 * 创建变更集请求。
 */
public class CreateChangeSetRequest {

    @NotBlank(message = "文档 ID 不能为空")
    private String documentId;

    @Min(value = 1, message = "预期版本号必须大于 0")
    private int expectedVersion = 1;

    private String idempotencyKey;

    @NotBlank(message = "摘要不能为空")
    @Size(max = 500, message = "摘要长度不能超过 500 字符")
    private String summary;

    private String authorId;
    private String authorType;

    @NotEmpty(message = "至少需要一个变更操作")
    @Valid
    private List<ChangeRequest> changes;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public int getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(int expectedVersion) { this.expectedVersion = expectedVersion; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }

    public List<ChangeRequest> getChanges() { return changes; }
    public void setChanges(List<ChangeRequest> changes) { this.changes = changes; }

    /**
     * 单次变更操作 DTO。
     */
    public static class ChangeRequest {
        @NotBlank(message = "操作类型不能为空")
        private String operation;

        @NotBlank(message = "目标节点 ID 不能为空")
        private String targetNodeId;

        private String targetNodeType;
        private Map<String, Object> oldValue;
        private Map<String, Object> newValue;
        private String position;
        private String context;

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }

        public String getTargetNodeId() { return targetNodeId; }
        public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

        public String getTargetNodeType() { return targetNodeType; }
        public void setTargetNodeType(String targetNodeType) { this.targetNodeType = targetNodeType; }

        public Map<String, Object> getOldValue() { return oldValue; }
        public void setOldValue(Map<String, Object> oldValue) { this.oldValue = oldValue; }

        public Map<String, Object> getNewValue() { return newValue; }
        public void setNewValue(Map<String, Object> newValue) { this.newValue = newValue; }

        public String getPosition() { return position; }
        public void setPosition(String position) { this.position = position; }

        public String getContext() { return context; }
        public void setContext(String context) { this.context = context; }
    }
}