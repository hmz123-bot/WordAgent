package com.subtlesight.word.service.impl;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.adapter.poi.PoiDocumentAdapter;
import com.subtlesight.word.model.ConversionReport;
import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.WebEditingProjection;
import com.subtlesight.word.model.WordDocumentAsset;
import com.subtlesight.word.model.enums.ErrorCode;
import com.subtlesight.word.model.enums.SupportLevel;
import com.subtlesight.word.service.DocumentImportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * 文档导入服务实现。
 * <p>
 * 负责 .docx 文件上传、安全检查、解析和结构识别。
 * </p>
 */
public class DocumentImportServiceImpl implements DocumentImportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentImportServiceImpl.class);

    /** 最大文件大小：50MB */
    private static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;

    /** 最大解压后大小：500MB */
    private static final long MAX_UNZIPPED_SIZE = 500 * 1024 * 1024L;

    /** 最大 XML 嵌套深度 */
    private static final int MAX_XML_DEPTH = 100;

    /** 最大文件数 */
    private static final int MAX_ZIP_ENTRIES = 5000;

    private final WordDocumentAdapter adapter;

    public DocumentImportServiceImpl() {
        this.adapter = new PoiDocumentAdapter();
    }

    public DocumentImportServiceImpl(WordDocumentAdapter adapter) {
        this.adapter = adapter;
    }

    public DocumentImportServiceImpl(WordDocumentAdapter adapter, Map<String, WordDocumentAsset> documentStore) {
        this.adapter = adapter;
    }

    @Override
    public SecurityCheckResult securityCheck(byte[] fileContent, String fileName) {
        log.info("执行安全检查: {}", fileName);
        SecurityCheckResult result = new SecurityCheckResult();

        // 1. 文件大小检查
        result.setFileSize(fileContent.length);
        if (fileContent.length > MAX_FILE_SIZE) {
            result.setPassed(false);
            result.setReason("文件大小超过限制: " + fileContent.length + " > " + MAX_FILE_SIZE);
            return result;
        }

        // 2. 解压内容检查（zip bomb 防护）
        try {
            SecurityCheckResult zipResult = checkZipBomb(fileContent);
            if (!zipResult.isPassed()) {
                return zipResult;
            }
            result.setUnzippedSize(zipResult.getUnzippedSize());
        } catch (IOException e) {
            result.setPassed(false);
            result.setReason("无法解压文件: " + e.getMessage());
            return result;
        }

        // 3. 文件扩展名检查
        if (fileName == null || (!fileName.toLowerCase().endsWith(".docx") && !fileName.toLowerCase().endsWith(".docm"))) {
            result.setPassed(false);
            result.setReason("不支持的文件类型: " + fileName);
            return result;
        }

        result.setPassed(true);
        return result;
    }

    @Override
    public ParseResult parse(byte[] fileContent) {
        log.info("解析 .docx 文件");
        ParseResult result = new ParseResult();

        try {
            WebEditingProjection projection = adapter.read(fileContent);

            if (projection == null || projection.getContent() == null) {
                result.setSuccess(false);
                result.setErrorMessage("解析失败：无法读取文档内容");
                return result;
            }

            List<DocumentNode> nodes = projection.getContent();
            result.setSuccess(true);
            result.setParagraphCount((int) nodes.stream()
                    .filter(n -> n.getNodeType() == com.subtlesight.word.model.enums.NodeType.PARAGRAPH).count());
            result.setTableCount((int) nodes.stream()
                    .filter(n -> n.getNodeType() == com.subtlesight.word.model.enums.NodeType.TABLE).count());
            result.setImageCount((int) nodes.stream()
                    .filter(n -> n.getNodeType() == com.subtlesight.word.model.enums.NodeType.IMAGE).count());

        } catch (Exception e) {
            log.error("解析失败", e);
            result.setSuccess(false);
            result.setErrorMessage("解析失败: " + e.getMessage());
        }

        return result;
    }

    @Override
    public ConversionReport importDocument(WordDocumentAsset asset, byte[] fileContent) {
        log.info("开始导入文档: {}", asset.getFileName());

        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.IMPORT);
        report.setDocumentId(asset.getDocumentId());

        // 1. 安全检查
        SecurityCheckResult securityResult = securityCheck(fileContent, asset.getFileName());
        if (!securityResult.isPassed()) {
            report.setSuccess(false);
            ConversionReport.Issue issue = new ConversionReport.Issue();
            issue.setErrorCode(ErrorCode.ACCESS_DENIED);
            issue.setMessage(securityResult.getReason());
            issue.setSeverity(ConversionReport.Issue.Severity.ERROR);
            report.addIssue(issue);
            return report;
        }

        // 2. 解析
        WebEditingProjection projection = adapter.read(fileContent);
        if (projection == null || projection.getContent() == null) {
            report.setSuccess(false);
            ConversionReport.Issue issue = new ConversionReport.Issue();
            issue.setErrorCode(ErrorCode.FILE_CORRUPTED);
            issue.setMessage("解析失败：无法读取文档内容");
            issue.setSeverity(ConversionReport.Issue.Severity.ERROR);
            report.addIssue(issue);
            return report;
        }

        // 3. 构建报告
        List<DocumentNode> nodes = projection.getContent();
        report.setSuccess(true);
        report.setTotalElements(nodes.size());
        report.setEditableCount(nodes.size());

        for (DocumentNode node : nodes) {
            String text = node.getText();
            ConversionReport.Item item = new ConversionReport.Item(
                    node.getNodeType().name(),
                    text != null ? text.substring(0, Math.min(50, text.length())) : "",
                    SupportLevel.EDITABLE
            );
            item.setNodeId(node.getNodeId());
            report.addItem(item);
        }

        report.setCreatedAt(Instant.now());
        log.info("导入完成: {} 个节点", nodes.size());
        return report;
    }

    /**
     * 检查 zip bomb 攻击。
     */
    private SecurityCheckResult checkZipBomb(byte[] fileContent) throws IOException {
        SecurityCheckResult result = new SecurityCheckResult();
        long totalUnzipped = 0;
        int entryCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(fileContent))) {
            ZipEntry entry;
            byte[] buffer = new byte[4096];
            while ((entry = zis.getNextEntry()) != null) {
                entryCount++;
                if (entryCount > MAX_ZIP_ENTRIES) {
                    result.setPassed(false);
                    result.setReason("ZIP 条目数超过限制: " + entryCount);
                    return result;
                }

                // 检查深层嵌套
                String name = entry.getName();
                int depth = name.split("/").length;
                if (depth > MAX_XML_DEPTH) {
                    result.setPassed(false);
                    result.setReason("XML 嵌套深度超过限制: " + depth);
                    return result;
                }

                // 累计解压大小
                long entrySize = 0;
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    entrySize += read;
                }
                totalUnzipped += entrySize;

                if (totalUnzipped > MAX_UNZIPPED_SIZE) {
                    result.setPassed(false);
                    result.setReason("解压后总大小超过限制: " + totalUnzipped);
                    return result;
                }
            }
        }

        result.setUnzippedSize(totalUnzipped);
        result.setPassed(true);
        return result;
    }
}