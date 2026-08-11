package com.subtlesight.word.model;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Word 文档资产，代表系统中一个 .docx 文件实例。
 * <p>
 * 对应 PRD 7.1 节 WordDocumentAsset 对象。
 * 原始 .docx 是唯一事实来源。
 * </p>
 */
public class WordDocumentAsset {

    private final String documentId;
    private String fileName;
    private long fileSize;
    private String fileHash;
    private int currentVersion;
    private String storagePath;
    private Instant createdAt;
    private Instant updatedAt;
    private DocumentStatus status;
    private String ownerId;
    private String workspaceId;

    public WordDocumentAsset() {
        this.documentId = UUID.randomUUID().toString();
        this.currentVersion = 1;
        this.status = DocumentStatus.IMPORTING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    public WordDocumentAsset(String documentId) {
        this.documentId = documentId;
        this.currentVersion = 1;
        this.status = DocumentStatus.IMPORTING;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    @JsonProperty("documentId")
    public String getDocumentId() {
        return documentId;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public long getFileSize() {
        return fileSize;
    }

    public void setFileSize(long fileSize) {
        this.fileSize = fileSize;
    }

    public String getFileHash() {
        return fileHash;
    }

    public void setFileHash(String fileHash) {
        this.fileHash = fileHash;
    }

    public int getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(int currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getStoragePath() {
        return storagePath;
    }

    public void setStoragePath(String storagePath) {
        this.storagePath = storagePath;
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

    public DocumentStatus getStatus() {
        return status;
    }

    public void setStatus(DocumentStatus status) {
        this.status = status;
    }

    public String getOwnerId() {
        return ownerId;
    }

    public void setOwnerId(String ownerId) {
        this.ownerId = ownerId;
    }

    public String getWorkspaceId() {
        return workspaceId;
    }

    public void setWorkspaceId(String workspaceId) {
        this.workspaceId = workspaceId;
    }

    public void bumpVersion() {
        this.currentVersion++;
        this.updatedAt = Instant.now();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        WordDocumentAsset that = (WordDocumentAsset) o;
        return Objects.equals(documentId, that.documentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(documentId);
    }

    @Override
    public String toString() {
        return "WordDocumentAsset{" +
                "documentId='" + documentId + '\'' +
                ", fileName='" + fileName + '\'' +
                ", currentVersion=" + currentVersion +
                ", status=" + status +
                '}';
    }

    /** 文档生命周期状态 */
    public enum DocumentStatus {
        /** 导入中（过渡态） */
        IMPORTING,
        /** 草稿（过渡态） */
        DRAFT,
        /** 已发布 */
        READY,
        /** 编辑中 */
        EDITING,
        /** 审核中 */
        REVIEWING,
        /** 导出中（过渡态） */
        EXPORTING,
        /** 错误 */
        ERROR,
        /** 已归档 */
        ARCHIVED
    }
}