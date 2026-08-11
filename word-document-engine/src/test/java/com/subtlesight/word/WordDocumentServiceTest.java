package com.subtlesight.word;

import com.subtlesight.word.model.*;
import com.subtlesight.word.model.enums.ChangeOperation;
import com.subtlesight.word.model.enums.ReviewStatus;
import com.subtlesight.word.service.WordDocumentService;
import com.subtlesight.word.service.impl.WordDocumentServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * WordDocumentService 核心功能测试。
 */
class WordDocumentServiceTest {

    private WordDocumentService service;
    private WordDocumentAsset asset;

    @BeforeEach
    void setUp() {
        service = new WordDocumentServiceImpl();
    }

    @Test
    void testImportAndRetrieveDocument() {
        byte[] minDocx = createMinimalDocx();
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        ConversionReport report = service.importDocument(asset, minDocx);
        System.out.println("导入报告: " + report.isSuccess());

        // 新 API 直接返回 WordDocumentAsset
        WordDocumentAsset retrieved = service.getDocument(asset.getDocumentId());
        assertNotNull(retrieved);
        assertEquals("test.docx", retrieved.getFileName());
    }

    @Test
    void testCreateAndSubmitChangeSet() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        ConversionReport report = service.importDocument(asset, createMinimalDocx());
        assertTrue(report.isSuccess());

        DocumentChangeSet changeSet = new DocumentChangeSet();
        changeSet.setDocumentId(asset.getDocumentId());
        changeSet.setExpectedVersion(1);
        changeSet.setSummary("测试变更");
        changeSet.setAuthorId("test-user");
        changeSet.setAuthorType("ai");

        DocumentChangeSet.Change change = new DocumentChangeSet.Change();
        change.setOperation(ChangeOperation.REPLACE_TEXT);
        change.setTargetNodeId("node-test");
        change.setNewValue(java.util.Map.of("text", "替换文本"));
        changeSet.addChange(change);

        DocumentChangeSet created = service.createChangeSet(changeSet);
        assertNotNull(created);
        assertEquals(ReviewStatus.PENDING_REVIEW, created.getReviewStatus());

        // 验证变更集
        WordDocumentService.ValidationResult validation = service.validateChangeSet(created.getChangeSetId());
        System.out.println("校验结果: " + validation.isValid());

        // 获取变更集
        Optional<DocumentChangeSet> retrieved = service.getChangeSet(created.getChangeSetId());
        assertTrue(retrieved.isPresent());
    }

    @Test
    void testVersionConflict() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        DocumentChangeSet changeSet = new DocumentChangeSet();
        changeSet.setDocumentId(asset.getDocumentId());
        changeSet.setExpectedVersion(999);

        assertThrows(RuntimeException.class, () -> service.createChangeSet(changeSet));
    }

    @Test
    void testSearchNodes() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        List<DocumentNode> result = service.searchNodes(asset.getDocumentId(), null, null);
        assertNotNull(result);
    }

    @Test
    void testDocumentNotFound() {
        assertThrows(IllegalArgumentException.class, () -> service.getDocument("non-existent"));
    }

    @Test
    void testListDocuments() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        List<WordDocumentAsset> docs = service.listDocuments();
        assertFalse(docs.isEmpty());
    }

    @Test
    void testDeleteDocument() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());
        String docId = asset.getDocumentId();

        service.deleteDocument(docId);
        assertThrows(IllegalArgumentException.class, () -> service.getDocument(docId));
    }

    @Test
    void testGetProjection() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        WebEditingProjection projection = service.getProjection(asset.getDocumentId());
        assertNotNull(projection);
        assertNotNull(projection.getHtmlContent());
    }

    @Test
    void testVersionHistory() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        List<WordDocumentService.DocumentVersion> versions = service.getVersionHistory(asset.getDocumentId());
        assertNotNull(versions);
    }

    @Test
    void testExportDocument() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        WordDocumentService.ExportOptions options = new WordDocumentService.ExportOptions();
        WordDocumentService.ExportResult result = service.exportDocument(asset.getDocumentId(), options);
        assertTrue(result.isSuccess());
        assertNotNull(result.getFileContent());
    }

    @Test
    void testGetChangeSets() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        List<DocumentChangeSet> csList = service.getChangeSets(asset.getDocumentId());
        assertNotNull(csList);
    }

    @Test
    void testRejectChangeSet() {
        asset = new WordDocumentAsset();
        asset.setFileName("test.docx");
        service.importDocument(asset, createMinimalDocx());

        DocumentChangeSet changeSet = new DocumentChangeSet();
        changeSet.setDocumentId(asset.getDocumentId());
        changeSet.setExpectedVersion(1);
        changeSet.setSummary("待拒绝");
        DocumentChangeSet.Change change = new DocumentChangeSet.Change();
        change.setOperation(ChangeOperation.REPLACE_TEXT);
        change.setTargetNodeId("test-node");
        changeSet.addChange(change);
        DocumentChangeSet created = service.createChangeSet(changeSet);

        service.rejectChangeSet(created.getChangeSetId(), "测试拒绝");
        Optional<DocumentChangeSet> rejected = service.getChangeSet(created.getChangeSetId());
        assertTrue(rejected.isPresent());
        assertEquals(ReviewStatus.REJECTED, rejected.get().getReviewStatus());
    }

    /**
     * 创建一个最小的有效 .docx 文件内容。
     */
    private byte[] createMinimalDocx() {
        try {
            java.io.ByteArrayOutputStream baos = new java.io.ByteArrayOutputStream();
            try (java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(baos)) {
                zos.putNextEntry(new java.util.zip.ZipEntry("[Content_Types].xml"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">
                          <Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>
                          <Default Extension="xml" ContentType="application/xml"/>
                          <Override PartName="/word/document.xml" ContentType="application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"/>
                        </Types>
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new java.util.zip.ZipEntry("_rels/.rels"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="word/document.xml"/>
                        </Relationships>
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new java.util.zip.ZipEntry("word/document.xml"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <w:document xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                          <w:body>
                            <w:p>
                              <w:pPr><w:pStyle w:val="Heading1"/></w:pPr>
                              <w:r><w:t>测试标题</w:t></w:r>
                            </w:p>
                            <w:p>
                              <w:r><w:t>这是一段测试正文内容。</w:t></w:r>
                            </w:p>
                            <w:p>
                              <w:r><w:t>第二段内容：Word 保真智能写作。</w:t></w:r>
                            </w:p>
                            <w:sectPr>
                              <w:pgSz w:w="11906" w:h="16838"/>
                              <w:pgMar w:top="1440" w:right="1440" w:bottom="1440" w:left="1440"/>
                            </w:sectPr>
                          </w:body>
                        </w:document>
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new java.util.zip.ZipEntry("word/_rels/document.xml.rels"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
                          <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/styles" Target="styles.xml"/>
                        </Relationships>
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();

                zos.putNextEntry(new java.util.zip.ZipEntry("word/styles.xml"));
                zos.write("""
                        <?xml version="1.0" encoding="UTF-8" standalone="yes"?>
                        <w:styles xmlns:w="http://schemas.openxmlformats.org/wordprocessingml/2006/main">
                          <w:style w:type="paragraph" w:styleId="Heading1">
                            <w:name w:val="heading 1"/>
                          </w:style>
                        </w:styles>
                        """.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                zos.closeEntry();
            }
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("创建测试文档失败", e);
        }
    }
}