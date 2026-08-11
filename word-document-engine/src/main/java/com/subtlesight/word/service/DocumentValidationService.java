package com.subtlesight.word.service;

import com.subtlesight.word.model.ConversionReport;

import java.util.List;

/**
 * 文档校验服务接口。
 * <p>
 * 每次导入、变更提交和导出都需执行校验。
 * 对应 PRD 7.3.E 节。
 * </p>
 */
public interface DocumentValidationService {

    /**
     * OOXML 结构校验。
     */
    ValidationResult validateOOXMLStructure(byte[] fileContent);

    /**
     * 关系引用检查。
     */
    ValidationResult validateRelationships(byte[] fileContent);

    /**
     * 节点锚点完整性检查。
     */
    ValidationResult validateNodeAnchors(String documentId);

    /**
     * 支持矩阵检查。
     */
    ValidationResult validateSupportMatrix(byte[] fileContent);

    /**
     * 图片/链接安全检查。
     */
    ValidationResult validateContentSecurity(byte[] fileContent);

    /**
     * 执行完整校验。
     */
    ValidationResult validateAll(byte[] fileContent, String documentId);

    /**
     * 校验结果。
     */
    class ValidationResult {
        private boolean valid;
        private List<ConversionReport.Issue> issues;

        public boolean isValid() {
            return valid;
        }

        public void setValid(boolean valid) {
            this.valid = valid;
        }

        public List<ConversionReport.Issue> getIssues() {
            return issues;
        }

        public void setIssues(List<ConversionReport.Issue> issues) {
            this.issues = issues;
        }
    }
}