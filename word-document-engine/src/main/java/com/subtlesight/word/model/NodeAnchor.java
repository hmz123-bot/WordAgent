package com.subtlesight.word.model;

import java.util.Objects;

/**
 * 稳定定位信息，对应 PRD 7.1 节 NodeAnchor 对象。
 * <p>
 * 用于将 DocumentNode 映射到 OOXML 部件中的具体位置。
 * 重载文档时通过此映射重建并校验节点。
 * </p>
 */
public class NodeAnchor {

    /** 关联的文档节点 ID */
    private String nodeId;

    /** OOXML 部件路径，如 "word/document.xml" */
    private String partPath;

    /** 结构路径，如 "/w:document/w:body/w:p[3]" */
    private String structuralPath;

    /** 创建此锚点时的文档版本号 */
    private int version;

    /** 额外定位信息，如关系 ID、内容控件标签等 */
    private String context;

    public NodeAnchor() {
    }

    public NodeAnchor(String nodeId, String partPath, String structuralPath, int version) {
        this.nodeId = nodeId;
        this.partPath = partPath;
        this.structuralPath = structuralPath;
        this.version = version;
    }

    public String getNodeId() {
        return nodeId;
    }

    public void setNodeId(String nodeId) {
        this.nodeId = nodeId;
    }

    public String getPartPath() {
        return partPath;
    }

    public void setPartPath(String partPath) {
        this.partPath = partPath;
    }

    public String getStructuralPath() {
        return structuralPath;
    }

    public void setStructuralPath(String structuralPath) {
        this.structuralPath = structuralPath;
    }

    public int getVersion() {
        return version;
    }

    public void setVersion(int version) {
        this.version = version;
    }

    public String getContext() {
        return context;
    }

    public void setContext(String context) {
        this.context = context;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        NodeAnchor that = (NodeAnchor) o;
        return version == that.version
                && Objects.equals(nodeId, that.nodeId)
                && Objects.equals(partPath, that.partPath)
                && Objects.equals(structuralPath, that.structuralPath);
    }

    @Override
    public int hashCode() {
        return Objects.hash(nodeId, partPath, structuralPath, version);
    }

    @Override
    public String toString() {
        return "NodeAnchor{" +
                "nodeId='" + nodeId + '\'' +
                ", partPath='" + partPath + '\'' +
                ", structuralPath='" + structuralPath + '\'' +
                ", version=" + version +
                '}';
    }
}