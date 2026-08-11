package com.subtlesight.word.service.impl;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.adapter.poi.PoiDocumentAdapter;
import com.subtlesight.word.exception.DocumentException;
import com.subtlesight.word.exception.VersionConflictException;
import com.subtlesight.word.model.*;
import com.subtlesight.word.model.enums.ChangeOperation;
import com.subtlesight.word.model.enums.ErrorCode;
import com.subtlesight.word.model.enums.NodeType;
import com.subtlesight.word.model.enums.ReviewStatus;
import com.subtlesight.word.model.enums.SupportLevel;
import com.subtlesight.word.service.WordDocumentService;
import com.subtlesight.word.util.NodeIdGenerator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Word 文档核心服务实现。
 * <p>
 * 管理文档生命周期：导入、读取、变更、导出、版本控制。
 * 内部使用内存存储（生产环境应替换为持久化存储）。
 * </p>
 */
public class WordDocumentServiceImpl implements WordDocumentService {

    private static final Logger log = LoggerFactory.getLogger(WordDocumentServiceImpl.class);

    private final WordDocumentAdapter adapter;
    private final Map<String, WordDocumentAsset> documents;
    private final Map<String, WebEditingProjection> projections;
    private final Map<String, List<DocumentNode>> nodeTrees;
    private final Map<String, DocumentChangeSet> changeSets;
    private final Map<String, List<DocumentVersion>> versionHistories;
    private final Map<String, Map<Integer, byte[]>> versionContents;
    private final Map<String, DocumentNode> nodeIndex;
    private final Map<String, byte[]> rawContents;

    public WordDocumentServiceImpl() {
        this.adapter = new PoiDocumentAdapter();
        this.documents = new ConcurrentHashMap<>();
        this.projections = new ConcurrentHashMap<>();
        this.nodeTrees = new ConcurrentHashMap<>();
        this.changeSets = new ConcurrentHashMap<>();
        this.versionHistories = new ConcurrentHashMap<>();
        this.versionContents = new ConcurrentHashMap<>();
        this.nodeIndex = new ConcurrentHashMap<>();
        this.rawContents = new ConcurrentHashMap<>();
    }

    public WordDocumentServiceImpl(WordDocumentAdapter adapter) {
        this.adapter = adapter;
        this.documents = new ConcurrentHashMap<>();
        this.projections = new ConcurrentHashMap<>();
        this.nodeTrees = new ConcurrentHashMap<>();
        this.changeSets = new ConcurrentHashMap<>();
        this.versionHistories = new ConcurrentHashMap<>();
        this.versionContents = new ConcurrentHashMap<>();
        this.nodeIndex = new ConcurrentHashMap<>();
        this.rawContents = new ConcurrentHashMap<>();
    }

    public WordDocumentServiceImpl(WordDocumentAdapter adapter,
                                   Map<String, WordDocumentAsset> documentStore) {
        this.adapter = adapter;
        this.documents = documentStore instanceof ConcurrentHashMap
                ? documentStore : new ConcurrentHashMap<>(documentStore);
        this.projections = new ConcurrentHashMap<>();
        this.nodeTrees = new ConcurrentHashMap<>();
        this.changeSets = new ConcurrentHashMap<>();
        this.versionHistories = new ConcurrentHashMap<>();
        this.versionContents = new ConcurrentHashMap<>();
        this.nodeIndex = new ConcurrentHashMap<>();
        this.rawContents = new ConcurrentHashMap<>();
    }

    @Override
    public WordDocumentAsset createDocument(String fileName, byte[] content) {
        String documentId = "doc-" + NodeIdGenerator.randomId();
        WordDocumentAsset asset = new WordDocumentAsset(documentId);
        asset.setFileName(fileName);
        asset.setFileSize(content != null ? content.length : 0);
        asset.setStatus(WordDocumentAsset.DocumentStatus.READY);
        documents.put(documentId, asset);
        versionHistories.put(documentId, new ArrayList<>());
        Map<Integer, byte[]> versions = new ConcurrentHashMap<>();
        versions.put(1, content.clone());
        versionContents.put(documentId, versions);
        rawContents.put(documentId, content.clone());
        nodeTrees.put(documentId, new ArrayList<>());
        return asset;
    }

    @Override
    public ConversionReport importDocument(WordDocumentAsset asset, byte[] fileContent) {
        log.info("导入文档: {}", asset.getFileName());

        // 1. 解析文档
        WebEditingProjection projection = adapter.read(fileContent);
        List<DocumentNode> nodes = projection.getContent() != null ? projection.getContent() : Collections.emptyList();

        // 2. 保存资产
        asset.setStatus(WordDocumentAsset.DocumentStatus.READY);
        documents.put(asset.getDocumentId(), asset);

        // 3. 保存节点树
        enrichNodesWithAnchors(nodes, fileContent);
        nodeTrees.put(asset.getDocumentId(), nodes);
        indexNodes(nodes);

        // 4. 生成投影
        projection.setDocumentId(asset.getDocumentId());
        projection.setVersion(String.valueOf(asset.getCurrentVersion()));
        projections.put(asset.getDocumentId(), projection);

        // 5. 保存版本
        versionHistories.put(asset.getDocumentId(), new ArrayList<>());
        Map<Integer, byte[]> versions = new ConcurrentHashMap<>();
        versions.put(1, fileContent.clone());
        versionContents.put(asset.getDocumentId(), versions);
        rawContents.put(asset.getDocumentId(), fileContent.clone());

        // 6. 构建转换报告
        ConversionReport report = buildImportReport(asset, nodes);
        return report;
    }

    @Override
    public List<WordDocumentAsset> listDocuments() {
        return new ArrayList<>(documents.values());
    }

    @Override
    public WordDocumentAsset getDocument(String documentId) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        return asset;
    }

    @Override
    public void deleteDocument(String documentId) {
        documents.remove(documentId);
        projections.remove(documentId);
        nodeTrees.remove(documentId);
        versionHistories.remove(documentId);
        versionContents.remove(documentId);
        List<DocumentNode> docNodes = nodeTrees.get(documentId);
        if (docNodes != null) {
            for (DocumentNode n : docNodes) {
                nodeIndex.remove(n.getNodeId());
            }
        }
        // 清除关联的变更集
        changeSets.values().removeIf(cs -> cs.getDocumentId().equals(documentId));
    }

    @Override
    public WordDocumentAsset saveDraft(String documentId) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + documentId);
        asset.setStatus(WordDocumentAsset.DocumentStatus.DRAFT);
        asset.setUpdatedAt(Instant.now());
        return asset;
    }

    @Override
    public SubmitResult saveDocument(String documentId, String summary, List<NodeTextUpdate> updates) {
        log.info("保存文档编辑: documentId={}, 变更数={}", documentId, updates != null ? updates.size() : 0);

        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }
        if (updates == null || updates.isEmpty()) {
            SubmitResult result = new SubmitResult();
            result.setSuccess(false);
            result.setErrorCode("NO_CHANGES");
            result.setErrorMessage("没有需要保存的变更");
            return result;
        }

        // 1. 构建变更集
        DocumentChangeSet changeSet = new DocumentChangeSet(documentId);
        changeSet.setDocumentId(documentId);
        changeSet.setExpectedVersion(asset.getCurrentVersion());
        changeSet.setSummary(summary != null ? summary : "手动编辑保存");
        changeSet.setAuthorId("web-editor");
        changeSet.setAuthorType("WEB_EDITOR");

        for (NodeTextUpdate update : updates) {
            DocumentNode node = getNode(documentId, update.nodeId());
            if (node == null) {
                log.warn("节点不存在，跳过: nodeId={}", update.nodeId());
                continue;
            }

            DocumentChangeSet.Change change = new DocumentChangeSet.Change();
            change.setOperation(ChangeOperation.REPLACE_TEXT);
            change.setTargetNodeId(update.nodeId());
            change.setTargetNodeType(node.getNodeType() != null ? node.getNodeType().name() : "paragraph");
            change.setOldValue(Map.of("text", node.getText() != null ? node.getText() : ""));
            change.setNewValue(Map.of("text", update.text() != null ? update.text() : ""));
            changeSet.addChange(change);
        }

        if (changeSet.getChanges().isEmpty()) {
            SubmitResult result = new SubmitResult();
            result.setSuccess(false);
            result.setErrorCode("NO_VALID_CHANGES");
            result.setErrorMessage("所有变更均无效（节点可能已不存在）");
            return result;
        }

        // 2. 创建变更集（含版本校验）
        createChangeSet(changeSet);

        // 3. 提交变更集（应用到二进制 .docx，创建版本，重建节点树）
        return submitChangeSet(changeSet.getChangeSetId());
    }

    @Override
    public void updateStatus(String documentId, WordDocumentAsset.DocumentStatus status) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + documentId);
        asset.setStatus(status);
        asset.setUpdatedAt(Instant.now());
        log.info("文档 {} 状态已更新为 {}", documentId, status);
    }

    @Override
    public void archiveDocument(String documentId) {
        updateStatus(documentId, WordDocumentAsset.DocumentStatus.ARCHIVED);
    }

    @Override
    public WebEditingProjection getProjection(String documentId) {
        WebEditingProjection projection = projections.get(documentId);
        if (projection == null) {
            throw new IllegalArgumentException("文档投影不存在: " + documentId);
        }
        // 重新生成投影（确保与事实来源一致）
        WordDocumentAsset asset = documents.get(documentId);
        if (asset != null) {
            Map<Integer, byte[]> versions = versionContents.get(documentId);
            if (versions != null) {
                byte[] latest = versions.get(asset.getCurrentVersion());
                if (latest != null) {
                    List<DocumentNode> nodes = nodeTrees.get(documentId);
                    if (nodes != null) {
                        WebEditingProjection fresh = buildProjection(asset, nodes, latest);
                        projections.put(documentId, fresh);
                        return fresh;
                    }
                }
            }
        }
        return projection;
    }

    @Override
    public List<DocumentNode> getDocumentNodes(String documentId) {
        return nodeTrees.getOrDefault(documentId, Collections.emptyList());
    }

    @Override
    public DocumentNode getNode(String documentId, String nodeId) {
        DocumentNode node = nodeIndex.get(nodeId);
        if (node == null) {
            // 回退到节点树搜索
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                node = flattenTree(nodes).stream()
                        .filter(n -> n.getNodeId().equals(nodeId))
                        .findFirst().orElse(null);
            }
        }
        if (node == null) {
            throw new IllegalArgumentException("节点不存在: " + nodeId);
        }
        return node;
    }

    @Override
    public List<DocumentNode> getNodeChildren(String documentId, String nodeId) {
        DocumentNode parent = getNode(documentId, nodeId);
        List<DocumentNode> children = parent.getChildren();
        if (children == null || children.isEmpty()) {
            return Collections.emptyList();
        }
        return children;
    }

    @Override
    public void updateNodeText(String documentId, String nodeId, String newText) {
        DocumentNode node = getNode(documentId, nodeId);
        node.setText(newText);
        log.debug("节点文本已更新: nodeId={}, text={}", nodeId, newText);
    }

    @Override
    public void updateNodeAttributes(String documentId, String nodeId, Map<String, Object> attributes) {
        DocumentNode node = getNode(documentId, nodeId);
        if (attributes != null) {
            attributes.forEach((key, value) -> node.getAttributes().merge(key, value, (old, val) -> val));
        }
        log.debug("节点属性已更新: nodeId={}, attrs={}", nodeId, attributes);
    }

    @Override
    public void deleteNode(String documentId, String nodeId) {
        DocumentNode node = nodeIndex.remove(nodeId);
        if (node == null) {
            // 从节点树中移除
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                nodes.removeIf(n -> n.getNodeId().equals(nodeId));
                for (DocumentNode n : nodes) {
                    n.getChildren().removeIf(child -> child.getNodeId().equals(nodeId));
                }
            }
        } else {
            // 从父节点中移除
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                for (DocumentNode n : nodes) {
                    n.getChildren().removeIf(child -> child.getNodeId().equals(nodeId));
                }
            }
        }
        log.debug("节点已删除: nodeId={}", nodeId);
    }

    @Override
    public DocumentNode insertNodeAfter(String documentId, String nodeId, String text, String type) {
        String newNodeId = documentId + "_" + NodeIdGenerator.randomId();

        DocumentNode newNode = new DocumentNode();
        newNode.setNodeId(newNodeId);
        newNode.setNodeType(NodeType.valueOf(type.toUpperCase()));
        newNode.setText(text);

        // 索引新节点
        nodeIndex.put(newNodeId, newNode);

        // 添加到节点树
        List<DocumentNode> nodes = nodeTrees.get(documentId);
        if (nodes != null) {
            // 找到目标节点位置，在其后插入
            int refIdx = -1;
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).getNodeId().equals(nodeId)) {
                    refIdx = i;
                    break;
                }
            }
            if (refIdx >= 0) {
                nodes.add(refIdx + 1, newNode);
            } else {
                nodes.add(newNode);
            }
        }

        log.debug("节点已插入: after={}, newNodeId={}, type={}", nodeId, newNodeId, type);
        return newNode;
    }

    @Override
    public List<DocumentNode> searchNodes(String documentId, String query, String nodeType) {
        List<DocumentNode> nodes = nodeTrees.get(documentId);
        if (nodes == null) return Collections.emptyList();

        return nodes.stream()
                .filter(n -> {
                    if (query != null && !query.isEmpty()) {
                        String text = n.getText();
                        if (text == null || !text.toLowerCase().contains(query.toLowerCase())) {
                            return false;
                        }
                    }
                    if (nodeType != null && !nodeType.isEmpty()) {
                        return n.getNodeType().name().equalsIgnoreCase(nodeType);
                    }
                    return true;
                })
                .collect(Collectors.toList());
    }

    @Override
    public DocumentChangeSet createChangeSet(DocumentChangeSet changeSet) {
        log.info("创建变更集: {} (文档: {}, 版本: {})",
                changeSet.getChangeSetId(), changeSet.getDocumentId(), changeSet.getExpectedVersion());

        WordDocumentAsset asset = documents.get(changeSet.getDocumentId());
        if (asset == null) {
            throw new IllegalArgumentException("文档不存在: " + changeSet.getDocumentId());
        }

        // 校验版本
        if (changeSet.getExpectedVersion() != asset.getCurrentVersion()) {
            throw new VersionConflictException(
                    changeSet.getDocumentId(),
                    changeSet.getExpectedVersion(),
                    asset.getCurrentVersion()
            );
        }

        changeSet.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        changeSet.setCreatedAt(Instant.now());
        changeSet.setUpdatedAt(Instant.now());
        changeSets.put(changeSet.getChangeSetId(), changeSet);
        return changeSet;
    }

    @Override
    public ValidationResult validateChangeSet(String changeSetId) {
        DocumentChangeSet changeSet = changeSets.get(changeSetId);
        if (changeSet == null) {
            throw new IllegalArgumentException("变更集不存在: " + changeSetId);
        }

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        // 1. 检查文档是否存在
        WordDocumentAsset asset = documents.get(changeSet.getDocumentId());
        if (asset == null) {
            errors.add("文档不存在: " + changeSet.getDocumentId());
            return new ValidationResult(false, errors, warnings);
        }

        // 2. 检查版本一致性
        if (changeSet.getExpectedVersion() != asset.getCurrentVersion()) {
            errors.add("版本冲突: 期望版本 " + changeSet.getExpectedVersion()
                    + ", 当前版本 " + asset.getCurrentVersion());
            return new ValidationResult(false, errors, warnings);
        }

        // 3. 校验每个操作
        for (DocumentChangeSet.Change change : changeSet.getChanges()) {
            ValidationResult opResult = validateChange(changeSet.getDocumentId(), change);
            if (!opResult.isValid()) {
                errors.addAll(opResult.getErrors());
            }
            warnings.addAll(opResult.getWarnings());
        }

        boolean valid = errors.isEmpty();
        return new ValidationResult(valid, errors, warnings);
    }

    @Override
    public SubmitResult submitChangeSet(String changeSetId) {
        log.info("提交变更集: {}", changeSetId);

        SubmitResult result = new SubmitResult();
        DocumentChangeSet changeSet = changeSets.get(changeSetId);
        if (changeSet == null) {
            result.setSuccess(false);
            result.setErrorCode("NOT_FOUND");
            result.setErrorMessage("变更集不存在: " + changeSetId);
            return result;
        }

        // 1. 验证
        ValidationResult validation = validateChangeSet(changeSetId);
        if (!validation.isValid()) {
            result.setSuccess(false);
            result.setErrorCode("VALIDATION_FAILED");
            result.setErrorMessage("校验失败: " + String.join("; ", validation.getErrors()));
            return result;
        }

        // 2. 获取文档最新内容
        WordDocumentAsset asset = documents.get(changeSet.getDocumentId());
        Map<Integer, byte[]> versions = versionContents.get(changeSet.getDocumentId());
        byte[] currentContent = versions.get(asset.getCurrentVersion());

        try {
            // 3. 逐个应用变更操作
            for (DocumentChangeSet.Change change : changeSet.getChanges()) {
                currentContent = applyChange(currentContent, change);
            }

            // 4. 保存新版本
            int newVersion = asset.getCurrentVersion() + 1;
            versions.put(newVersion, currentContent.clone());
            asset.bumpVersion();

            // 5. 记录版本历史
            DocumentVersion version = new DocumentVersion();
            version.setVersionNumber(newVersion);
            version.setChangeSetId(changeSetId);
            version.setSummary(changeSet.getSummary());
            version.setAuthorId(changeSet.getAuthorId());
            version.setAuthorType(changeSet.getAuthorType());
            version.setCreatedAt(Instant.now());
            versionHistories.get(changeSet.getDocumentId()).add(version);

            // 6. 更新状态
            asset.setStatus(WordDocumentAsset.DocumentStatus.READY);
            changeSet.setReviewStatus(ReviewStatus.ACCEPTED);
            changeSet.setUpdatedAt(Instant.now());

            // 7. 重新解析节点树
            WebEditingProjection newProjection = adapter.read(currentContent);
            List<DocumentNode> newNodes = newProjection.getContent();
            if (newNodes != null) {
                enrichNodesWithAnchors(newNodes, currentContent);
                nodeTrees.put(changeSet.getDocumentId(), newNodes);
                indexNodes(newNodes);
            }

            result.setSuccess(true);
            result.setChangeSetId(changeSetId);
            result.setNewVersion(newVersion);

        } catch (Exception e) {
            log.error("提交变更集失败", e);
            result.setSuccess(false);
            result.setErrorCode("APPLY_FAILED");
            result.setErrorMessage("应用变更失败: " + e.getMessage());
            changeSet.setReviewStatus(ReviewStatus.FAILED);
            changeSet.setFailureMessage(e.getMessage());
            asset.setStatus(WordDocumentAsset.DocumentStatus.ERROR);
        }

        return result;
    }

    @Override
    public DocumentChangeSet acceptChangeSet(String changeSetId) {
        log.info("接受变更集: {}", changeSetId);
        DocumentChangeSet changeSet = changeSets.get(changeSetId);
        if (changeSet == null) {
            throw new DocumentException(ErrorCode.NOT_FOUND, "变更集不存在: " + changeSetId);
        }
        changeSet.setReviewStatus(ReviewStatus.ACCEPTED);
        changeSet.setUpdatedAt(Instant.now());
        log.info("变更集已接受: {}", changeSetId);
        return changeSet;
    }

    @Override
    public void deleteChangeSet(String changeSetId) {
        log.info("删除变更集: {}", changeSetId);
        DocumentChangeSet removed = changeSets.remove(changeSetId);
        if (removed == null) {
            throw new DocumentException(ErrorCode.NOT_FOUND, "变更集不存在: " + changeSetId);
        }
        log.info("变更集已删除: {}", changeSetId);
    }

    @Override
    public DocumentChangeSet rejectChangeSet(String changeSetId, String reason) {
        DocumentChangeSet changeSet = changeSets.get(changeSetId);
        if (changeSet == null) {
            throw new DocumentException(ErrorCode.NOT_FOUND, "变更集不存在: " + changeSetId);
        }
        changeSet.setReviewStatus(ReviewStatus.REJECTED);
        changeSet.setRejectionReason(reason);
        changeSet.setUpdatedAt(Instant.now());
        return changeSet;
    }

    @Override
    public Optional<DocumentChangeSet> getChangeSet(String changeSetId) {
        return Optional.ofNullable(changeSets.get(changeSetId));
    }

    @Override
    public List<DocumentChangeSet> getChangeSets(String documentId) {
        return changeSets.values().stream()
                .filter(cs -> cs.getDocumentId().equals(documentId))
                .sorted(Comparator.comparing(DocumentChangeSet::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    @Override
    public ExportResult exportDocument(String documentId, ExportOptions options) {
        log.info("导出文档: {} (配置: 包含修订={})", documentId, options.isIncludeTrackChanges());

        ExportResult result = new ExportResult();
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) {
            result.setSuccess(false);
            result.setErrorMessage("文档不存在: " + documentId);
            return result;
        }

        Map<Integer, byte[]> versions = versionContents.get(documentId);
        byte[] latestContent = versions.get(asset.getCurrentVersion());

        if (latestContent == null) {
            result.setSuccess(false);
            result.setErrorMessage("文档内容已被清除");
            return result;
        }

        result.setSuccess(true);
        result.setFileContent(latestContent.clone());
        result.setFileName(asset.getFileName());

        // 构建导出报告
        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.EXPORT);
        report.setSuccess(true);
        report.setDocumentId(documentId);
        result.setReport(report);

        return result;
    }

    @Override
    public WordDocumentAsset restoreVersion(String documentId, int targetVersion) {
        log.info("恢复版本: 文档={}, 目标版本={}", documentId, targetVersion);

        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) {
            throw new IllegalArgumentException("文档不存在: " + documentId);
        }

        Map<Integer, byte[]> versions = versionContents.get(documentId);
        byte[] targetContent = versions.get(targetVersion);
        if (targetContent == null) {
            throw new IllegalArgumentException("版本不存在: " + targetVersion);
        }

        int newVersion = asset.getCurrentVersion() + 1;
        versions.put(newVersion, targetContent.clone());
        asset.bumpVersion();

        // 重新解析节点树
        WebEditingProjection restoreProjection = adapter.read(targetContent);
        List<DocumentNode> newNodes = restoreProjection.getContent();
        if (newNodes != null) {
            enrichNodesWithAnchors(newNodes, targetContent);
            nodeTrees.put(documentId, newNodes);
            indexNodes(newNodes);
        }

        return asset;
    }

    @Override
    public List<DocumentVersion> getVersionHistory(String documentId) {
        return versionHistories.getOrDefault(documentId, Collections.emptyList());
    }

    // ========== 内部方法 ==========

    private void indexNodes(List<DocumentNode> nodes) {
        if (nodes == null) return;
        for (DocumentNode node : nodes) {
            if (node.getNodeId() != null) {
                nodeIndex.put(node.getNodeId(), node);
            }
        }
    }

    private List<DocumentNode> flattenTree(List<DocumentNode> nodes) {
        List<DocumentNode> result = new ArrayList<>();
        if (nodes == null) return result;
        for (DocumentNode node : nodes) {
            result.add(node);
        }
        return result;
    }

    /**
     * 为节点树补充锚点信息。
     */
    private void enrichNodesWithAnchors(List<DocumentNode> nodes, byte[] fileContent) {
        // anchor 信息由 adapter 内部维护，无需额外处理
    }

    /**
     * 构建网页编辑投影。
     */
    private WebEditingProjection buildProjection(WordDocumentAsset asset,
                                                  List<DocumentNode> nodes,
                                                  byte[] fileContent) {
        WebEditingProjection projection = adapter.read(fileContent);
        projection.setDocumentId(asset.getDocumentId());
        projection.setVersion(String.valueOf(asset.getCurrentVersion()));
        return projection;
    }

    /**
     * 构建导入报告。
     */
    private ConversionReport buildImportReport(WordDocumentAsset asset,
                                                List<DocumentNode> nodes) {
        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.IMPORT);
        report.setDocumentId(asset.getDocumentId());
        report.setSuccess(true);
        report.setTotalElements(nodes.size());

        for (DocumentNode node : nodes) {
            ConversionReport.Item item = new ConversionReport.Item(
                    node.getNodeType().name(),
                    node.getText() != null ?
                            node.getText().substring(0, Math.min(50, node.getText().length())) : "",
                    SupportLevel.EDITABLE
            );
            item.setNodeId(node.getNodeId());
            report.addItem(item);
        }

        report.setEditableCount(nodes.size());
        report.setCreatedAt(Instant.now());
        return report;
    }

    /**
     * 校验单个变更操作。
     */
    private ValidationResult validateChange(String documentId, DocumentChangeSet.Change change) {
        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        if (change.getOperation() == null) {
            errors.add("变更操作类型为空");
            return new ValidationResult(false, errors, warnings);
        }

        // 节点级操作需要目标节点 ID
        if (change.getOperation() != ChangeOperation.INSERT_PARAGRAPH
                && change.getOperation() != ChangeOperation.INSERT_HEADING
                && change.getOperation() != ChangeOperation.INSERT_LIST
                && change.getOperation() != ChangeOperation.INSERT_IMAGE
                && change.getOperation() != ChangeOperation.INSERT_FOOTNOTE
                && (change.getTargetNodeId() == null || change.getTargetNodeId().isEmpty())) {
            errors.add("操作 " + change.getOperation() + " 需要目标节点 ID");
        }

        // 检查目标节点是否存在
        if (change.getTargetNodeId() != null) {
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                boolean exists = nodes.stream().anyMatch(n -> n.getNodeId().equals(change.getTargetNodeId()));
                if (!exists) {
                    warnings.add("目标节点 " + change.getTargetNodeId() + " 在当前节点树中未找到");
                }
            }
        }

        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    /**
     * 应用单个变更操作到文档内容。
     */
    private byte[] applyChange(byte[] content, DocumentChangeSet.Change change) {
        DocumentChangeSet singleChangeSet = new DocumentChangeSet("tmp-" + UUID.randomUUID().toString());
        singleChangeSet.setChanges(List.of(change));
        return adapter.applyChanges(content, singleChangeSet);
    }

    private String extractNewText(DocumentChangeSet.Change change) {
        if (change.getNewValue() != null && change.getNewValue().containsKey("text")) {
            return (String) change.getNewValue().get("text");
        }
        return "";
    }

    private int extractInt(DocumentChangeSet.Change change, String key, int defaultValue) {
        if (change.getNewValue() != null && change.getNewValue().containsKey(key)) {
            Object val = change.getNewValue().get(key);
            if (val instanceof Number) return ((Number) val).intValue();
            if (val instanceof String) return Integer.parseInt((String) val);
        }
        return defaultValue;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> mergeMaps(Map<String, Object>... maps) {
        Map<String, Object> result = new HashMap<>();
        for (Map<String, Object> map : maps) {
            if (map != null) result.putAll(map);
        }
        return result;
    }

    public Map<String, List<DocumentNode>> getNodeTrees() {
        return nodeTrees;
    }

    public Map<String, byte[]> getRawContents() {
        return rawContents;
    }
}