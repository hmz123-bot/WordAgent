package com.subtlesight.word.web.entity;

import jakarta.persistence.*;

/**
 * 文档二进制内容 JPA 实体（原始 .docx 文件数据 + 版本快照）。
 */
@Entity
@Table(name = "document_content")
public class DocumentContentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "document_id", length = 64)
    private String documentId;

    /**
     * 内容类型：RAW（当前原始内容）、VERSION（历史版本快照）
     */
    @Column(name = "content_type", length = 32)
    private String contentType;

    @Column(name = "version_number")
    private Integer versionNumber;

    @Lob
    @Column(name = "content", columnDefinition = "MEDIUMBLOB")
    private byte[] content;

    public DocumentContentEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getContentType() { return contentType; }
    public void setContentType(String contentType) { this.contentType = contentType; }

    public Integer getVersionNumber() { return versionNumber; }
    public void setVersionNumber(Integer versionNumber) { this.versionNumber = versionNumber; }

    public byte[] getContent() { return content; }
    public void setContent(byte[] content) { this.content = content; }
}
