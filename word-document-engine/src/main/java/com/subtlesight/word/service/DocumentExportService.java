package com.subtlesight.word.service;

import com.subtlesight.word.model.ConversionReport;

/**
 * 文档导出服务接口。
 * <p>
 * 负责从当前 Word 事实来源生成 .docx 下载副本。
 * 支持"干净稿"和"带审阅标记"两种模式。
 * </p>
 */
public interface DocumentExportService {

    /**
     * 导出 .docx 文件。
     */
    ExportResult export(String documentId, ExportOptions options);

    /**
     * 执行 OOXML 校验和可打开性检查。
     */
    ValidationResult validateExport(byte[] fileContent);

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
     * 校验结果。
     */
    class ValidationResult {
        private boolean valid;
        private String message;

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public String getMessage() {
            return message;
        }

        public void setMessage(String message) {
            this.message = message;
        }
    }
}