package com.subtlesight.word.web.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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
import com.subtlesight.word.web.entity.*;
import com.subtlesight.word.web.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 数据库支持的 Word 文档服务实现。
 * <p>
 * 使用 JPA 持久化文档资产、变更集、版本历史和二进制内容，
 * 同时用 ConcurrentHashMap 做内存缓存以保持高性能读写。
 * </p>
 */
public class DatabaseWordDocumentService implements WordDocumentService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseWordDocumentService.class);
    private static final ObjectMapper mapper = new ObjectMapper();

    private final WordDocumentAdapter adapter;

    // 内存缓存（高性能读写）
    private final Map<String, WordDocumentAsset> documents;
    private final Map<String, WebEditingProjection> projections;
    private final Map<String, List<DocumentNode>> nodeTrees;
    private final Map<String, DocumentChangeSet> changeSets;
    private final Map<String, List<DocumentVersion>> versionHistories;
    private final Map<String, Map<Integer, byte[]>> versionContents;
    private final Map<String, DocumentNode> nodeIndex;
    private final Map<String, byte[]> rawContents;

    // JPA 持久化
    private final DocumentAssetRepository assetRepo;
    private final DocumentChangesetRepository changesetRepo;
    private final DocumentChangeRepository changeRepo;
    private final DocumentVersionRepository versionRepo;
    private final DocumentContentRepository contentRepo;

    public DatabaseWordDocumentService(
            WordDocumentAdapter adapter,
            DocumentAssetRepository assetRepo,
            DocumentChangesetRepository changesetRepo,
            DocumentChangeRepository changeRepo,
            DocumentVersionRepository versionRepo,
            DocumentContentRepository contentRepo) {
        this.adapter = adapter != null ? adapter : new PoiDocumentAdapter();
        this.assetRepo = assetRepo;
        this.changesetRepo = changesetRepo;
        this.changeRepo = changeRepo;
        this.versionRepo = versionRepo;
        this.contentRepo = contentRepo;

        this.documents = new ConcurrentHashMap<>();
        this.projections = new ConcurrentHashMap<>();
        this.nodeTrees = new ConcurrentHashMap<>();
        this.changeSets = new ConcurrentHashMap<>();
        this.versionHistories = new ConcurrentHashMap<>();
        this.versionContents = new ConcurrentHashMap<>();
        this.nodeIndex = new ConcurrentHashMap<>();
        this.rawContents = new ConcurrentHashMap<>();

        loadFromDatabase();
    }

    // ========== 数据库加载 ==========

    private void loadFromDatabase() {
        log.info("从数据库加载文档数据...");

        // 1. 加载文档资产
        List<DocumentAssetEntity> assets = assetRepo.findAll();
        for (DocumentAssetEntity entity : assets) {
            WordDocumentAsset asset = toDomain(entity);
            documents.put(asset.getDocumentId(), asset);
            versionHistories.putIfAbsent(asset.getDocumentId(), new ArrayList<>());
            versionContents.putIfAbsent(asset.getDocumentId(), new ConcurrentHashMap<>());
            nodeTrees.putIfAbsent(asset.getDocumentId(), new ArrayList<>());
        }
        log.info("加载了 {} 个文档资产", assets.size());

        // 2. 加载变更集
        List<DocumentChangesetEntity> csEntities = changesetRepo.findAll();
        for (DocumentChangesetEntity entity : csEntities) {
            changeSets.put(entity.getChangesetId(), toDomain(entity));
        }
        log.info("加载了 {} 个变更集", csEntities.size());

        // 3. 加载版本历史
        for (String docId : documents.keySet()) {
            List<DocumentVersionEntity> vEntities = versionRepo.findByDocumentId(docId);
            List<DocumentVersion> versions = vEntities.stream()
                    .map(this::toDomain)
                    .collect(Collectors.toList());
            versionHistories.put(docId, versions);
        }

        // 4. 加载二进制内容（RAW + VERSION）
        List<DocumentContentEntity> contents = contentRepo.findAll();
        for (DocumentContentEntity entity : contents) {
            String docId = entity.getDocumentId();
            if ("RAW".equals(entity.getContentType())) {
                rawContents.put(docId, entity.getContent());
            } else if ("VERSION".equals(entity.getContentType()) && entity.getVersionNumber() != null) {
                Map<Integer, byte[]> vers = versionContents.computeIfAbsent(docId, k -> new ConcurrentHashMap<>());
                vers.put(entity.getVersionNumber(), entity.getContent());
            }
        }
        log.info("加载了 {} 个二进制内容记录", contents.size());

        // 5. 对已就绪的文档，从 RAW 内容重建节点树
        for (Map.Entry<String, byte[]> entry : rawContents.entrySet()) {
            String docId = entry.getKey();
            byte[] content = entry.getValue();
            if (content != null && content.length > 0) {
                try {
                    WebEditingProjection projection = adapter.read(content);
                    List<DocumentNode> nodes = projection.getContent();
                    if (nodes != null && !nodes.isEmpty()) {
                        nodeTrees.put(docId, nodes);
                        indexNodes(nodes);
                    }
                } catch (Exception e) {
                    log.warn("重建文档 {} 的节点树失败: {}", docId, e.getMessage());
                }
            }
        }
        log.info("数据库加载完成");
    }

    // ========== 文档 CRUD ==========

    @Override
    @Transactional
    public WordDocumentAsset createDocument(String fileName, byte[] content) {
        String documentId = "doc-" + NodeIdGenerator.randomId();
        WordDocumentAsset asset = new WordDocumentAsset(documentId);
        asset.setFileName(fileName);
        asset.setFileSize(content != null ? content.length : 0);
        asset.setStatus(WordDocumentAsset.DocumentStatus.READY);
        documents.put(documentId, asset);

        // 持久化
        assetRepo.save(toEntity(asset));
        saveContent(documentId, "RAW", null, content);
        saveContent(documentId, "VERSION", 1, content);
        rawContents.put(documentId, content != null ? content.clone() : new byte[0]);

        versionHistories.put(documentId, new ArrayList<>());
        Map<Integer, byte[]> versions = new ConcurrentHashMap<>();
        if (content != null) versions.put(1, content.clone());
        versionContents.put(documentId, versions);
        nodeTrees.put(documentId, new ArrayList<>());

        return asset;
    }

    @Override
    @Transactional
    public ConversionReport importDocument(WordDocumentAsset asset, byte[] fileContent) {
        log.info("导入文档: {}", asset.getFileName());

        WebEditingProjection projection = adapter.read(fileContent);
        List<DocumentNode> nodes = projection.getContent() != null ? projection.getContent() : Collections.emptyList();

        asset.setStatus(WordDocumentAsset.DocumentStatus.READY);
        documents.put(asset.getDocumentId(), asset);

        // 持久化
        assetRepo.save(toEntity(asset));
        saveContent(asset.getDocumentId(), "RAW", null, fileContent);
        saveContent(asset.getDocumentId(), "VERSION", 1, fileContent);
        rawContents.put(asset.getDocumentId(), fileContent.clone());

        nodeTrees.put(asset.getDocumentId(), nodes);
        indexNodes(nodes);

        projection.setDocumentId(asset.getDocumentId());
        projection.setVersion(String.valueOf(asset.getCurrentVersion()));
        projections.put(asset.getDocumentId(), projection);

        versionHistories.put(asset.getDocumentId(), new ArrayList<>());
        Map<Integer, byte[]> versions = new ConcurrentHashMap<>();
        versions.put(1, fileContent.clone());
        versionContents.put(asset.getDocumentId(), versions);

        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.IMPORT);
        report.setDocumentId(asset.getDocumentId());
        report.setSuccess(true);
        report.setTotalElements(nodes.size());
        report.setEditableCount(nodes.size());
        for (DocumentNode node : nodes) {
            ConversionReport.Item item = new ConversionReport.Item(
                    node.getNodeType().name(),
                    node.getText() != null ? node.getText().substring(0, Math.min(50, node.getText().length())) : "",
                    SupportLevel.EDITABLE);
            item.setNodeId(node.getNodeId());
            report.addItem(item);
        }
        report.setCreatedAt(Instant.now());
        return report;
    }

    @Override
    public List<WordDocumentAsset> listDocuments() {
        return new ArrayList<>(documents.values());
    }

    @Override
    public WordDocumentAsset getDocument(String documentId) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + documentId);
        return asset;
    }

    @Override
    @Transactional
    public void deleteDocument(String documentId) {
        documents.remove(documentId);
        projections.remove(documentId);
        nodeTrees.remove(documentId);
        versionHistories.remove(documentId);
        versionContents.remove(documentId);
        rawContents.remove(documentId);

        List<DocumentNode> docNodes = nodeTrees.get(documentId);
        if (docNodes != null) {
            for (DocumentNode n : docNodes) nodeIndex.remove(n.getNodeId());
        }
        changeSets.values().removeIf(cs -> cs.getDocumentId().equals(documentId));

        // 删除数据库记录
        assetRepo.deleteByDocumentId(documentId);
        changesetRepo.deleteByDocumentId(documentId);
        versionRepo.deleteByDocumentId(documentId);
        contentRepo.deleteByDocumentId(documentId);
    }

    @Override
    @Transactional
    public WordDocumentAsset saveDraft(String documentId) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + documentId);

        asset.setStatus(WordDocumentAsset.DocumentStatus.DRAFT);
        asset.setUpdatedAt(Instant.now());

        // 持久化到数据库
        DocumentAssetEntity entity = assetRepo.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("数据库中未找到文档: " + documentId));
        entity.setStatus("DRAFT");
        entity.setUpdatedAt(Instant.now());
        assetRepo.save(entity);

        log.info("文档 {} 已保存为草稿", documentId);
        return asset;
    }

    @Override
    @Transactional
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
            DocumentNode node;
            try {
                node = getNode(documentId, update.nodeId());
            } catch (IllegalArgumentException e) {
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

        // 2. 创建变更集并持久化
        createChangeSet(changeSet);

        // 3. 提交变更集（应用到二进制 .docx，创建版本，重建节点树并持久化）
        return submitChangeSet(changeSet.getChangeSetId());
    }

    @Override
    public void updateStatus(String documentId, WordDocumentAsset.DocumentStatus status) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + documentId);

        asset.setStatus(status);
        asset.setUpdatedAt(Instant.now());

        // 持久化
        DocumentAssetEntity entity = assetRepo.findByDocumentId(documentId)
                .orElseThrow(() -> new IllegalArgumentException("数据库中未找到文档: " + documentId));
        entity.setStatus(status.name());
        entity.setUpdatedAt(asset.getUpdatedAt());
        assetRepo.save(entity);

        log.info("文档 {} 状态已更新为 {}", documentId, status);
    }

    @Override
    public void archiveDocument(String documentId) {
        updateStatus(documentId, WordDocumentAsset.DocumentStatus.ARCHIVED);
    }

    // ========== 节点操作 ==========

    @Override
    public WebEditingProjection getProjection(String documentId) {
        WebEditingProjection projection = projections.get(documentId);
        if (projection == null) throw new IllegalArgumentException("文档投影不存在: " + documentId);

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
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                node = flattenTree(nodes).stream()
                        .filter(n -> n.getNodeId().equals(nodeId))
                        .findFirst().orElse(null);
            }
        }
        if (node == null) throw new IllegalArgumentException("节点不存在: " + nodeId);
        return node;
    }

    @Override
    public List<DocumentNode> getNodeChildren(String documentId, String nodeId) {
        DocumentNode parent = getNode(documentId, nodeId);
        List<DocumentNode> children = parent.getChildren();
        return children != null ? children : Collections.emptyList();
    }

    @Override
    public void updateNodeText(String documentId, String nodeId, String newText) {
        DocumentNode node = getNode(documentId, nodeId);
        node.setText(newText);
        log.debug("节点文本已更新: nodeId={}", nodeId);
    }

    @Override
    public void updateNodeAttributes(String documentId, String nodeId, Map<String, Object> attributes) {
        DocumentNode node = getNode(documentId, nodeId);
        if (attributes != null) {
            attributes.forEach((k, v) -> node.getAttributes().merge(k, v, (old, val) -> val));
        }
    }

    @Override
    public void deleteNode(String documentId, String nodeId) {
        DocumentNode node = nodeIndex.remove(nodeId);
        if (node == null) {
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                nodes.removeIf(n -> n.getNodeId().equals(nodeId));
                for (DocumentNode n : nodes) n.getChildren().removeIf(c -> c.getNodeId().equals(nodeId));
            }
        } else {
            List<DocumentNode> nodes = nodeTrees.get(documentId);
            if (nodes != null) {
                for (DocumentNode n : nodes) n.getChildren().removeIf(c -> c.getNodeId().equals(nodeId));
            }
        }
    }

    @Override
    public DocumentNode insertNodeAfter(String documentId, String nodeId, String text, String type) {
        String newNodeId = documentId + "_" + NodeIdGenerator.randomId();
        DocumentNode newNode = new DocumentNode();
        newNode.setNodeId(newNodeId);
        newNode.setNodeType(NodeType.valueOf(type.toUpperCase()));
        newNode.setText(text);
        nodeIndex.put(newNodeId, newNode);

        List<DocumentNode> nodes = nodeTrees.get(documentId);
        if (nodes != null) {
            int refIdx = -1;
            for (int i = 0; i < nodes.size(); i++) {
                if (nodes.get(i).getNodeId().equals(nodeId)) { refIdx = i; break; }
            }
            if (refIdx >= 0) nodes.add(refIdx + 1, newNode);
            else nodes.add(newNode);
        }
        return newNode;
    }

    @Override
    public List<DocumentNode> searchNodes(String documentId, String query, String nodeType) {
        List<DocumentNode> nodes = nodeTrees.get(documentId);
        if (nodes == null) return Collections.emptyList();
        return nodes.stream().filter(n -> {
            if (query != null && !query.isEmpty()) {
                String text = n.getText();
                if (text == null || !text.toLowerCase().contains(query.toLowerCase())) return false;
            }
            if (nodeType != null && !nodeType.isEmpty())
                return n.getNodeType().name().equalsIgnoreCase(nodeType);
            return true;
        }).collect(Collectors.toList());
    }

    // ========== 变更集操作 ==========

    @Override
    @Transactional
    public DocumentChangeSet createChangeSet(DocumentChangeSet changeSet) {
        log.info("创建变更集: {}", changeSet.getChangeSetId());
        WordDocumentAsset asset = documents.get(changeSet.getDocumentId());
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + changeSet.getDocumentId());

        if (changeSet.getExpectedVersion() != asset.getCurrentVersion()) {
            throw new VersionConflictException(changeSet.getDocumentId(),
                    changeSet.getExpectedVersion(), asset.getCurrentVersion());
        }

        changeSet.setReviewStatus(ReviewStatus.PENDING_REVIEW);
        changeSet.setCreatedAt(Instant.now());
        changeSet.setUpdatedAt(Instant.now());
        changeSets.put(changeSet.getChangeSetId(), changeSet);

        // 持久化变更集
        changesetRepo.save(toEntity(changeSet));
        return changeSet;
    }

    @Override
    public ValidationResult validateChangeSet(String changeSetId) {
        DocumentChangeSet changeSet = changeSets.get(changeSetId);
        if (changeSet == null) throw new IllegalArgumentException("变更集不存在: " + changeSetId);

        List<String> errors = new ArrayList<>();
        List<String> warnings = new ArrayList<>();

        WordDocumentAsset asset = documents.get(changeSet.getDocumentId());
        if (asset == null) {
            errors.add("文档不存在: " + changeSet.getDocumentId());
            return new ValidationResult(false, errors, warnings);
        }
        if (changeSet.getExpectedVersion() != asset.getCurrentVersion()) {
            errors.add("版本冲突: 期望 " + changeSet.getExpectedVersion()
                    + ", 当前 " + asset.getCurrentVersion());
            return new ValidationResult(false, errors, warnings);
        }
        for (DocumentChangeSet.Change change : changeSet.getChanges()) {
            if (change.getTargetNodeId() != null && !change.getTargetNodeId().isEmpty()) {
                List<DocumentNode> nodes = nodeTrees.get(changeSet.getDocumentId());
                if (nodes != null) {
                    boolean exists = nodes.stream().anyMatch(n -> n.getNodeId().equals(change.getTargetNodeId()));
                    if (!exists) warnings.add("目标节点 " + change.getTargetNodeId() + " 未找到");
                }
            }
        }
        return new ValidationResult(errors.isEmpty(), errors, warnings);
    }

    @Override
    @Transactional
    public SubmitResult submitChangeSet(String changeSetId) {
        log.info("提交变更集: {}", changeSetId);
        SubmitResult result = new SubmitResult();
        DocumentChangeSet changeSet = changeSets.get(changeSetId);
        if (changeSet == null) {
            result.setSuccess(false); result.setErrorCode("NOT_FOUND");
            result.setErrorMessage("变更集不存在: " + changeSetId);
            return result;
        }

        ValidationResult validation = validateChangeSet(changeSetId);
        if (!validation.isValid()) {
            result.setSuccess(false); result.setErrorCode("VALIDATION_FAILED");
            result.setErrorMessage("校验失败: " + String.join("; ", validation.getErrors()));
            return result;
        }

        WordDocumentAsset asset = documents.get(changeSet.getDocumentId());
        Map<Integer, byte[]> versions = versionContents.get(changeSet.getDocumentId());
        byte[] currentContent = versions.get(asset.getCurrentVersion());

        try {
            for (DocumentChangeSet.Change change : changeSet.getChanges()) {
                DocumentChangeSet single = new DocumentChangeSet("tmp-" + UUID.randomUUID());
                single.setChanges(List.of(change));
                currentContent = adapter.applyChanges(currentContent, single);
            }

            int newVersion = asset.getCurrentVersion() + 1;
            versions.put(newVersion, currentContent.clone());
            asset.bumpVersion();
            asset.setStatus(WordDocumentAsset.DocumentStatus.READY);

            // 持久化
            assetRepo.save(toEntity(asset));
            saveContent(asset.getDocumentId(), "VERSION", newVersion, currentContent);
            saveContent(asset.getDocumentId(), "RAW", null, currentContent);

            DocumentVersion version = new DocumentVersion();
            version.setVersionNumber(newVersion);
            version.setChangeSetId(changeSetId);
            version.setSummary(changeSet.getSummary());
            version.setAuthorId(changeSet.getAuthorId());
            version.setAuthorType(changeSet.getAuthorType());
            version.setCreatedAt(Instant.now());
            versionHistories.get(changeSet.getDocumentId()).add(version);
            versionRepo.save(toEntity(version, changeSet.getDocumentId()));

            changeSet.setReviewStatus(ReviewStatus.ACCEPTED);
            changeSet.setUpdatedAt(Instant.now());
            changesetRepo.save(toEntity(changeSet));

            // 重建节点树
            WebEditingProjection newProjection = adapter.read(currentContent);
            List<DocumentNode> newNodes = newProjection.getContent();
            if (newNodes != null) {
                nodeTrees.put(changeSet.getDocumentId(), newNodes);
                indexNodes(newNodes);
            }

            result.setSuccess(true);
            result.setChangeSetId(changeSetId);
            result.setNewVersion(newVersion);
        } catch (Exception e) {
            log.error("提交变更集失败", e);
            result.setSuccess(false); result.setErrorCode("APPLY_FAILED");
            result.setErrorMessage("应用变更失败: " + e.getMessage());
            changeSet.setReviewStatus(ReviewStatus.FAILED);
            changeSet.setFailureMessage(e.getMessage());
            asset.setStatus(WordDocumentAsset.DocumentStatus.ERROR);
            changesetRepo.save(toEntity(changeSet));
        }
        return result;
    }

    @Override
    @Transactional
    public DocumentChangeSet acceptChangeSet(String changeSetId) {
        DocumentChangeSet cs = changeSets.get(changeSetId);
        if (cs == null) throw new DocumentException(ErrorCode.NOT_FOUND, "变更集不存在: " + changeSetId);
        cs.setReviewStatus(ReviewStatus.ACCEPTED);
        cs.setUpdatedAt(Instant.now());
        changesetRepo.save(toEntity(cs));
        return cs;
    }

    @Override
    @Transactional
    public void deleteChangeSet(String changeSetId) {
        DocumentChangeSet removed = changeSets.remove(changeSetId);
        if (removed == null) throw new DocumentException(ErrorCode.NOT_FOUND, "变更集不存在: " + changeSetId);
        changesetRepo.deleteByChangesetId(changeSetId);
    }

    @Override
    @Transactional
    public DocumentChangeSet rejectChangeSet(String changeSetId, String reason) {
        DocumentChangeSet cs = changeSets.get(changeSetId);
        if (cs == null) throw new DocumentException(ErrorCode.NOT_FOUND, "变更集不存在: " + changeSetId);
        cs.setReviewStatus(ReviewStatus.REJECTED);
        cs.setRejectionReason(reason);
        cs.setUpdatedAt(Instant.now());
        changesetRepo.save(toEntity(cs));
        return cs;
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

    // ========== 导出与版本 ==========

    @Override
    public ExportResult exportDocument(String documentId, ExportOptions options) {
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

        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.EXPORT);
        report.setSuccess(true);
        report.setDocumentId(documentId);
        result.setReport(report);
        return result;
    }

    @Override
    @Transactional
    public WordDocumentAsset restoreVersion(String documentId, int targetVersion) {
        WordDocumentAsset asset = documents.get(documentId);
        if (asset == null) throw new IllegalArgumentException("文档不存在: " + documentId);

        Map<Integer, byte[]> versions = versionContents.get(documentId);
        byte[] targetContent = versions.get(targetVersion);
        if (targetContent == null) throw new IllegalArgumentException("版本不存在: " + targetVersion);

        int newVersion = asset.getCurrentVersion() + 1;
        versions.put(newVersion, targetContent.clone());
        asset.bumpVersion();

        assetRepo.save(toEntity(asset));
        saveContent(documentId, "VERSION", newVersion, targetContent);
        saveContent(documentId, "RAW", null, targetContent);

        WebEditingProjection restoreProjection = adapter.read(targetContent);
        List<DocumentNode> newNodes = restoreProjection.getContent();
        if (newNodes != null) {
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
            if (node.getNodeId() != null) nodeIndex.put(node.getNodeId(), node);
        }
    }

    private List<DocumentNode> flattenTree(List<DocumentNode> nodes) {
        List<DocumentNode> result = new ArrayList<>();
        if (nodes == null) return result;
        result.addAll(nodes);
        return result;
    }

    private WebEditingProjection buildProjection(WordDocumentAsset asset,
                                                  List<DocumentNode> nodes, byte[] fileContent) {
        WebEditingProjection proj = adapter.read(fileContent);
        proj.setDocumentId(asset.getDocumentId());
        proj.setVersion(String.valueOf(asset.getCurrentVersion()));
        return proj;
    }

    // ========== JPA 辅助方法 ==========

    @Transactional
    private void saveContent(String documentId, String type, Integer version, byte[] data) {
        if (data == null) return;
        DocumentContentEntity entity = new DocumentContentEntity();
        entity.setDocumentId(documentId);
        entity.setContentType(type);
        entity.setVersionNumber(version);
        entity.setContent(data.clone());
        contentRepo.save(entity);
    }

    // ========== 领域/DTO ↔ Entity 转换 ==========

    private DocumentAssetEntity toEntity(WordDocumentAsset a) {
        DocumentAssetEntity e = assetRepo.findByDocumentId(a.getDocumentId())
                .orElse(new DocumentAssetEntity());
        e.setDocumentId(a.getDocumentId());
        e.setFileName(a.getFileName());
        e.setFileSize(a.getFileSize());
        e.setFileHash(a.getFileHash());
        e.setCurrentVersion(a.getCurrentVersion());
        e.setStoragePath(a.getStoragePath());
        e.setStatus(a.getStatus() != null ? a.getStatus().name() : null);
        e.setOwnerId(a.getOwnerId());
        e.setWorkspaceId(a.getWorkspaceId());
        e.setCreatedAt(a.getCreatedAt());
        e.setUpdatedAt(a.getUpdatedAt());
        return e;
    }

    private WordDocumentAsset toDomain(DocumentAssetEntity e) {
        WordDocumentAsset a = new WordDocumentAsset(e.getDocumentId());
        a.setFileName(e.getFileName());
        a.setFileSize(e.getFileSize() != null ? e.getFileSize() : 0);
        a.setFileHash(e.getFileHash());
        a.setCurrentVersion(e.getCurrentVersion() != null ? e.getCurrentVersion() : 1);
        a.setStoragePath(e.getStoragePath());
        a.setCreatedAt(e.getCreatedAt());
        a.setUpdatedAt(e.getUpdatedAt());
        if (e.getStatus() != null) {
            a.setStatus(WordDocumentAsset.DocumentStatus.valueOf(e.getStatus()));
        }
        a.setOwnerId(e.getOwnerId());
        a.setWorkspaceId(e.getWorkspaceId());
        return a;
    }

    private DocumentChangesetEntity toEntity(DocumentChangeSet cs) {
        DocumentChangesetEntity e = changesetRepo.findByChangesetId(cs.getChangeSetId())
                .orElse(new DocumentChangesetEntity());
        e.setChangesetId(cs.getChangeSetId());
        e.setDocumentId(cs.getDocumentId());
        e.setExpectedVersion(cs.getExpectedVersion());
        e.setIdempotencyKey(cs.getIdempotencyKey());
        e.setSummary(cs.getSummary());
        e.setAuthorId(cs.getAuthorId());
        e.setAuthorType(cs.getAuthorType());
        e.setReviewStatus(cs.getReviewStatus() != null ? cs.getReviewStatus().name() : null);
        e.setRejectionReason(cs.getRejectionReason());
        e.setFailureMessage(cs.getFailureMessage());
        e.setCreatedAt(cs.getCreatedAt());
        e.setUpdatedAt(cs.getUpdatedAt());

        // 转换关联的 Change 列表（复用托管集合，避免唯一键冲突）
        List<DocumentChangeEntity> managedChanges = e.getChanges();
        // 构建已有实体索引
        java.util.Map<String, DocumentChangeEntity> existingMap = new java.util.HashMap<>();
        for (DocumentChangeEntity ce : managedChanges) {
            if (ce.getChangeId() != null) existingMap.put(ce.getChangeId(), ce);
        }

        java.util.Set<String> retainedIds = new java.util.HashSet<>();
        List<DocumentChangeEntity> entitiesToAdd = new ArrayList<>();
        for (DocumentChangeSet.Change ch : cs.getChanges()) {
            DocumentChangeEntity ce = existingMap.get(ch.getChangeId());
            if (ce == null) {
                ce = new DocumentChangeEntity();
                entitiesToAdd.add(ce);
            }
            retainedIds.add(ch.getChangeId());
            ce.setChangeId(ch.getChangeId());
            ce.setChangeset(e);
            ce.setOperation(ch.getOperation() != null ? ch.getOperation().name() : null);
            ce.setTargetNodeId(ch.getTargetNodeId());
            ce.setTargetNodeType(ch.getTargetNodeType());
            ce.setPosition(ch.getPosition());
            ce.setContext(ch.getContext());
            ce.setOldValueJson(toJson(ch.getOldValue()));
            ce.setNewValueJson(toJson(ch.getNewValue()));
        }
        // 移除不在新变更列表中的实体
        managedChanges.removeIf(ce -> ce.getChangeId() != null && !retainedIds.contains(ce.getChangeId()));
        // 添加新增的实体到托管集合
        managedChanges.addAll(entitiesToAdd);
        return e;
    }

    private DocumentChangeSet toDomain(DocumentChangesetEntity e) {
        DocumentChangeSet cs = new DocumentChangeSet(e.getChangesetId());
        cs.setDocumentId(e.getDocumentId());
        cs.setExpectedVersion(e.getExpectedVersion() != null ? e.getExpectedVersion() : 0);
        cs.setIdempotencyKey(e.getIdempotencyKey());
        cs.setSummary(e.getSummary());
        cs.setAuthorId(e.getAuthorId());
        cs.setAuthorType(e.getAuthorType());
        if (e.getReviewStatus() != null) cs.setReviewStatus(ReviewStatus.valueOf(e.getReviewStatus()));
        cs.setRejectionReason(e.getRejectionReason());
        cs.setFailureMessage(e.getFailureMessage());
        cs.setCreatedAt(e.getCreatedAt());
        cs.setUpdatedAt(e.getUpdatedAt());

        List<DocumentChangeSet.Change> changes = new ArrayList<>();
        for (DocumentChangeEntity ce : e.getChanges()) {
            DocumentChangeSet.Change ch = new DocumentChangeSet.Change();
            ch.setChangeId(ce.getChangeId());
            if (ce.getOperation() != null) ch.setOperation(ChangeOperation.valueOf(ce.getOperation()));
            ch.setTargetNodeId(ce.getTargetNodeId());
            ch.setTargetNodeType(ce.getTargetNodeType());
            ch.setPosition(ce.getPosition());
            ch.setContext(ce.getContext());
            ch.setOldValue(fromJson(ce.getOldValueJson()));
            ch.setNewValue(fromJson(ce.getNewValueJson()));
            changes.add(ch);
        }
        cs.setChanges(changes);
        return cs;
    }

    private DocumentVersion toDomain(DocumentVersionEntity e) {
        DocumentVersion v = new DocumentVersion();
        v.setVersionNumber(e.getVersionNumber() != null ? e.getVersionNumber() : 1);
        v.setChangeSetId(e.getChangesetId());
        v.setSummary(e.getSummary());
        v.setAuthorId(e.getAuthorId());
        v.setAuthorType(e.getAuthorType());
        v.setCreatedAt(e.getCreatedAt());
        v.setStoragePath(e.getStoragePath());
        return v;
    }

    private DocumentVersionEntity toEntity(DocumentVersion v, String documentId) {
        DocumentVersionEntity e = new DocumentVersionEntity();
        e.setDocumentId(documentId);
        e.setVersionNumber(v.getVersionNumber());
        e.setChangesetId(v.getChangeSetId());
        e.setSummary(v.getSummary());
        e.setAuthorId(v.getAuthorId());
        e.setAuthorType(v.getAuthorType());
        e.setCreatedAt(v.getCreatedAt());
        e.setStoragePath(v.getStoragePath());
        return e;
    }

    @SuppressWarnings("unchecked")
    private String toJson(Map<String, Object> map) {
        if (map == null) return null;
        try { return mapper.writeValueAsString(map); }
        catch (JsonProcessingException ex) { return "{}"; }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> fromJson(String json) {
        if (json == null || json.isEmpty()) return Collections.emptyMap();
        try { return mapper.readValue(json, Map.class); }
        catch (JsonProcessingException ex) { return Collections.emptyMap(); }
    }

    // ========== 暴露给其他 Bean 的内部映射 ==========

    /** 共享的文档资产映射（供 ImportService / SearchService 使用） */
    public Map<String, WordDocumentAsset> getDocuments() {
        return documents;
    }

    public Map<String, List<DocumentNode>> getNodeTrees() {
        return nodeTrees;
    }

    public Map<String, byte[]> getRawContents() {
        return rawContents;
    }
}
