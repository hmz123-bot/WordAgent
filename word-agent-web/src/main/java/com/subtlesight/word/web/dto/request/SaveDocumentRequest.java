package com.subtlesight.word.web.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * 保存文档请求（编辑完成后一键保存，自动创建变更集并提交合并）。
 */
public class SaveDocumentRequest {

    @Size(max = 500, message = "摘要长度不能超过 500 字符")
    private String summary;

    @NotEmpty(message = "至少需要一个变更")
    @Valid
    private List<NodeUpdate> changes;

    public String getSummary() {
        return summary;
    }

    public void setSummary(String summary) {
        this.summary = summary;
    }

    public List<NodeUpdate> getChanges() {
        return changes;
    }

    public void setChanges(List<NodeUpdate> changes) {
        this.changes = changes;
    }

    /**
     * 单节点文本更新。
     */
    public static class NodeUpdate {

        @NotBlank(message = "节点 ID 不能为空")
        private String nodeId;

        private String text;

        public String getNodeId() {
            return nodeId;
        }

        public void setNodeId(String nodeId) {
            this.nodeId = nodeId;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }
}
