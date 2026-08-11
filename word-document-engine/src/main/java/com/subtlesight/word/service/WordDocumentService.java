package com.subtlesight.word.service;

import com.subtlesight.word.model.*;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Word 文档核心服务接口。
 * <p>
 * 文档生命周期的完整管理：导入、读取、变更、导出、版本控制。
 * 对外部调用方（如控制器层）提供统一入口。
 * </p>
 */
public interface WordDocumentService {

    /**
     * 创建文档资产（仅创建记录，不解析内容）。
     */
    WordDocumentAsset createDocument(String fileName, byte[] content);

    /**
     * 导入 .docx 文件，创建 WordDocumentAsset 并生成初始投影。
     */
    ConversionReport importDocument(WordDocumentAsset asset, byte[] fileContent);

    /**
     * 获取文档列表。
     */
    List<WordDocumentAsset> listDocuments();

    /**
     * 获取文档资产信息。
     */
    WordDocumentAsset getDocument(String documentId);

    /**
     * 删除文档及其所有关联数据。
     */
    void deleteDocument(String documentId);

    /**
     * 将文档保存为草稿，持久化当前编辑状态到数据库。
     */
    WordDocumentAsset saveDraft(String documentId);

    /**
     * 保存文档编辑结果（一键保存：更新节点文本 → 创建变更集 → 提交合并到 .docx）。
     *
     * @param documentId 文档 ID
     * @param summary    变更摘要
     * @param updates    节点文本更新列表
     * @return 提交结果（含新版本号）
     */
    SubmitResult saveDocument(String documentId, String summary, List<NodeTextUpdate> updates);

    /**
     * 更新文档状态（发布/编辑/审核/归档/错误）。
     */
    void updateStatus(String documentId, WordDocumentAsset.DocumentStatus status);

    /**
     * 归档文档。
     */
    void archiveDocument(String documentId);

    /**
     * 获取文档的网页编辑投影。
     */
    WebEditingProjection getProjection(String documentId);

    /**
     * 读取文档语义节点树。
     */
    List<DocumentNode> getDocumentNodes(String documentId);

    /**
     * 根据 nodeId 查询单个节点。
     */
    DocumentNode getNode(String documentId, String nodeId);

    /**
     * 获取节点的子节点列表。
     */
    List<DocumentNode> getNodeChildren(String documentId, String nodeId);

    /**
     * 更新节点文本内容。
     */
    void updateNodeText(String documentId, String nodeId, String newText);

    /**
     * 更新节点属性。
     */
    void updateNodeAttributes(String documentId, String nodeId, Map<String, Object> attributes);

    /**
     * 删除节点。
     */
    void deleteNode(String documentId, String nodeId);

    /**
     * 在指定节点后插入新节点。
     */
    DocumentNode insertNodeAfter(String documentId, String nodeId, String text, String type);

    /**
     * 搜索文档节点（按标题、正文、文字、样式、表格位置等）。
     */
    List<DocumentNode> searchNodes(String documentId, String query, String nodeType);

    /**
     * 创建变更集草稿。
     */
    DocumentChangeSet createChangeSet(DocumentChangeSet changeSet);

    /**
     * 验证变更集是否可以提交。
     */
    ValidationResult validateChangeSet(String changeSetId);

    /**
     * 提交变更集（接受并写入事实来源 .docx）。
     * 版本不一致时返回 version_conflict。
     */
    SubmitResult submitChangeSet(String changeSetId);

    /**
     * 拒绝变更集。
     */
    DocumentChangeSet rejectChangeSet(String changeSetId, String reason);

    /**
     * 获取提案状态。
     */
    Optional<DocumentChangeSet> getChangeSet(String changeSetId);

    /**
     * 获取文档所有变更集。
     */
    List<DocumentChangeSet> getChangeSets(String documentId);

    /**
     * 接受变更集（合并到文档）。
     */
    DocumentChangeSet acceptChangeSet(String changeSetId);

    /**
     * 删除变更集。
     */
    void deleteChangeSet(String changeSetId);

    /**
     * 导出 .docx 文件。
     */
    ExportResult exportDocument(String documentId, ExportOptions options);

    /**
     * 恢复到指定版本。
     */
    WordDocumentAsset restoreVersion(String documentId, int targetVersion);

    /**
     * 获取文档版本历史。
     */
    List<DocumentVersion> getVersionHistory(String documentId);

    /**
     * 验证结果。
     */
    class ValidationResult {
        private boolean valid;
        private List<String> errors;
        private List<String> warnings;

        public ValidationResult() {
        }

        public ValidationResult(boolean valid, List<String> errors, List<String> warnings) {
            this.valid = valid;
            this.errors = errors;
            this.warnings = warnings;
        }

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public List<String> getErrors() {
            return errors;
        }

        public void setErrors(List<String> errors) {
            this.errors = errors;
        }

        public List<String> getWarnings() {
            return warnings;
        }

        public void setWarnings(List<String> warnings) {
            this.warnings = warnings;
        }
    }

    /**
     * 提交结果。
     */
    class SubmitResult {
        private boolean success;
        private String changeSetId;
        private int newVersion;
        private String errorCode;
        private String errorMessage;
        private int currentVersion;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public String getChangeSetId() {
            return changeSetId;
        }

        public void setChangeSetId(String changeSetId) {
            this.changeSetId = changeSetId;
        }

        public int getNewVersion() {
            return newVersion;
        }

        public void setNewVersion(int newVersion) {
            this.newVersion = newVersion;
        }

        public String getErrorCode() {
            return errorCode;
        }

        public void setErrorCode(String errorCode) {
            this.errorCode = errorCode;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }

        public int getCurrentVersion() {
            return currentVersion;
        }

        public void setCurrentVersion(int currentVersion) {
            this.currentVersion = currentVersion;
        }
    }

    /**
     * 导出选项。
     */
    class ExportOptions {
        private boolean includeTrackChanges;
        private String format;

        public ExportOptions() {
            this.includeTrackChanges = false;
            this.format = "docx";
        }

        public boolean isIncludeTrackChanges() {
            return includeTrackChanges;
        }

        public void setIncludeTrackChanges(boolean includeTrackChanges) {
            this.includeTrackChanges = includeTrackChanges;
        }

        public String getFormat() {
            return format;
        }

        public void setFormat(String format) {
            this.format = format;
        }
    }

    /**
     * 导出结果。
     */
    class ExportResult {
        private boolean success;
        private byte[] fileContent;
        private String fileName;
        private ConversionReport report;
        private String errorMessage;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public byte[] getFileContent() {
            return fileContent;
        }

        public void setFileContent(byte[] fileContent) {
            this.fileContent = fileContent;
        }

        public String getFileName() {
            return fileName;
        }

        public void setFileName(String fileName) {
            this.fileName = fileName;
        }

        public ConversionReport getReport() {
            return report;
        }

        public void setReport(ConversionReport report) {
            this.report = report;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }

    /**
     * 版本历史记录。
     */
    class DocumentVersion {
        private int versionNumber;
        private String changeSetId;
        private String summary;
        private String authorId;
        private String authorType;
        private java.time.Instant createdAt;
        private String storagePath;

        public int getVersionNumber() {
            return versionNumber;
        }

        public void setVersionNumber(int versionNumber) {
            this.versionNumber = versionNumber;
        }

        public String getChangeSetId() {
            return changeSetId;
        }

        public void setChangeSetId(String changeSetId) {
            this.changeSetId = changeSetId;
        }

        public String getSummary() {
            return summary;
        }

        public void setSummary(String summary) {
            this.summary = summary;
        }

        public String getAuthorId() {
            return authorId;
        }

        public void setAuthorId(String authorId) {
            this.authorId = authorId;
        }

        public String getAuthorType() {
            return authorType;
        }

        public void setAuthorType(String authorType) {
            this.authorType = authorType;
        }

        public java.time.Instant getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(java.time.Instant createdAt) {
            this.createdAt = createdAt;
        }

        public String getStoragePath() {
            return storagePath;
        }

        public void setStoragePath(String storagePath) {
            this.storagePath = storagePath;
        }
    }

    /**
     * 节点文本更新描述（用于保存文档时的批量更新）。
     */
    record NodeTextUpdate(String nodeId, String text) {
    }
}