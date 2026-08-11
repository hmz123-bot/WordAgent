package com.subtlesight.word.web.controller;

import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.enums.ChangeOperation;
import com.subtlesight.word.model.enums.ReviewStatus;
import com.subtlesight.word.service.WordDocumentService;
import com.subtlesight.word.model.DocumentChangeSet;
import com.subtlesight.word.web.config.AiConfig;
import com.subtlesight.word.web.dto.request.AiEditRequest;
import com.subtlesight.word.web.dto.response.AiEditResponse;
import com.subtlesight.word.web.dto.response.ApiResponse;
import com.subtlesight.word.web.service.ai.AiService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * AI 智能编辑 REST API。
 */
@RestController
@RequestMapping("/api/v1/ai")
public class AiController {

    private static final Logger log = LoggerFactory.getLogger(AiController.class);

    private final AiService aiService;
    private final WordDocumentService documentService;
    private final AiConfig aiConfig;

    public AiController(AiService aiService, WordDocumentService documentService, AiConfig aiConfig) {
        this.aiService = aiService;
        this.documentService = documentService;
        this.aiConfig = aiConfig;
    }

    /**
     * 获取 AI 配置状态。
     * GET /api/v1/ai/status
     */
    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        boolean configured = aiConfig.isEnabled()
                && aiConfig.getApiKey() != null
                && !aiConfig.getApiKey().isEmpty();
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
                "enabled", aiConfig.isEnabled(),
                "configured", configured,
                "model", aiConfig.getModel(),
                "endpoint", aiConfig.getApiEndpoint()
        )));
    }

    /**
     * AI 智能编辑。
     * POST /api/v1/ai/edit
     */
    @PostMapping("/edit")
    public ResponseEntity<ApiResponse<AiEditResponse>> aiEdit(
            @Valid @RequestBody AiEditRequest request) {

        // 获取文档节点树，构建文本上下文
        String context = buildDocumentContext(request.getDocumentId(), request.getNodeIds());

        AiEditResponse response = aiService.edit(
                request.getDocumentId(),
                request.getInstruction(),
                context,
                request.getNodeIds()
        );

        // 校验并过滤无效的 nodeId（LLM 可能编造不存在的节点 ID）
        AiEditResponse validated = validateAndFilterSuggestions(request.getDocumentId(), response);

        return ResponseEntity.ok(ApiResponse.ok("AI 编辑完成", validated));
    }

    /**
     * AI 编辑并自动创建变更集。
     * POST /api/v1/ai/edit-and-apply
     */
    @PostMapping("/edit-and-apply")
    public ResponseEntity<ApiResponse<DocumentChangeSet>> aiEditAndApply(
            @Valid @RequestBody AiEditRequest request) {

        String context = buildDocumentContext(request.getDocumentId(), request.getNodeIds());
        AiEditResponse response = aiService.edit(
                request.getDocumentId(),
                request.getInstruction(),
                context,
                request.getNodeIds()
        );

        // 创建变更集
        DocumentChangeSet changeSet = new DocumentChangeSet();
        changeSet.setDocumentId(request.getDocumentId());
        changeSet.setSummary("AI 编辑: " + request.getInstruction());
        changeSet.setAuthorId("ai-assistant");
        changeSet.setAuthorType("SYSTEM");
        changeSet.setExpectedVersion(
                documentService.getDocument(request.getDocumentId()).getCurrentVersion()
        );

        for (AiEditResponse.Suggestion suggestion : response.getSuggestions()) {
            DocumentChangeSet.Change change = new DocumentChangeSet.Change();
            change.setOperation(ChangeOperation.REPLACE_TEXT);
            change.setTargetNodeId(suggestion.getNodeId());
            change.setOldValue(Map.of("text", suggestion.getOriginalText()));
            change.setNewValue(Map.of("text", suggestion.getSuggestedText()));
            change.setContext(suggestion.getDescription());
            changeSet.addChange(change);
        }

        DocumentChangeSet saved = documentService.createChangeSet(changeSet);
        log.info("AI 编辑已创建变更集: documentId={}, instruction={}, changes={}",
                request.getDocumentId(), request.getInstruction(), response.getSuggestions().size());

        return ResponseEntity.ok(ApiResponse.created(saved));
    }

    /**
     * 校验并过滤建议中的 nodeId——LLM 经常编造不存在的节点 ID，
     * 如果不过滤会导致前端 apply 时全部 404 失败。
     */
    private AiEditResponse validateAndFilterSuggestions(String documentId, AiEditResponse response) {
        List<DocumentNode> nodes = documentService.getDocumentNodes(documentId);
        Set<String> validNodeIds = nodes.stream()
                .map(DocumentNode::getNodeId)
                .collect(Collectors.toSet());

        List<AiEditResponse.Suggestion> original = response.getSuggestions();
        if (original == null || original.isEmpty()) {
            return response;
        }

        List<AiEditResponse.Suggestion> valid = new ArrayList<>();
        List<String> rejectedNodeIds = new ArrayList<>();

        for (AiEditResponse.Suggestion s : original) {
            String nid = s.getNodeId();
            if (nid != null && validNodeIds.contains(nid)) {
                valid.add(s);
            } else {
                rejectedNodeIds.add(nid == null ? "(null)" : nid);
            }
        }

        if (!rejectedNodeIds.isEmpty()) {
            log.warn("LLM 返回了 {} 个无效 nodeId，已过滤: {}", rejectedNodeIds.size(), rejectedNodeIds);
        }

        response.setSuggestions(valid);
        if (valid.size() < original.size()) {
            response.setSummary(response.getSummary()
                    + String.format(" (已过滤 %d 条无效建议)", original.size() - valid.size()));
        }

        return response;
    }

    /**
     * 构建文档上下文文本。
     * 将文档节点树展平为文本列表，供 AI 理解。
     */
    private String buildDocumentContext(String documentId, List<String> nodeIds) {
        List<DocumentNode> nodes = documentService.getDocumentNodes(documentId);

        if (nodeIds != null && !nodeIds.isEmpty()) {
            nodes = nodes.stream()
                    .filter(n -> nodeIds.contains(n.getNodeId()))
                    .collect(Collectors.toList());
        }

        StringBuilder sb = new StringBuilder();
        for (DocumentNode node : nodes) {
            sb.append("[").append(node.getNodeType()).append("] ")
                    .append("id=").append(node.getNodeId()).append(" | ")
                    .append("text=").append(node.getText() != null ? node.getText() : "(空)")
                    .append("\n");
        }
        return sb.toString();
    }
}