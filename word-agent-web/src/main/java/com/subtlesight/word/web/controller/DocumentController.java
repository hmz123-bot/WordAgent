package com.subtlesight.word.web.controller;

import com.subtlesight.word.model.ConversionReport;
import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.WebEditingProjection;
import com.subtlesight.word.model.WordDocumentAsset;
import com.subtlesight.word.service.DocumentExportService;
import com.subtlesight.word.service.DocumentImportService;
import com.subtlesight.word.service.WordDocumentService;
import com.subtlesight.word.service.WordDocumentService.DocumentVersion;
import com.subtlesight.word.service.WordDocumentService.SubmitResult;
import com.subtlesight.word.web.dto.request.SaveDocumentRequest;
import com.subtlesight.word.web.dto.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 文档管理 REST API。
 */
@RestController
@RequestMapping("/api/v1/documents")
public class DocumentController {

    private final WordDocumentService documentService;
    private final DocumentImportService importService;
    private final DocumentExportService exportService;

    public DocumentController(WordDocumentService documentService,
                              DocumentImportService importService,
                              DocumentExportService exportService) {
        this.documentService = documentService;
        this.importService = importService;
        this.exportService = exportService;
    }

    /**
     * 导入文档。
     * POST /api/v1/documents/import
     * multipart/form-data: file + metadata (JSON)
     */
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<Map<String, Object>>> importDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "description", required = false) String description,
            @RequestParam(value = "authorId", required = false) String authorId) throws IOException {

        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "untitled.docx";
        }
        if (!fileName.toLowerCase().endsWith(".docx")) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "仅支持 .docx 格式文件"));
        }

        WordDocumentAsset asset = documentService.createDocument(fileName, file.getBytes());
        if (description != null) {
            asset.setStoragePath(description);
        }
        if (authorId != null) {
            asset.setOwnerId(authorId);
        }

        // 使用 documentService.importDocument() 来解析并保存节点树和投影到内存存储中
        ConversionReport report = documentService.importDocument(asset, file.getBytes());

        Map<String, Object> result = Map.of(
                "documentId", asset.getDocumentId(),
                "fileName", asset.getFileName(),
                "version", asset.getCurrentVersion(),
                "report", report
        );
        return ResponseEntity.ok(ApiResponse.created(result));
    }

    /**
     * 获取文档列表。
     * GET /api/v1/documents
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<WordDocumentAsset>>> listDocuments() {
        List<WordDocumentAsset> documents = documentService.listDocuments();
        return ResponseEntity.ok(ApiResponse.ok(documents));
    }

    /**
     * 获取文档详情。
     * GET /api/v1/documents/{documentId}
     */
    @GetMapping("/{documentId}")
    public ResponseEntity<ApiResponse<WordDocumentAsset>> getDocument(
            @PathVariable("documentId") String documentId) {
        WordDocumentAsset asset = documentService.getDocument(documentId);
        return ResponseEntity.ok(ApiResponse.ok(asset));
    }

    /**
     * 获取文档节点树。
     * GET /api/v1/documents/{documentId}/nodes
     */
    @GetMapping("/{documentId}/nodes")
    public ResponseEntity<ApiResponse<List<DocumentNode>>> getDocumentNodes(
            @PathVariable("documentId") String documentId) {
        List<DocumentNode> nodes = documentService.getDocumentNodes(documentId);
        return ResponseEntity.ok(ApiResponse.ok(nodes));
    }

    /**
     * 获取文档网页投影（TipTap HTML）。
     * GET /api/v1/documents/{documentId}/projection
     */
    @GetMapping("/{documentId}/projection")
    public ResponseEntity<ApiResponse<WebEditingProjection>> getProjection(
            @PathVariable("documentId") String documentId) {
        WebEditingProjection projection = documentService.getProjection(documentId);
        return ResponseEntity.ok(ApiResponse.ok(projection));
    }

    /**
     * 导出文档为 .docx。
     * GET /api/v1/documents/{documentId}/export?includeTrackChanges=false
     */
    @GetMapping("/{documentId}/export")
    public ResponseEntity<Resource> exportDocument(
            @PathVariable("documentId") String documentId,
            @RequestParam(value = "includeTrackChanges", defaultValue = "false") boolean includeTrackChanges,
            @RequestParam(value = "format", defaultValue = "docx") String format) {

        DocumentExportService.ExportOptions options = new DocumentExportService.ExportOptions();
        options.setIncludeTrackChanges(includeTrackChanges);
        options.setFormat(format);

        DocumentExportService.ExportResult result = exportService.export(documentId, options);
        if (!result.isSuccess()) {
            return ResponseEntity.internalServerError().build();
        }

        ByteArrayResource resource = new ByteArrayResource(result.getFileContent());
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + result.getFileName() + "\"")
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.wordprocessingml.document"))
                .body(resource);
    }

    /**
     * 删除文档。
     * DELETE /api/v1/documents/{documentId}
     */
    @DeleteMapping("/{documentId}")
    public ResponseEntity<ApiResponse<Void>> deleteDocument(
            @PathVariable("documentId") String documentId) {
        documentService.deleteDocument(documentId);
        return ResponseEntity.ok(ApiResponse.ok("文档已删除", null));
    }

    /**
     * 获取文档版本历史。
     * GET /api/v1/documents/{documentId}/versions
     */
    @GetMapping("/{documentId}/versions")
    public ResponseEntity<ApiResponse<List<DocumentVersion>>> getVersionHistory(
            @PathVariable("documentId") String documentId) {
        List<DocumentVersion> versions = documentService.getVersionHistory(documentId);
        return ResponseEntity.ok(ApiResponse.ok(versions));
    }

    /**
     * 恢复到指定版本。
     * POST /api/v1/documents/{documentId}/versions/{version}/restore
     */
    @PostMapping("/{documentId}/versions/{version}/restore")
    public ResponseEntity<ApiResponse<Void>> restoreVersion(
            @PathVariable("documentId") String documentId,
            @PathVariable("version") int version) {
        documentService.restoreVersion(documentId, version);
        return ResponseEntity.ok(ApiResponse.ok("版本已恢复", null));
    }

    /**
     * 保存文档为草稿。
     * POST /api/v1/documents/{documentId}/save-draft
     */
    @PostMapping("/{documentId}/save-draft")
    public ResponseEntity<ApiResponse<WordDocumentAsset>> saveDraft(
            @PathVariable("documentId") String documentId) {
        WordDocumentAsset asset = documentService.saveDraft(documentId);
        return ResponseEntity.ok(ApiResponse.ok("草稿已保存", asset));
    }

    /**
     * 保存文档编辑结果（一键保存：批量更新节点 → 创建变更集 → 提交合并到 .docx）。
     * POST /api/v1/documents/{documentId}/save
     */
    @PostMapping("/{documentId}/save")
    public ResponseEntity<ApiResponse<SubmitResult>> saveDocument(
            @PathVariable("documentId") String documentId,
            @Valid @RequestBody SaveDocumentRequest request) {

        List<WordDocumentService.NodeTextUpdate> updates = request.getChanges().stream()
                .map(c -> new WordDocumentService.NodeTextUpdate(c.getNodeId(), c.getText()))
                .toList();

        SubmitResult result = documentService.saveDocument(documentId, request.getSummary(), updates);
        if (result.isSuccess()) {
            return ResponseEntity.ok(ApiResponse.ok("文档已保存，当前版本: v" + result.getNewVersion(), result));
        } else {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, result.getErrorMessage()));
        }
    }

    /**
     * 更新文档状态（已发布/编辑中/审核中/已归档/错误）。
     * PUT /api/v1/documents/{documentId}/status
     */
    @PutMapping("/{documentId}/status")
    public ResponseEntity<ApiResponse<WordDocumentAsset>> updateStatus(
            @PathVariable("documentId") String documentId,
            @RequestParam("status") String status) {
        try {
            WordDocumentAsset.DocumentStatus newStatus = WordDocumentAsset.DocumentStatus.valueOf(status.toUpperCase());
            documentService.updateStatus(documentId, newStatus);
            return ResponseEntity.ok(ApiResponse.ok("状态已更新为 " + newStatus, null));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest()
                    .body(ApiResponse.error(400, "无效的状态值: " + status));
        }
    }

    /**
     * 归档文档。
     * PUT /api/v1/documents/{documentId}/archive
     */
    @PutMapping("/{documentId}/archive")
    public ResponseEntity<ApiResponse<WordDocumentAsset>> archiveDocument(
            @PathVariable("documentId") String documentId) {
        documentService.archiveDocument(documentId);
        return ResponseEntity.ok(ApiResponse.ok("文档已归档", null));
    }
}