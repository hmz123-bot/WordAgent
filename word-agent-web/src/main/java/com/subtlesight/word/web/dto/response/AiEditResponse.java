package com.subtlesight.word.web.dto.response;

import java.util.List;

/**
 * AI 编辑响应。
 */
public class AiEditResponse {

    /** 摘要说明 */
    private String summary;

    /** 建议的变更列表 */
    private List<Suggestion> suggestions;

    /** 原始 LLM 响应（调试用） */
    private String rawResponse;

    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }

    public List<Suggestion> getSuggestions() { return suggestions; }
    public void setSuggestions(List<Suggestion> suggestions) { this.suggestions = suggestions; }

    public String getRawResponse() { return rawResponse; }
    public void setRawResponse(String rawResponse) { this.rawResponse = rawResponse; }

    /**
     * 单条建议变更。
     */
    public static class Suggestion {
        private String nodeId;
        private String originalText;
        private String suggestedText;
        private String description;
        private String operation;

        public String getNodeId() { return nodeId; }
        public void setNodeId(String nodeId) { this.nodeId = nodeId; }

        public String getOriginalText() { return originalText; }
        public void setOriginalText(String originalText) { this.originalText = originalText; }

        public String getSuggestedText() { return suggestedText; }
        public void setSuggestedText(String suggestedText) { this.suggestedText = suggestedText; }

        public String getDescription() { return description; }
        public void setDescription(String description) { this.description = description; }

        public String getOperation() { return operation; }
        public void setOperation(String operation) { this.operation = operation; }
    }
}