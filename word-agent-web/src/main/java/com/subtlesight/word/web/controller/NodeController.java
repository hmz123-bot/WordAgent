package com.subtlesight.word.web.controller;

import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.service.WordDocumentService;
import com.subtlesight.word.web.dto.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 文档节点操作 REST API。
 * 支持对单个节点的文本、格式、位置等操作。
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/nodes")
public class NodeController {

    private final WordDocumentService documentService;

    public NodeController(WordDocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 获取节点详情。
     * GET /api/v1/documents/{documentId}/nodes/{nodeId}
     */
    @GetMapping("/{nodeId}")
    public ResponseEntity<ApiResponse<DocumentNode>> getNode(
            @PathVariable("documentId") String documentId,
            @PathVariable("nodeId") String nodeId) {
        DocumentNode node = documentService.getNode(documentId, nodeId);
        return ResponseEntity.ok(ApiResponse.ok(node));
    }

    /**
     * 获取节点的子节点列表。
     * GET /api/v1/documents/{documentId}/nodes/{nodeId}/children
     */
    @GetMapping("/{nodeId}/children")
    public ResponseEntity<ApiResponse<List<DocumentNode>>> getNodeChildren(
            @PathVariable("documentId") String documentId,
            @PathVariable("nodeId") String nodeId) {
        List<DocumentNode> children = documentService.getNodeChildren(documentId, nodeId);
        return ResponseEntity.ok(ApiResponse.ok(children));
    }

    /**
     * 替换节点文本内容。
     * PUT /api/v1/documents/{documentId}/nodes/{nodeId}/text
     * Body: { "text": "新文本内容" }
     */
    @PutMapping("/{nodeId}/text")
    public ResponseEntity<ApiResponse<Void>> updateNodeText(
            @PathVariable("documentId") String documentId,
            @PathVariable("nodeId") String nodeId,
            @RequestBody Map<String, String> body) {
        String newText = body.get("text");
        if (newText == null) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "text 字段不能为空"));
        }
        documentService.updateNodeText(documentId, nodeId, newText);
        return ResponseEntity.ok(ApiResponse.ok("文本已更新", null));
    }

    /**
     * 更新节点属性。
     * PUT /api/v1/documents/{documentId}/nodes/{nodeId}/attributes
     * Body: { "key": "value", ... }
     */
    @PutMapping("/{nodeId}/attributes")
    public ResponseEntity<ApiResponse<Void>> updateNodeAttributes(
            @PathVariable("documentId") String documentId,
            @PathVariable("nodeId") String nodeId,
            @RequestBody Map<String, Object> attributes) {
        documentService.updateNodeAttributes(documentId, nodeId, attributes);
        return ResponseEntity.ok(ApiResponse.ok("属性已更新", null));
    }

    /**
     * 删除节点。
     * DELETE /api/v1/documents/{documentId}/nodes/{nodeId}
     */
    @DeleteMapping("/{nodeId}")
    public ResponseEntity<ApiResponse<Void>> deleteNode(
            @PathVariable("documentId") String documentId,
            @PathVariable("nodeId") String nodeId) {
        documentService.deleteNode(documentId, nodeId);
        return ResponseEntity.ok(ApiResponse.ok("节点已删除", null));
    }

    /**
     * 在节点后插入新段落。
     * POST /api/v1/documents/{documentId}/nodes/{nodeId}/insert-after
     * Body: { "text": "段落内容", "type": "paragraph" }
     */
    @PostMapping("/{nodeId}/insert-after")
    public ResponseEntity<ApiResponse<DocumentNode>> insertAfter(
            @PathVariable("documentId") String documentId,
            @PathVariable("nodeId") String nodeId,
            @RequestBody Map<String, Object> body) {
        String text = (String) body.getOrDefault("text", "");
        String type = (String) body.getOrDefault("type", "paragraph");
        DocumentNode newNode = documentService.insertNodeAfter(documentId, nodeId, text, type);
        return ResponseEntity.ok(ApiResponse.created(newNode));
    }
}