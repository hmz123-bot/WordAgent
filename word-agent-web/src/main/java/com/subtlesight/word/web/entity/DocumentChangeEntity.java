package com.subtlesight.word.web.entity;

import jakarta.persistence.*;

/**
 * 文档变更操作 JPA 实体，对应 DocumentChangeSet.Change。
 */
@Entity
@Table(name = "document_change")
public class DocumentChangeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "change_id", unique = true, length = 64)
    private String changeId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "changeset_id", referencedColumnName = "changeset_id")
    private DocumentChangesetEntity changeset;

    @Column(length = 32)
    private String operation;

    @Column(name = "target_node_id", length = 64)
    private String targetNodeId;

    @Column(name = "target_node_type", length = 32)
    private String targetNodeType;

    @Column(length = 32)
    private String position;

    @Column(columnDefinition = "TEXT")
    private String context;

    @Column(name = "old_value_json", columnDefinition = "TEXT")
    private String oldValueJson;

    @Column(name = "new_value_json", columnDefinition = "TEXT")
    private String newValueJson;

    @Column(name = "content_json", columnDefinition = "TEXT")
    private String contentJson;

    public DocumentChangeEntity() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getChangeId() { return changeId; }
    public void setChangeId(String changeId) { this.changeId = changeId; }

    public DocumentChangesetEntity getChangeset() { return changeset; }
    public void setChangeset(DocumentChangesetEntity changeset) { this.changeset = changeset; }

    public String getOperation() { return operation; }
    public void setOperation(String operation) { this.operation = operation; }

    public String getTargetNodeId() { return targetNodeId; }
    public void setTargetNodeId(String targetNodeId) { this.targetNodeId = targetNodeId; }

    public String getTargetNodeType() { return targetNodeType; }
    public void setTargetNodeType(String targetNodeType) { this.targetNodeType = targetNodeType; }

    public String getPosition() { return position; }
    public void setPosition(String position) { this.position = position; }

    public String getContext() { return context; }
    public void setContext(String context) { this.context = context; }

    public String getOldValueJson() { return oldValueJson; }
    public void setOldValueJson(String oldValueJson) { this.oldValueJson = oldValueJson; }

    public String getNewValueJson() { return newValueJson; }
    public void setNewValueJson(String newValueJson) { this.newValueJson = newValueJson; }

    public String getContentJson() { return contentJson; }
    public void setContentJson(String contentJson) { this.contentJson = contentJson; }
}
