package com.subtlesight.word.web.entity;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 文档变更集 JPA 实体，对应 DocumentChangeSet 领域模型。
 */
@Entity
@Table(name = "document_changeset")
public class DocumentChangesetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "changeset_id", unique = true, nullable = false, length = 64)
    private String changesetId;

    @Column(name = "document_id", length = 64)
    private String documentId;

    @Column(name = "expected_version")
    private Integer expectedVersion;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(length = 512)
    private String summary;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(name = "author_type", length = 32)
    private String authorType;

    @Column(name = "review_status", length = 32)
    private String reviewStatus;

    @Column(name = "rejection_reason", length = 1024)
    private String rejectionReason;

    @Column(name = "failure_message", length = 1024)
    private String failureMessage;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @OneToMany(mappedBy = "changeset", cascade = CascadeType.ALL,
               orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("id ASC")
    private List<DocumentChangeEntity> changes = new ArrayList<>();

    public DocumentChangesetEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChangesetId() { return changesetId; }
    public void setChangesetId(String changesetId) { this.changesetId = changesetId; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public Integer getExpectedVersion() { return expectedVersion; }
    public void setExpectedVersion(Integer expectedVersion) { this.expectedVersion = expectedVersion; }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }

    public String getReviewStatus() { return reviewStatus; }
    public void setReviewStatus(String reviewStatus) { this.reviewStatus = reviewStatus; }

    public String getRejectionReason() { return rejectionReason; }
    public void setRejectionReason(String rejectionReason) { this.rejectionReason = rejectionReason; }

    public String getFailureMessage() { return failureMessage; }
    public void setFailureMessage(String failureMessage) { this.failureMessage = failureMessage; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public List<DocumentChangeEntity> getChanges() { return changes; }
    public void setChanges(List<DocumentChangeEntity> changes) {
        this.changes = changes != null ? changes : new ArrayList<>();
    }
}
