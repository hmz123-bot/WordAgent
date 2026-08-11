package com.subtlesight.word.web.controller;

import com.subtlesight.word.model.DocumentChangeSet;
import com.subtlesight.word.model.enums.ChangeOperation;
import com.subtlesight.word.model.enums.ReviewStatus;
import com.subtlesight.word.service.WordDocumentService;
import com.subtlesight.word.service.WordDocumentService.SubmitResult;
import com.subtlesight.word.web.dto.request.CreateChangeSetRequest;
import com.subtlesight.word.web.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 变更集管理 REST API。
 */
@RestController
@RequestMapping("/api/v1/documents/{documentId}/changesets")
public class ChangeSetController {

    private final WordDocumentService documentService;

    public ChangeSetController(WordDocumentService documentService) {
        this.documentService = documentService;
    }

    /**
     * 创建变更集。
     * POST /api/v1/documents/{documentId}/changesets
     */
    @PostMapping
    public ResponseEntity<ApiResponse<DocumentChangeSet>> createChangeSet(
            @PathVariable("documentId") String documentId,
            @Valid @RequestBody CreateChangeSetRequest request) {

        DocumentChangeSet changeSet = new DocumentChangeSet();
        changeSet.setDocumentId(documentId);
        changeSet.setExpectedVersion(request.getExpectedVersion());
        changeSet.setIdempotencyKey(request.getIdempotencyKey());
        changeSet.setSummary(request.getSummary());
        changeSet.setAuthorId(request.getAuthorId());
        changeSet.setAuthorType(request.getAuthorType());

        for (CreateChangeSetRequest.ChangeRequest cr : request.getChanges()) {
            DocumentChangeSet.Change change = new DocumentChangeSet.Change();
            change.setOperation(ChangeOperation.valueOf(cr.getOperation()));
            change.setTargetNodeId(cr.getTargetNodeId());
            change.setTargetNodeType(cr.getTargetNodeType());
            change.setOldValue(cr.getOldValue());
            change.setNewValue(cr.getNewValue());
            change.setPosition(cr.getPosition());
            change.setContext(cr.getContext());
            changeSet.addChange(change);
        }

        DocumentChangeSet saved = documentService.createChangeSet(changeSet);
        return ResponseEntity.ok(ApiResponse.created(saved));
    }

    /**
     * 获取变更集列表。
     * GET /api/v1/documents/{documentId}/changesets
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<DocumentChangeSet>>> listChangeSets(
            @PathVariable("documentId") String documentId) {
        List<DocumentChangeSet> list = documentService.getChangeSets(documentId);
        return ResponseEntity.ok(ApiResponse.ok(list));
    }

    /**
     * 获取变更集详情。
     * GET /api/v1/documents/{documentId}/changesets/{changeSetId}
     */
    @GetMapping("/{changeSetId}")
    public ResponseEntity<ApiResponse<DocumentChangeSet>> getChangeSet(
            @PathVariable("documentId") String documentId,
            @PathVariable("changeSetId") String changeSetId) {
        DocumentChangeSet changeSet = documentService.getChangeSet(changeSetId)
                .orElseThrow(() -> new RuntimeException("变更集不存在: " + changeSetId));
        return ResponseEntity.ok(ApiResponse.ok(changeSet));
    }

    /**
     * 提交变更集（DRAFT → PENDING）。
     * POST /api/v1/documents/{documentId}/changesets/{changeSetId}/submit
     */
    @PostMapping("/{changeSetId}/submit")
    public ResponseEntity<ApiResponse<SubmitResult>> submitChangeSet(
            @PathVariable("documentId") String documentId,
            @PathVariable("changeSetId") String changeSetId) {
        SubmitResult result = documentService.submitChangeSet(changeSetId);
        return ResponseEntity.ok(ApiResponse.ok("已提交审阅", result));
    }

    /**
     * 接受变更集（PENDING → ACCEPTED + 合并到文档）。
     * POST /api/v1/documents/{documentId}/changesets/{changeSetId}/accept
     */
    @PostMapping("/{changeSetId}/accept")
    public ResponseEntity<ApiResponse<DocumentChangeSet>> acceptChangeSet(
            @PathVariable("documentId") String documentId,
            @PathVariable("changeSetId") String changeSetId) {
        DocumentChangeSet changeSet = documentService.acceptChangeSet(changeSetId);
        return ResponseEntity.ok(ApiResponse.ok("已接受并合并", changeSet));
    }

    /**
     * 拒绝变更集（PENDING → REJECTED）。
     * POST /api/v1/documents/{documentId}/changesets/{changeSetId}/reject
     */
    @PostMapping("/{changeSetId}/reject")
    public ResponseEntity<ApiResponse<DocumentChangeSet>> rejectChangeSet(
            @PathVariable("documentId") String documentId,
            @PathVariable("changeSetId") String changeSetId,
            @RequestBody(required = false) Map<String, String> body) {
        String reason = body != null ? body.get("reason") : null;
        DocumentChangeSet changeSet = documentService.rejectChangeSet(changeSetId, reason);
        return ResponseEntity.ok(ApiResponse.ok("已拒绝", changeSet));
    }

    /**
     * 删除变更集。
     * DELETE /api/v1/documents/{documentId}/changesets/{changeSetId}
     */
    @DeleteMapping("/{changeSetId}")
    public ResponseEntity<ApiResponse<Void>> deleteChangeSet(
            @PathVariable("documentId") String documentId,
            @PathVariable("changeSetId") String changeSetId) {
        documentService.deleteChangeSet(changeSetId);
        return ResponseEntity.ok(ApiResponse.ok("变更集已删除", null));
    }
}