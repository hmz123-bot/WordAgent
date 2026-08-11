package com.subtlesight.word.service.search;

import com.subtlesight.word.model.enums.NodeType;

import java.util.List;
import java.util.Map;

/**
 * 文档搜索/文本匹配服务接口。
 * <p>
 * 支持全文搜索、正则搜索、按节点类型过滤、查找替换等功能。
 * </p>
 */
public interface DocumentSearchService {

    /**
     * 全文搜索：在文档中搜索指定文本。
     *
     * @param documentId 文档 ID
     * @param query      搜索文本
     * @param options    搜索选项（大小写敏感、全词匹配等）
     * @return 搜索结果
     */
    SearchResult search(String documentId, String query, SearchOptions options);

    /**
     * 正则搜索：使用正则表达式匹配文档内容。
     *
     * @param documentId 文档 ID
     * @param pattern    正则表达式
     * @param options    搜索选项
     * @return 搜索结果
     */
    SearchResult searchByRegex(String documentId, String pattern, SearchOptions options);

    /**
     * 按节点类型过滤搜索。
     *
     * @param documentId 文档 ID
     * @param query      搜索文本（可为 null 或空，表示不过滤文本）
     * @param nodeTypes  要搜索的节点类型列表
     * @param options    搜索选项
     * @return 搜索结果
     */
    SearchResult searchByType(String documentId, String query, List<NodeType> nodeTypes, SearchOptions options);

    /**
     * 按格式属性搜索（如字体、颜色、字号等）。
     *
     * @param documentId   文档 ID
     * @param formatQuery  格式条件键值对
     * @param options      搜索选项
     * @return 搜索结果
     */
    SearchResult searchByFormat(String documentId, Map<String, Object> formatQuery, SearchOptions options);

    /**
     * 查找并替换：在文档中查找指定文本并替换。
     *
     * @param documentId 文档 ID
     * @param oldText    要查找的文本
     * @param newText    替换文本
     * @param options    搜索选项
     * @return 替换统计
     */
    SearchResult.ReplaceSummary findAndReplace(String documentId, String oldText, String newText, SearchOptions options);

    /**
     * 搜索选项。
     */
    class SearchOptions {
        private boolean caseSensitive = false;
        private boolean wholeWord = false;
        private boolean includeFootnotes = true;
        private boolean includeHeaders = true;
        private boolean includeComments = false;
        private int maxResults = 100;
        private int contextChars = 50;

        public SearchOptions() {}

        public SearchOptions(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        public boolean isCaseSensitive() { return caseSensitive; }
        public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

        public boolean isWholeWord() { return wholeWord; }
        public void setWholeWord(boolean wholeWord) { this.wholeWord = wholeWord; }

        public boolean isIncludeFootnotes() { return includeFootnotes; }
        public void setIncludeFootnotes(boolean includeFootnotes) { this.includeFootnotes = includeFootnotes; }

        public boolean isIncludeHeaders() { return includeHeaders; }
        public void setIncludeHeaders(boolean includeHeaders) { this.includeHeaders = includeHeaders; }

        public boolean isIncludeComments() { return includeComments; }
        public void setIncludeComments(boolean includeComments) { this.includeComments = includeComments; }

        public int getMaxResults() { return maxResults; }
        public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

        public int getContextChars() { return contextChars; }
        public void setContextChars(int contextChars) { this.contextChars = contextChars; }
    }
}