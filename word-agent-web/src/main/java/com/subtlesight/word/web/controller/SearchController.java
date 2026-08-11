package com.subtlesight.word.web.controller;

import com.subtlesight.word.model.enums.NodeType;
import com.subtlesight.word.service.search.DocumentSearchService;
import com.subtlesight.word.service.search.SearchResult;
import com.subtlesight.word.web.dto.request.SearchRequest;
import com.subtlesight.word.web.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.stream.Collectors;

/**
 * 文档搜索/文本匹配 REST API。
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/search")
public class SearchController {

    private final DocumentSearchService searchService;

    public SearchController(DocumentSearchService searchService) {
        this.searchService = searchService;
    }

    /**
     * 全文搜索。
     * POST /api/v1/documents/{documentId}/search
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SearchResult>> search(
            @PathVariable("documentId") String documentId,
            @RequestBody SearchRequest request) {

        DocumentSearchService.SearchOptions options = toOptions(request);
        SearchResult result;

        switch (request.getMode() != null ? request.getMode() : "text") {
            case "regex":
                result = searchService.searchByRegex(documentId, request.getPattern(), options);
                break;
            case "type":
                result = searchService.searchByType(
                        documentId,
                        request.getQuery(),
                        request.getNodeTypes().stream()
                                .map(NodeType::valueOf)
                                .collect(Collectors.toList()),
                        options
                );
                break;
            case "format":
                result = searchService.searchByFormat(documentId, request.getFormatQuery(), options);
                break;
            default:
                result = searchService.search(documentId, request.getQuery(), options);
                break;
        }

        return ResponseEntity.ok(ApiResponse.ok(result));
    }

    /**
     * 查找并替换。
     * POST /api/v1/documents/{documentId}/search/replace
     */
    @PostMapping("/replace")
    public ResponseEntity<ApiResponse<SearchResult.ReplaceSummary>> findAndReplace(
            @PathVariable("documentId") String documentId,
            @RequestBody SearchRequest request) {

        DocumentSearchService.SearchOptions options = toOptions(request);
        SearchResult.ReplaceSummary summary = searchService.findAndReplace(
                documentId, request.getQuery(), request.getReplacement(), options
        );
        return ResponseEntity.ok(ApiResponse.ok(summary));
    }

    private DocumentSearchService.SearchOptions toOptions(SearchRequest request) {
        DocumentSearchService.SearchOptions options = new DocumentSearchService.SearchOptions();
        options.setCaseSensitive(request.isCaseSensitive());
        options.setWholeWord(request.isWholeWord());
        options.setMaxResults(request.getMaxResults());
        options.setContextChars(request.getContextChars());
        return options;
    }
}