package com.subtlesight.word.web.dto.request;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

/**
 * AI 编辑请求。
 */
public class AiEditRequest {

    @NotBlank(message = "文档 ID 不能为空")
    private String documentId;

    @NotBlank(message = "编辑指令不能为空")
    private String instruction;

    /** 可选：限定操作的节点 ID 列表 */
    private List<String> nodeIds;

    /** 文档上下文（当前节点树文本快照） */
    private String documentContext;

    public String getDocumentId() { return documentId; }
    public void setDocumentId(String documentId) { this.documentId = documentId; }

    public String getInstruction() { return instruction; }
    public void setInstruction(String instruction) { this.instruction = instruction; }

    public List<String> getNodeIds() { return nodeIds; }
    public void setNodeIds(List<String> nodeIds) { this.nodeIds = nodeIds; }

    public String getDocumentContext() { return documentContext; }
    public void setDocumentContext(String documentContext) { this.documentContext = documentContext; }
}