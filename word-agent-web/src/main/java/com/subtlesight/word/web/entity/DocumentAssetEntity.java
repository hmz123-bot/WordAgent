package com.subtlesight.word.web.entity;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * 文档资产 JPA 实体，对应 WordDocumentAsset 领域模型。
 */
@Entity
@Table(name = "document_asset")
public class DocumentAssetEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", unique = true, nullable = false, length = 64)
    private String documentId;

    @Column(name = "file_name", length = 512)
    private String fileName;

    @Column(name = "file_size")
    private Long fileSize;

    @Column(name = "file_hash", length = 128)
    private String fileHash;

    @Column(name = "current_version")
    private Integer currentVersion;

    @Column(name = "storage_path", length = 1024)
    private String storagePath;

    @Column(length = 32)
    private String status;

    @Column(name = "owner_id", length = 64)
    private String ownerId;

    @Column(name = "workspace_id", length = 64)
    private String workspaceId;

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    public DocumentAssetEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getFileName() { return fileName; }
    public void setFileName(String fileName) { this.fileName = fileName; }

    public Long getFileSize() { return fileSize; }
    public void setFileSize(Long fileSize) { this.fileSize = fileSize; }

    public String getFileHash() { return fileHash; }
    public void setFileHash(String fileHash) { this.fileHash = fileHash; }

    public Integer getCurrentVersion() { return currentVersion; }
    public void setCurrentVersion(Integer currentVersion) { this.currentVersion = currentVersion; }

    public String getStoragePath() { return storagePath; }
    public void setStoragePath(String storagePath) { this.storagePath = storagePath; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getOwnerId() { return ownerId; }
    public void setOwnerId(String ownerId) { this.ownerId = ownerId; }

    public String getWorkspaceId() { return workspaceId; }
    public void setWorkspaceId(String workspaceId) { this.workspaceId = workspaceId; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
}
