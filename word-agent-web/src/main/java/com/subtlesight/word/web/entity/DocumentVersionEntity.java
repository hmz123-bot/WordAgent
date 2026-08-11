package com.subtlesight.word.web.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 文档版本历史 JPA 实体。
 */
@Entity
@Table(name = "document_version")
public class DocumentVersionEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", length = 64)
    private String documentId;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Column(name = "changeset_id", length = 64)
    private String changesetId;

    @Column(length = 512)
    private String summary;

    @Column(name = "author_id", length = 64)
    private String authorId;

    @Column(name = "author_type", length = 32)
    private String authorType;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    @Column(name = "created_at")
    private Instant createdAt;

    public DocumentVersionEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public String getChangesetId() { return changesetId; }
    public void setChangesetId(String changesetId) { this.changesetId = changesetId; }

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public String getAuthorId() { return authorId; }
    public void setAuthorId(String authorId) { this.authorId = authorId; }

    public String getAuthorType() { return authorType; }
    public void setAuthorType(String authorType) { this.authorType = authorType; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
