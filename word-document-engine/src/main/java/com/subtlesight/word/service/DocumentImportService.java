package com.subtlesight.word.service;

import com.subtlesight.word.model.ConversionReport;
import com.subtlesight.word.model.WordDocumentAsset;

/**
 * 文档导入服务接口。
 * <p>
 * 负责 .docx 文件上传、安全检查、解析和结构识别。
 * </p>
 */
public interface DocumentImportService {

    /**
     * 执行导入前安全检查：文件大小、解压后大小、XML 深度、zip bomb 检测等。
     */
    SecurityCheckResult securityCheck(byte[] fileContent, String fileName);

    /**
     * 解析 .docx 文件，建立文档结构和语义节点树。
     */
    ParseResult parse(byte[] fileContent);

    /**
     * 执行完整导入流程：安全检查 -> 解析 -> 生成投影 -> 保存资产。
     */
    ConversionReport importDocument(WordDocumentAsset asset, byte[] fileContent);

    /**
     * 安全检查结果。
     */
    class SecurityCheckResult {
        private boolean passed;
        private String reason;
        private long fileSize;
        private long unzippedSize;

        public boolean isPassed() {
            return passed;
        }

        public void setPassed(boolean passed) {
            this.passed = passed;
        }

        public String getReason() {
            return reason;
        }

        public void setReason(String reason) {
            this.reason = reason;
        }

        public long getFileSize() {
            return fileSize;
        }

        public void setFileSize(long fileSize) {
            this.fileSize = fileSize;
        }

        public long getUnzippedSize() {
            return unzippedSize;
        }

        public void setUnzippedSize(long unzippedSize) {
            this.unzippedSize = unzippedSize;
        }
    }

    /**
     * 解析结果。
     */
    class ParseResult {
        private boolean success;
        private int paragraphCount;
        private int tableCount;
        private int imageCount;
        private int sectionCount;
        private String errorMessage;

        public boolean isSuccess() {
            return success;
        }

        public void setSuccess(boolean success) {
            this.success = success;
        }

        public int getParagraphCount() {
            return paragraphCount;
        }

        public void setParagraphCount(int paragraphCount) {
            this.paragraphCount = paragraphCount;
        }

        public int getTableCount() {
            return tableCount;
        }

        public void setTableCount(int tableCount) {
            this.tableCount = tableCount;
        }

        public int getImageCount() {
            return imageCount;
        }

        public void setImageCount(int imageCount) {
            this.imageCount = imageCount;
        }

        public int getSectionCount() {
            return sectionCount;
        }

        public void setSectionCount(int sectionCount) {
            this.sectionCount = sectionCount;
        }

        public String getErrorMessage() {
            return errorMessage;
        }

        public void setErrorMessage(String errorMessage) {
            this.errorMessage = errorMessage;
        }
    }
}