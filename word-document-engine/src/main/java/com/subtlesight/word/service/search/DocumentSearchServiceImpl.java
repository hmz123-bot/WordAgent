package com.subtlesight.word.service.search;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.exception.DocumentException;
import com.subtlesight.word.model.DocumentChangeSet;
import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.WordDocumentAsset;
import com.subtlesight.word.model.enums.ChangeOperation;
import com.subtlesight.word.model.enums.ErrorCode;
import com.subtlesight.word.model.enums.NodeType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 文档搜索服务实现。
 * <p>
 * 基于DocumentNode树进行内存搜索，支持全文、正则、类型过滤、格式搜索和查找替换。
 * </p>
 */
public class DocumentSearchServiceImpl implements DocumentSearchService {

    private static final Logger log = LoggerFactory.getLogger(DocumentSearchServiceImpl.class);

    private final Map<String, WordDocumentAsset> documentStore;
    private final Map<String, List<DocumentNode>> nodeTrees;
    private final Map<String, byte[]> rawContents;
    private final WordDocumentAdapter adapter;

    public DocumentSearchServiceImpl(Map<String, WordDocumentAsset> documentStore,
                                     Map<String, List<DocumentNode>> nodeTrees,
                                     Map<String, byte[]> rawContents,
                                     WordDocumentAdapter adapter) {
        this.documentStore = documentStore;
        this.nodeTrees = nodeTrees;
        this.rawContents = rawContents;
        this.adapter = adapter;
    }

    @Override
    public SearchResult search(String documentId, String query, SearchOptions options) {
        long start = System.currentTimeMillis();
        getDocument(documentId);
        List<DocumentNode> allNodes = getNodes(documentId);

        List<SearchResult.Match> matches = new ArrayList<>();
        for (DocumentNode node : allNodes) {
            if (matches.size() >= options.getMaxResults()) break;
            if (!shouldIncludeNode(node, options)) continue;

            String text = node.getText();
            if (text == null || text.isEmpty()) continue;

            String searchText = options.isCaseSensitive() ? text : text.toLowerCase();
            String searchQuery = options.isCaseSensitive() ? query : query.toLowerCase();

            if (options.isWholeWord()) {
                String regex = "\\b" + Pattern.quote(searchQuery) + "\\b";
                Matcher matcher = Pattern.compile(regex).matcher(searchText);
                while (matcher.find() && matches.size() < options.getMaxResults()) {
                    matches.add(buildMatch(node, text, matcher, options.getContextChars()));
                }
            } else {
                int idx = 0;
                while ((idx = searchText.indexOf(searchQuery, idx)) != -1 && matches.size() < options.getMaxResults()) {
                    int startIdx = Math.max(0, idx - options.getContextChars());
                    int endIdx = Math.min(text.length(), idx + query.length() + options.getContextChars());
                    String context = text.substring(startIdx, endIdx);
                    matches.add(new SearchResult.Match(
                            node.getNodeId(), node.getNodeType(), text,
                            context, idx, idx + query.length()
                    ));
                    idx += query.length();
                }
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return new SearchResult(documentId, query, matches.size(), elapsed, matches);
    }

    @Override
    public SearchResult searchByRegex(String documentId, String pattern, SearchOptions options) {
        long start = System.currentTimeMillis();
        getDocument(documentId);
        List<DocumentNode> allNodes = getNodes(documentId);

        Pattern regex = options.isCaseSensitive()
                ? Pattern.compile(pattern)
                : Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);

        List<SearchResult.Match> matches = new ArrayList<>();
        for (DocumentNode node : allNodes) {
            if (matches.size() >= options.getMaxResults()) break;
            if (!shouldIncludeNode(node, options)) continue;

            String text = node.getText();
            if (text == null || text.isEmpty()) continue;

            Matcher matcher = regex.matcher(text);
            while (matcher.find() && matches.size() < options.getMaxResults()) {
                matches.add(buildMatch(node, text, matcher, options.getContextChars()));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return new SearchResult(documentId, pattern, matches.size(), elapsed, matches);
    }

    @Override
    public SearchResult searchByType(String documentId, String query, List<NodeType> nodeTypes, SearchOptions options) {
        long start = System.currentTimeMillis();
        getDocument(documentId);
        List<DocumentNode> allNodes = getNodes(documentId);

        List<SearchResult.Match> matches = new ArrayList<>();
        Set<NodeType> typeSet = new HashSet<>(nodeTypes);

        for (DocumentNode node : allNodes) {
            if (matches.size() >= options.getMaxResults()) break;
            if (!typeSet.contains(node.getNodeType())) continue;
            if (!shouldIncludeNode(node, options)) continue;

            String text = node.getText();
            if (query != null && !query.isEmpty()) {
                if (text == null) continue;
                String searchText = options.isCaseSensitive() ? text : text.toLowerCase();
                String searchQuery = options.isCaseSensitive() ? query : query.toLowerCase();
                if (!searchText.contains(searchQuery)) continue;
            }

            matches.add(new SearchResult.Match(
                    node.getNodeId(), node.getNodeType(), text,
                    text != null ? text.substring(0, Math.min(text.length(), options.getContextChars())) : "",
                    0, text != null ? text.length() : 0
            ));
        }

        long elapsed = System.currentTimeMillis() - start;
        return new SearchResult(documentId, query, matches.size(), elapsed, matches);
    }

    @Override
    public SearchResult searchByFormat(String documentId, Map<String, Object> formatQuery, SearchOptions options) {
        long start = System.currentTimeMillis();
        getDocument(documentId);
        List<DocumentNode> allNodes = getNodes(documentId);

        List<SearchResult.Match> matches = new ArrayList<>();
        for (DocumentNode node : allNodes) {
            if (matches.size() >= options.getMaxResults()) break;
            if (!shouldIncludeNode(node, options)) continue;

            Map<String, Object> nodeAttrs = node.getAttributes();
            boolean allMatch = formatQuery.entrySet().stream()
                    .allMatch(e -> Objects.equals(nodeAttrs.get(e.getKey()), e.getValue()));

            if (allMatch) {
                String text = node.getText();
                matches.add(new SearchResult.Match(
                        node.getNodeId(), node.getNodeType(), text,
                        text != null ? text.substring(0, Math.min(text.length(), 50)) : "",
                        0, text != null ? text.length() : 0
                ));
            }
        }

        long elapsed = System.currentTimeMillis() - start;
        return new SearchResult(documentId, "format:" + formatQuery, matches.size(), elapsed, matches);
    }

    @Override
    public SearchResult.ReplaceSummary findAndReplace(String documentId, String oldText, String newText, SearchOptions options) {
        getDocument(documentId);
        List<DocumentNode> allNodes = getNodes(documentId);

        String searchText = options.isCaseSensitive() ? oldText : oldText.toLowerCase();
        List<String> replacedNodeIds = new ArrayList<>();
        int totalMatches = 0;

        DocumentChangeSet changeSet = new DocumentChangeSet();
        changeSet.setDocumentId(documentId);

        for (DocumentNode node : allNodes) {
            String text = node.getText();
            if (text == null || text.isEmpty()) continue;

            String compareText = options.isCaseSensitive() ? text : text.toLowerCase();
            int count = countOccurrences(compareText, searchText, options.isWholeWord());

            if (count > 0) {
                String replacedText = text.replaceAll(
                        options.isWholeWord() ? "\\b" + Pattern.quote(oldText) + "\\b" : Pattern.quote(oldText),
                        Matcher.quoteReplacement(newText));

                DocumentChangeSet.Change change = new DocumentChangeSet.Change();
                change.setOperation(ChangeOperation.REPLACE_TEXT);
                change.setTargetNodeId(node.getNodeId());
                Map<String, Object> oldValue = new HashMap<>();
                oldValue.put("text", text);
                change.setOldValue(oldValue);
                Map<String, Object> newValue = new HashMap<>();
                newValue.put("text", replacedText);
                change.setNewValue(newValue);
                changeSet.addChange(change);

                replacedNodeIds.add(node.getNodeId());
                totalMatches += count;
            }
        }

        // 批量应用变更
        if (!changeSet.getChanges().isEmpty()) {
            try {
                byte[] rawContent = rawContents.get(documentId);
                if (rawContent != null) {
                    byte[] newContent = adapter.applyChanges(rawContent, changeSet);
                    if (newContent != null) {
                        rawContents.put(documentId, newContent);
                    }
                }
            } catch (Exception e) {
                log.warn("批量替换文本失败 documentId={}: {}", documentId, e.getMessage());
            }
        }

        return new SearchResult.ReplaceSummary(totalMatches, replacedNodeIds.size(), replacedNodeIds);
    }

    // ==================== 私有辅助方法 ====================

    private WordDocumentAsset getDocument(String documentId) {
        WordDocumentAsset asset = documentStore.get(documentId);
        if (asset == null) {
            throw new DocumentException(ErrorCode.NOT_FOUND, "文档不存在: " + documentId);
        }
        return asset;
    }

    private List<DocumentNode> getNodes(String documentId) {
        List<DocumentNode> nodes = nodeTrees.get(documentId);
        if (nodes == null) {
            return Collections.emptyList();
        }
        return flattenTree(nodes);
    }

    private List<DocumentNode> flattenTree(List<DocumentNode> nodes) {
        List<DocumentNode> result = new ArrayList<>();
        if (nodes == null) return result;
        for (DocumentNode node : nodes) {
            result.add(node);
            // 子节点已通过 nodeId 引用，搜索时只搜索顶层节点
            // 子节点会在导入时单独存储在 nodeTrees 中
        }
        return result;
    }

    private boolean shouldIncludeNode(DocumentNode node, SearchOptions options) {
        if (node.getNodeType() == null) return true;
        switch (node.getNodeType()) {
            case FOOTNOTE:
            case ENDNOTE:
                return options.isIncludeFootnotes();
            case HEADER:
            case FOOTER:
                return options.isIncludeHeaders();
            case COMMENT:
                return options.isIncludeComments();
            default:
                return true;
        }
    }

    private SearchResult.Match buildMatch(DocumentNode node, String text, Matcher matcher, int contextChars) {
        int start = Math.max(0, matcher.start() - contextChars);
        int end = Math.min(text.length(), matcher.end() + contextChars);
        String context = text.substring(start, end);
        return new SearchResult.Match(
                node.getNodeId(), node.getNodeType(), text,
                context, matcher.start(), matcher.end()
        );
    }

    private int countOccurrences(String text, String search, boolean wholeWord) {
        if (wholeWord) {
            String regex = "\\b" + Pattern.quote(search) + "\\b";
            Matcher matcher = Pattern.compile(regex).matcher(text);
            int count = 0;
            while (matcher.find()) count++;
            return count;
        }
        int count = 0, idx = 0;
        while ((idx = text.indexOf(search, idx)) != -1) {
            count++;
            idx += search.length();
        }
        return count;
    }
}