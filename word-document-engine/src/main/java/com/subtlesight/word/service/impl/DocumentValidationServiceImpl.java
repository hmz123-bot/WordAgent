package com.subtlesight.word.service.impl;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.adapter.poi.PoiDocumentAdapter;
import com.subtlesight.word.model.ConversionReport;
import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.NodeAnchor;
import com.subtlesight.word.model.WebEditingProjection;
import com.subtlesight.word.model.enums.ErrorCode;
import com.subtlesight.word.service.DocumentValidationService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.*;
import java.util.zip.ZipInputStream;

/**
 * 文档校验服务实现。
 * <p>
 * 每次导入、变更提交和导出都需执行校验。
 * 对应 PRD 7.3.E 节结构化错误。
 * </p>
 */
public class DocumentValidationServiceImpl implements DocumentValidationService {

    private static final Logger log = LoggerFactory.getLogger(DocumentValidationServiceImpl.class);

    private final WordDocumentAdapter adapter;

    public DocumentValidationServiceImpl() {
        this.adapter = new PoiDocumentAdapter();
    }

    public DocumentValidationServiceImpl(WordDocumentAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public ValidationResult validateOOXMLStructure(byte[] fileContent) {
        log.info("执行 OOXML 结构校验");
        List<ConversionReport.Issue> issues = new ArrayList<>();

        // 1. ZIP 完整性检查
        try {
            boolean validZip = checkZipIntegrity(fileContent);
            if (!validZip) {
                issues.add(createIssue(ErrorCode.FILE_CORRUPTED,
                        "ZIP 文件结构损坏", "请重新上传文档", ConversionReport.Issue.Severity.ERROR));
            }
        } catch (Exception e) {
            issues.add(createIssue(ErrorCode.FILE_CORRUPTED,
                    "ZIP 文件读取失败: " + e.getMessage(),
                    "请检查文件是否完整", ConversionReport.Issue.Severity.ERROR));
        }

        // 2. POI 结构校验
        try {
            ConversionReport validationReport = adapter.validate(fileContent);
            if (!validationReport.isSuccess()) {
                for (ConversionReport.Issue issue : validationReport.getIssues()) {
                    issues.add(createIssue(ErrorCode.VALIDATION_FAILED,
                            issue.getMessage(), "建议检查文档内容", ConversionReport.Issue.Severity.WARNING));
                }
            }
        } catch (Exception e) {
            issues.add(createIssue(ErrorCode.FILE_CORRUPTED,
                    "POI 结构校验失败: " + e.getMessage(),
                    "请检查 OOXML 是否符合规范", ConversionReport.Issue.Severity.ERROR));
        }

        ValidationResult result = new ValidationResult();
        result.setValid(issues.stream().noneMatch(i -> i.getSeverity() == ConversionReport.Issue.Severity.ERROR));
        result.setIssues(issues);
        return result;
    }

    @Override
    public ValidationResult validateRelationships(byte[] fileContent) {
        log.info("校验关系引用");
        List<ConversionReport.Issue> issues = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileContent))) {
            Set<String> allEntries = new HashSet<>();
            var entry = zis.getNextEntry();
            while (entry != null) {
                allEntries.add(entry.getName());
                entry = zis.getNextEntry();
            }

            // 检查必需部件
            String[] requiredParts = {"word/document.xml", "[Content_Types].xml"};
            for (String part : requiredParts) {
                if (!allEntries.contains(part)) {
                    issues.add(createIssue(ErrorCode.FILE_CORRUPTED,
                            "缺少必需部件: " + part,
                            "请确认文件是有效的 .docx 格式", ConversionReport.Issue.Severity.ERROR));
                }
            }

        } catch (Exception e) {
            issues.add(createIssue(ErrorCode.FILE_CORRUPTED,
                    "关系检查失败: " + e.getMessage(),
                    null, ConversionReport.Issue.Severity.ERROR));
        }

        ValidationResult result = new ValidationResult();
        result.setValid(issues.isEmpty());
        result.setIssues(issues);
        return result;
    }

    @Override
    public ValidationResult validateNodeAnchors(String documentId) {
        log.info("校验节点锚点完整性: {}", documentId);
        List<ConversionReport.Issue> issues = new ArrayList<>();

        // 注意：此方法需要在完整服务上下文（有节点树）中调用
        // 此处留下桩实现，实际锚点校验在 WordDocumentServiceImpl 中通过 version 检查完成
        ValidationResult result = new ValidationResult();
        result.setValid(true);
        result.setIssues(issues);
        return result;
    }

    @Override
    public ValidationResult validateSupportMatrix(byte[] fileContent) {
        log.info("校验支持矩阵");
        List<ConversionReport.Issue> issues = new ArrayList<>();

        // 检查文档中是否有超出支持范围的元素
        try {
            WebEditingProjection projection = adapter.read(fileContent);
            if (projection != null && projection.getContent() != null) {
                for (DocumentNode node : projection.getContent()) {
                    // 检查特殊元素
                    switch (node.getNodeType()) {
                        case EQUATION -> issues.add(createIssue(ErrorCode.UNSUPPORTED_OPERATION,
                                "公式节点仅支持只读保留: " + node.getNodeId(),
                                "公式编辑功能尚在开发中", ConversionReport.Issue.Severity.WARNING));
                        case CHART -> issues.add(createIssue(ErrorCode.UNSUPPORTED_OPERATION,
                                "图表节点仅支持只读保留: " + node.getNodeId(),
                                "图表编辑功能尚在开发中", ConversionReport.Issue.Severity.WARNING));
                        default -> {
                            // 支持的类型
                        }
                    }
                }
            }
        } catch (Exception e) {
            issues.add(createIssue(ErrorCode.VALIDATION_FAILED,
                    "支持矩阵检查失败: " + e.getMessage(),
                    null, ConversionReport.Issue.Severity.ERROR));
        }

        ValidationResult result = new ValidationResult();
        result.setValid(issues.stream().noneMatch(i -> i.getSeverity() == ConversionReport.Issue.Severity.ERROR));
        result.setIssues(issues);
        return result;
    }

    @Override
    public ValidationResult validateContentSecurity(byte[] fileContent) {
        log.info("校验内容安全");
        List<ConversionReport.Issue> issues = new ArrayList<>();

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileContent))) {
            Set<String> externalRefs = new HashSet<>();
            var entry = zis.getNextEntry();

            while (entry != null) {
                String name = entry.getName().toLowerCase();
                // 检查 XML 中的外部引用
                if (name.endsWith(".xml") || name.endsWith(".rels")) {
                    byte[] content = readEntryBytes(zis);
                    String text = new String(content, "UTF-8");

                    // 检查外部链接
                    if (text.contains("External") || text.contains("external")) {
                        // 解析外部引用
                        int idx = text.indexOf("Target=\"");
                        while (idx >= 0) {
                            int endIdx = text.indexOf("\"", idx + 8);
                            if (endIdx > idx) {
                                String target = text.substring(idx + 8, endIdx);
                                if (target.startsWith("http://") || target.startsWith("https://")) {
                                    externalRefs.add(target);
                                }
                            }
                            idx = text.indexOf("Target=\"", endIdx);
                        }
                    }
                }
                entry = zis.getNextEntry();
            }

            // 报告外部链接
            for (String ref : externalRefs) {
                issues.add(createIssue(ErrorCode.MALICIOUS_CONTENT,
                        "检测到外部链接: " + ref,
                        "请确认链接来源可信", ConversionReport.Issue.Severity.INFO));
            }

        } catch (Exception e) {
            issues.add(createIssue(ErrorCode.VALIDATION_FAILED,
                    "安全检查失败: " + e.getMessage(),
                    null, ConversionReport.Issue.Severity.ERROR));
        }

        ValidationResult result = new ValidationResult();
        result.setValid(issues.stream().noneMatch(i -> i.getSeverity() == ConversionReport.Issue.Severity.ERROR));
        result.setIssues(issues);
        return result;
    }

    @Override
    public ValidationResult validateAll(byte[] fileContent, String documentId) {
        log.info("执行完整校验: {}", documentId);
        List<ConversionReport.Issue> allIssues = new ArrayList<>();

        // 依次执行所有校验
        consumeIssues(allIssues, validateOOXMLStructure(fileContent));
        consumeIssues(allIssues, validateRelationships(fileContent));
        consumeIssues(allIssues, validateSupportMatrix(fileContent));
        consumeIssues(allIssues, validateContentSecurity(fileContent));

        // 节点锚点校验需要文档ID，在有 documentId 时执行
        if (documentId != null) {
            consumeIssues(allIssues, validateNodeAnchors(documentId));
        }

        ValidationResult result = new ValidationResult();
        result.setValid(allIssues.stream().noneMatch(i -> i.getSeverity() == ConversionReport.Issue.Severity.ERROR));
        result.setIssues(allIssues);
        return result;
    }

    // ========== 辅助方法 ==========

    private boolean checkZipIntegrity(byte[] content) {
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            return zis.getNextEntry() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private byte[] readEntryBytes(ZipInputStream zis) throws java.io.IOException {
        java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[4096];
        int read;
        while ((read = zis.read(buffer)) != -1) {
            baos.write(buffer, 0, read);
        }
        return baos.toByteArray();
    }

    private ConversionReport.Issue createIssue(ErrorCode code, String message,
                                                String suggestion,
                                                ConversionReport.Issue.Severity severity) {
        ConversionReport.Issue issue = new ConversionReport.Issue();
        issue.setErrorCode(code);
        issue.setMessage(message);
        issue.setSuggestion(suggestion);
        issue.setSeverity(severity);
        return issue;
    }

    private void consumeIssues(List<ConversionReport.Issue> target, ValidationResult result) {
        if (result.getIssues() != null) {
            target.addAll(result.getIssues());
        }
    }
}