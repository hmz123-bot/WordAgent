package com.subtlesight.word.service.search;

import com.subtlesight.word.model.enums.NodeType;

import java.util.*;

/**
 * 搜索结果，包含匹配的文档节点信息和上下文。
 */
public class SearchResult {

    private final String documentId;
    private final String query;
    private final long totalMatches;
    private final long searchTimeMs;
    private final List<Match> matches;

    public SearchResult(String documentId, String query, long totalMatches, long searchTimeMs, List<Match> matches) {
        this.documentId = documentId;
        this.query = query;
        this.totalMatches = totalMatches;
        this.searchTimeMs = searchTimeMs;
        this.matches = matches != null ? matches : Collections.emptyList();
    }

    public String getDocumentId() { return documentId; }
    public String getQuery() { return query; }
    public long getTotalMatches() { return totalMatches; }
    public long getSearchTimeMs() { return searchTimeMs; }
    public List<Match> getMatches() { return matches; }

    /**
     * 单次匹配结果。
     */
    public static class Match {
        private final String nodeId;
        private final NodeType nodeType;
        private final String textContent;
        private final String context;
        private final int matchStart;
        private final int matchEnd;
        private final Map<String, Object> attributes;

        public Match(String nodeId, NodeType nodeType, String textContent,
                     String context, int matchStart, int matchEnd) {
            this.nodeId = nodeId;
            this.nodeType = nodeType;
            this.textContent = textContent;
            this.context = context;
            this.matchStart = matchStart;
            this.matchEnd = matchEnd;
            this.attributes = new HashMap<>();
        }

        public String getNodeId() { return nodeId; }
        public NodeType getNodeType() { return nodeType; }
        public String getTextContent() { return textContent; }
        public String getContext() { return context; }
        public int getMatchStart() { return matchStart; }
        public int getMatchEnd() { return matchEnd; }
        public Map<String, Object> getAttributes() { return attributes; }

        public void setAttribute(String key, Object value) {
            this.attributes.put(key, value);
        }
    }

    /**
     * 替换统计。
     */
    public static class ReplaceSummary {
        private final int totalMatches;
        private final int replacedCount;
        private final List<String> replacedNodeIds;

        public ReplaceSummary(int totalMatches, int replacedCount, List<String> replacedNodeIds) {
            this.totalMatches = totalMatches;
            this.replacedCount = replacedCount;
            this.replacedNodeIds = replacedNodeIds;
        }

        public int getTotalMatches() { return totalMatches; }
        public int getReplacedCount() { return replacedCount; }
        public List<String> getReplacedNodeIds() { return replacedNodeIds; }
    }
}