package com.subtlesight.word.model;

import com.subtlesight.word.model.enums.ReviewStatus;

import java.time.Instant;
import java.util.*;

/**
 * 文档变更集，对应 PRD 7.1 节 DocumentChangeSet 对象。
 * <p>
 * AI 或用户提出的原子修改集合，包含基线版本、操作、审阅状态和幂等键。
 * 默认原子提交；任一操作失败时整体回滚。
 * </p>
 */
public class DocumentChangeSet {

    private final String changeSetId;
    private String documentId;
    private int expectedVersion;
    private String idempotencyKey;
    private String summary;
    private String authorId;
    private String authorType;
    private List<Change> changes;
    private ReviewStatus reviewStatus;
    private Instant createdAt;
    private Instant updatedAt;
    private String rejectionReason;
    private String failureMessage;

    public DocumentChangeSet() {
        this.changeSetId = UUID.randomUUID().toString();
        this.changes = new ArrayList<>();
        this.reviewStatus = ReviewStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public DocumentChangeSet(String changeSetId) {
        this.changeSetId = changeSetId;
        this.changes = new ArrayList<>();
        this.reviewStatus = ReviewStatus.DRAFT;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public String getChangeSetId() {
        return changeSetId;
    }

    public String getDocumentId() {
        return documentId;
    }

    public void setDocumentId(String documentId) {
        this.documentId = documentId;
    }

    public int getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(int expectedVersion) {
        this.expectedVersion = expectedVersion;
    }

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
    }

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public String getAuthorId() {
        return authorId;
    }

    public void setAuthorId(String authorId) {
        this.authorId = authorId;
    }

    public String getAuthorType() {
        return authorType;
    }

    public void setAuthorType(String authorType) {
        this.authorType = authorType;
    }

    public List<Change> getChanges() {
        return changes;
    }

    public void setChanges(List<Change> changes) {
        this.changes = changes != null ? changes : new ArrayList<>();
    }

    public void addChange(Change change) {
        this.changes.add(change);
    }

    public ReviewStatus getReviewStatus() {
        return reviewStatus;
    }

    public void setReviewStatus(ReviewStatus reviewStatus) {
        this.reviewStatus = reviewStatus;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(Instant updatedAt) {
        this.updatedAt = updatedAt;
    }

    public String getRejectionReason() {
        return rejectionReason;
    }

    public void setRejectionReason(String rejectionReason) {
        this.rejectionReason = rejectionReason;
    }

    public String getFailureMessage() {
        return failureMessage;
    }

    public void setFailureMessage(String failureMessage) {
        this.failureMessage = failureMessage;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DocumentChangeSet that = (DocumentChangeSet) o;
        return Objects.equals(changeSetId, that.changeSetId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(changeSetId);
    }

    @Override
    public String toString() {
        return "DocumentChangeSet{" +
                "changeSetId='" + changeSetId + '\'' +
                ", documentId='" + documentId + '\'' +
                ", expectedVersion=" + expectedVersion +
                ", reviewStatus=" + reviewStatus +
                ", changes=" + changes.size() +
                '}';
    }

    /**
     * 单次原子变更操作。
     */
    public static class Change {
        private String changeId;
        private com.subtlesight.word.model.enums.ChangeOperation operation;
        private String targetNodeId;
        private String targetNodeType;
        private Map<String, Object> oldValue;
        private Map<String, Object> newValue;
        private String position;
        private String context;
        private DocumentNode content;

        public Change() {
            this.changeId = UUID.randomUUID().toString();
        }

        public String getChangeId() {
            return changeId;
        }

        public void setChangeId(String changeId) {
            this.changeId = changeId;
        }

        public com.subtlesight.word.model.enums.ChangeOperation getOperation() {
            return operation;
        }

        public void setOperation(com.subtlesight.word.model.enums.ChangeOperation operation) {
            this.operation = operation;
        }

        public String getTargetNodeId() {
            return targetNodeId;
        }

        public void setTargetNodeId(String targetNodeId) {
            this.targetNodeId = targetNodeId;
        }

        public String getTargetNodeType() {
            return targetNodeType;
        }

        public void setTargetNodeType(String targetNodeType) {
            this.targetNodeType = targetNodeType;
        }

        public Map<String, Object> getOldValue() {
            return oldValue;
        }

        public void setOldValue(Map<String, Object> oldValue) {
            this.oldValue = oldValue;
        }

        public Map<String, Object> getNewValue() {
            return newValue;
        }

        public void setNewValue(Map<String, Object> newValue) {
            this.newValue = newValue;
        }

        public String getPosition() {
            return position;
        }

        public void setPosition(String position) {
            this.position = position;
        }

        public String getContext() {
            return context;
        }

        public void setContext(String context) {
            this.context = context;
        }

        public DocumentNode getContent() {
            return content;
        }

        public void setContent(DocumentNode content) {
            this.content = content;
        }
    }
}