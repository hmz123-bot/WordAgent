package com.subtlesight.word.service.impl;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.adapter.poi.PoiDocumentAdapter;
import com.subtlesight.word.model.ConversionReport;
import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.WebEditingProjection;
import com.subtlesight.word.model.WordDocumentAsset;
import com.subtlesight.word.model.enums.SupportLevel;
import com.subtlesight.word.service.DocumentExportService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.time.Instant;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文档导出服务实现。
 * <p>
 * 负责从 Word 事实来源生成 .docx 下载副本。
 * 支持"干净稿"和"带审阅标记"两种模式。
 * </p>
 */
public class DocumentExportServiceImpl implements DocumentExportService {

    private static final Logger log = LoggerFactory.getLogger(DocumentExportServiceImpl.class);

    private final WordDocumentAdapter adapter;

    public DocumentExportServiceImpl() {
        this.adapter = new PoiDocumentAdapter();
    }

    public DocumentExportServiceImpl(WordDocumentAdapter adapter) {
        this.adapter = adapter;
    }

    @Override
    public ExportResult export(String documentId, ExportOptions options) {
        log.info("导出文档: {} (选项: 包含修订={})", documentId, options.isIncludeTrackChanges());

        ExportResult result = new ExportResult();
        result.setSuccess(false);
        result.setErrorMessage("导出需要配合 WordDocumentService 使用，请调用 WordDocumentService.exportDocument()");
        return result;
    }

    @Override
    public ValidationResult validateExport(byte[] fileContent) {
        log.info("校验导出文件");

        ValidationResult result = new ValidationResult();

        // 1. 检查文件是否为空
        if (fileContent == null || fileContent.length == 0) {
            result.setValid(false);
            result.setMessage("导出文件为空");
            return result;
        }

        // 2. OOXML 结构校验
        try {
            ConversionReport validationReport = adapter.validate(fileContent);
            if (!validationReport.isSuccess()) {
                String errors = validationReport.getIssues().stream()
                    .map(ConversionReport.Issue::getMessage)
                    .collect(Collectors.joining("; "));
                result.setValid(false);
                result.setMessage("OOXML 结构校验失败: " + errors);
                return result;
            }
        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("导出文件校验异常: " + e.getMessage());
            return result;
        }

        // 3. 基本可打开性检查
        try (var ignored = new ByteArrayInputStream(fileContent)) {
            // 检查 ZIP 签名
            if (fileContent.length < 4 ||
                    fileContent[0] != 0x50 ||
                    fileContent[1] != 0x4B ||
                    fileContent[2] != 0x03 ||
                    fileContent[3] != 0x04) {
                result.setValid(false);
                result.setMessage("文件不是有效的 ZIP 格式（OOXML）");
                return result;
            }
        } catch (Exception e) {
            result.setValid(false);
            result.setMessage("文件校验失败: " + e.getMessage());
            return result;
        }

        result.setValid(true);
        result.setMessage("校验通过，文件大小: " + fileContent.length + " 字节");
        return result;
    }

    /**
     * 构建导出报告。
     */
    public ConversionReport buildExportReport(byte[] fileContent, boolean success) {
        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.EXPORT);
        report.setSuccess(success);
        report.setCreatedAt(Instant.now());

        if (success) {
            try {
                WebEditingProjection projection = adapter.read(fileContent);
                if (projection != null) {
                    List<DocumentNode> nodes = projection.getContent();
                    report.setTotalElements(nodes.size());
                    report.setEditableCount(nodes.size());
                }
            } catch (Exception e) {
                log.warn("构建导出报告时解析失败", e);
            }
        }

        return report;
    }
}