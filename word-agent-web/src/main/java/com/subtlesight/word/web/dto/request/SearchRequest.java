package com.subtlesight.word.web.dto.request;

import java.util.List;
import java.util.Map;

/**
 * 搜索请求 DTO。
 */
public class SearchRequest {

    /** 搜索查询文本 */
    private String query;

    /** 正则表达式（与 query 二选一） */
    private String pattern;

    /** 搜索模式：text / regex / type / format */
    private String mode = "text";

    /** 是否大小写敏感 */
    private boolean caseSensitive = false;

    /** 是否全词匹配 */
    private boolean wholeWord = false;

    /** 最大结果数 */
    private int maxResults = 100;

    /** 上下文窗口字符数 */
    private int contextChars = 50;

    /** 按类型搜索时的节点类型列表 */
    private List<String> nodeTypes;

    /** 按格式搜索时的格式条件 */
    private Map<String, Object> formatQuery;

    /** 查找替换时的替换文本（仅 findAndReplace 时使用） */
    private String replacement;

    // ====== Getters & Setters ======

    public String getQuery() { return query; }
    public void setQuery(String query) { this.query = query; }

    public String getPattern() { return pattern; }
    public void setPattern(String pattern) { this.pattern = pattern; }

    public String getMode() { return mode; }
    public void setMode(String mode) { this.mode = mode; }

    public boolean isCaseSensitive() { return caseSensitive; }
    public void setCaseSensitive(boolean caseSensitive) { this.caseSensitive = caseSensitive; }

    public boolean isWholeWord() { return wholeWord; }
    public void setWholeWord(boolean wholeWord) { this.wholeWord = wholeWord; }

    public int getMaxResults() { return maxResults; }
    public void setMaxResults(int maxResults) { this.maxResults = maxResults; }

    public int getContextChars() { return contextChars; }
    public void setContextChars(int contextChars) { this.contextChars = contextChars; }

    public List<String> getNodeTypes() { return nodeTypes; }
    public void setNodeTypes(List<String> nodeTypes) { this.nodeTypes = nodeTypes; }

    public Map<String, Object> getFormatQuery() { return formatQuery; }
    public void setFormatQuery(Map<String, Object> formatQuery) { this.formatQuery = formatQuery; }

    public String getReplacement() { return replacement; }
    public void setReplacement(String replacement) { this.replacement = replacement; }
}