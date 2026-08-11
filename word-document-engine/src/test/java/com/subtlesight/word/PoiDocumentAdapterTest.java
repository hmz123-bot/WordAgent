package com.subtlesight.word;

import com.subtlesight.word.adapter.poi.PoiDocumentAdapter;
import com.subtlesight.word.model.DocumentNode;
import com.subtlesight.word.model.NodeAnchor;
import com.subtlesight.word.model.enums.NodeType;
import com.subtlesight.word.model.enums.SupportLevel;
import com.subtlesight.word.adapter.WordDocumentAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * POI 适配器单元测试：表格、图片、段落、格式等核心功能。
 */
class PoiDocumentAdapterTest {

    private PoiDocumentAdapter adapter;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        adapter = new PoiDocumentAdapter();
    }

    @Test
    void testSupportLevel() {
        assertEquals(SupportLevel.EDITABLE, adapter.getSupportLevel(NodeType.PARAGRAPH));
        assertEquals(SupportLevel.EDITABLE, adapter.getSupportLevel(NodeType.TABLE));
        assertEquals(SupportLevel.EDITABLE, adapter.getSupportLevel(NodeType.IMAGE));
        assertEquals(SupportLevel.READ_ONLY, adapter.getSupportLevel(NodeType.SHAPE));
        assertEquals(SupportLevel.UNSUPPORTED, adapter.getSupportLevel(NodeType.UNKNOWN));
    }

    @Test
    void testParseTableStructure() throws Exception {
        byte[] docx = createTableDocx(new String[][]{
                {"姓名", "年龄", "城市"},
                {"张三", "28", "北京"},
                {"李四", "35", "上海"},
                {"王五", "42", "深圳"}
        });
        List<DocumentNode> nodes = adapter.parse(docx, "test-doc");
        DocumentNode tableNode = nodes.stream()
                .filter(n -> n.getNodeType() == NodeType.TABLE).findFirst().orElse(null);
        assertNotNull(tableNode, "应解析出表格节点");
        assertEquals("test-doc_table_0", tableNode.getNodeId());
        Map<String, Object> attrs = tableNode.getAttributes();
        assertEquals(4, attrs.get("rowCount"), "应有4行");
        assertEquals(3, attrs.get("colCount"), "应有3列");

        List<String> rowIds = tableNode.getChildrenIds();
        assertEquals(4, rowIds.size(), "应有4行");
        DocumentNode headerRow = nodes.stream().filter(n -> n.getNodeId().equals(rowIds.get(0))).findFirst().orElse(null);
        assertNotNull(headerRow);
        assertEquals(NodeType.TABLE_ROW, headerRow.getNodeType());
        assertEquals(3, headerRow.getChildrenIds().size());
        DocumentNode headerCell = nodes.stream().filter(n -> n.getNodeId().equals(headerRow.getChildrenIds().get(0))).findFirst().orElse(null);
        assertNotNull(headerCell);
        assertEquals("姓名", headerCell.getTextContent());
    }

    @Test
    void testParseEmptyTable() throws Exception {
        byte[] docx = createTableDocx(new String[][]{});
        List<DocumentNode> nodes = adapter.parse(docx, "empty-table");
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeType() == NodeType.TABLE));
    }

    @Test
    void testParseInlineImage() throws Exception {
        byte[] docx = createDocxWithImage("前文", "图注", createMinimalPng(), "img.png");
        List<DocumentNode> nodes = adapter.parse(docx, "img-doc");
        DocumentNode imageNode = nodes.stream().filter(n -> n.getNodeType() == NodeType.IMAGE).findFirst().orElse(null);
        assertNotNull(imageNode, "应解析出图片节点");
        Map<String, Object> attrs = imageNode.getAttributes();
        assertNotNull(attrs.get("imageId"));
        assertEquals("image/png", attrs.get("contentType"));
        assertTrue(attrs.containsKey("width"));
        assertTrue(attrs.containsKey("height"));
    }

    @Test
    void testParseMultipleImages() throws Exception {
        byte[] docx = createDocxWithMultipleImages();
        List<DocumentNode> nodes = adapter.parse(docx, "multi-img");
        assertEquals(2, nodes.stream().filter(n -> n.getNodeType() == NodeType.IMAGE).count());
    }

    @Test
    void testParseImageWithoutAltText() throws Exception {
        byte[] docx = createDocxWithImage("前文", null, createMinimalPng(), "plain.png");
        List<DocumentNode> nodes = adapter.parse(docx, "no-alt");
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeType() == NodeType.IMAGE));
    }

    @Test
    void testReplaceText() throws Exception {
        byte[] docx = createSimpleDocx("Hello, World! This is a test.");
        int matchCount = adapter.replaceText(docx, "World", "Word Agent");
        assertTrue(matchCount > 0, "文本替换应找到匹配项");
    }

    @Test
    void testReplaceTextNoMatch() throws Exception {
        byte[] docx = createSimpleDocx("Hello World");
        int matchCount = adapter.replaceText(docx, "NonExistent", "replacement");
        assertEquals(0, matchCount);
    }

    @Test
    void testDocumentStats() throws Exception {
        byte[] docx = createTableDocx(new String[][]{{"Col1", "Col2"}, {"Data1", "Data2"}});
        WordDocumentAdapter.DocumentStats stats = adapter.getDocumentStats(docx);
        assertNotNull(stats);
        assertTrue(stats.getTableCount() >= 1);
    }

    @Test
    void testDocumentStatsWithImages() throws Exception {
        byte[] docx = createDocxWithImage("文本", "图注", createMinimalPng(), "test.png");
        WordDocumentAdapter.DocumentStats stats = adapter.getDocumentStats(docx);
        assertNotNull(stats);
        assertTrue(stats.getImageCount() >= 1, "应检测到图片");
    }

    @Test
    void testValidateStructure() throws Exception {
        byte[] docx = createSimpleDocx("Valid document.");
        assertTrue(adapter.validateStructure(docx).isEmpty(), "有效文档应无结构错误");
    }

    @Test
    void testValidateStructureWithCorruptData() {
        assertFalse(adapter.validateStructure(new byte[]{0, 1, 2, 3, 4, 5}).isEmpty());
    }

    @Test
    void testParseMixedContent() throws Exception {
        byte[] docx = createMixedContentDocx();
        List<DocumentNode> nodes = adapter.parse(docx, "mixed");
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeType() == NodeType.PARAGRAPH));
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeType() == NodeType.HEADING));
        assertTrue(nodes.stream().anyMatch(n -> n.getNodeType() == NodeType.TABLE));
    }

    @Test
    void testParseEmptyDocument() throws Exception {
        byte[] docx = createEmptyDocx();
        List<DocumentNode> nodes = adapter.parse(docx, "empty");
        assertNotNull(nodes);
    }

    @Test
    void testNodeAnchorMapping() throws Exception {
        byte[] docx = createTableDocx(new String[][]{{"A", "B"}, {"C", "D"}});
        List<DocumentNode> nodes = adapter.parse(docx, "anchors");
        for (DocumentNode node : nodes) {
            NodeAnchor anchor = node.getAnchor();
            assertNotNull(anchor, "每个节点应有锚点: " + node.getNodeId());
            assertEquals(node.getNodeId(), anchor.getNodeId());
            assertNotNull(anchor.getPartPath(), "锚点应有部件名");
            assertNotNull(anchor.getStructuralPath(), "锚点应有结构路径");
        }
    }

    @Test
    void testParseResult() throws Exception {
        byte[] docx = createSimpleDocx("Hello");
        WordDocumentAdapter.ParseResult result = adapter.parse(docx);
        assertTrue(result.isSuccess());
        assertNotNull(result.getNodes());
        assertFalse(result.getNodes().isEmpty());
    }

    // ==================== 辅助方法 ====================

    private byte[] createTableDocx(String[][] data) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeContentTypes(zos, true);
            writeRels(zos);
            writeDocRels(zos, false);
            writeStyles(zos);
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            StringBuilder body = new StringBuilder();
            body.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\"><w:body>");
            if (data != null && data.length > 0) {
                body.append("<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr><w:tblGrid>");
                int cols = (data[0] != null) ? Math.max(1, data[0].length) : 1;
                for (int i = 0; i < cols; i++) body.append("<w:gridCol w:w=\"2000\"/>");
                body.append("</w:tblGrid>");
                for (String[] row : data) {
                    body.append("<w:tr>");
                    if (row != null) for (String cell : row) {
                        body.append("<w:tc><w:p>");
                        if (cell != null && !cell.isEmpty()) body.append("<w:r><w:t>").append(escapeXml(cell)).append("</w:t></w:r>");
                        body.append("</w:p></w:tc>");
                    }
                    body.append("</w:tr>");
                }
                body.append("</w:tbl>");
            }
            body.append("</w:body></w:document>");
            zos.write(body.toString().getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createDocxWithImage(String beforeText, String altText, byte[] imageData, String fn) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeContentTypes(zos, true);
            writeRels(zos);
            zos.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/" + fn + "\"/>" +
                    "</Relationships>").getBytes("UTF-8"));
            zos.closeEntry();
            zos.putNextEntry(new ZipEntry("word/media/" + fn));
            zos.write(imageData);
            zos.closeEntry();

            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(buildImageDocXml(beforeText, altText).getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private String buildImageDocXml(String beforeText, String altText) {
        StringBuilder b = new StringBuilder();
        b.append("<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" ");
        b.append("xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" ");
        b.append("xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" ");
        b.append("xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" ");
        b.append("xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">");
        b.append("<w:body>");
        if (beforeText != null) b.append("<w:p><w:r><w:t>").append(escapeXml(beforeText)).append("</w:t></w:r></w:p>");
        b.append("<w:p><w:r><w:drawing>");
        b.append("<wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">");
        b.append("<wp:extent cx=\"1371600\" cy=\"914400\"/>");
        b.append("<wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>");
        b.append("<wp:docPr id=\"1\" name=\"Pic1\"");
        if (altText != null) b.append(" descr=\"").append(escapeXml(altText)).append("\"");
        b.append("/><wp:cNvGraphicFramePr/>");
        b.append("<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">");
        b.append("<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Img1\"/><pic:cNvPicPr/></pic:nvPicPr>");
        b.append("<pic:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>");
        b.append("<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"1371600\" cy=\"914400\"/></a:xfrm>");
        b.append("<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>");
        b.append("</pic:pic></a:graphicData></a:graphic>");
        b.append("</wp:inline></w:drawing></w:r></w:p>");
        b.append("</w:body></w:document>");
        return b.toString();
    }

    private byte[] createDocxWithMultipleImages() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeContentTypes(zos, true);
            writeRels(zos);
            zos.putNextEntry(new ZipEntry("word/_rels/document.xml.rels"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                    "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/img1.png\"/>" +
                    "<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/image\" Target=\"media/img2.png\"/>" +
                    "</Relationships>").getBytes("UTF-8"));
            zos.closeEntry();
            byte[] png = createMinimalPng();
            zos.putNextEntry(new ZipEntry("word/media/img1.png")); zos.write(png); zos.closeEntry();
            zos.putNextEntry(new ZipEntry("word/media/img2.png")); zos.write(png); zos.closeEntry();
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            String xml = "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\" " +
                    "xmlns:wp=\"http://schemas.openxmlformats.org/drawingml/2006/wordprocessingDrawing\" " +
                    "xmlns:a=\"http://schemas.openxmlformats.org/drawingml/2006/main\" " +
                    "xmlns:r=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships\" " +
                    "xmlns:pic=\"http://schemas.openxmlformats.org/drawingml/2006/picture\"><w:body>" +
                    "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                    "<wp:extent cx=\"914400\" cy=\"914400\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>" +
                    "<wp:docPr id=\"1\" name=\"Img1\"/><wp:cNvGraphicFramePr/>" +
                    "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                    "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Img1\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                    "<pic:blipFill><a:blip r:embed=\"rId1\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                    "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"914400\" cy=\"914400\"/></a:xfrm>" +
                    "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
                    "</pic:pic></a:graphicData></a:graphic>" +
                    "</wp:inline></w:drawing></w:r></w:p>" +
                    "<w:p><w:r><w:drawing><wp:inline distT=\"0\" distB=\"0\" distL=\"0\" distR=\"0\">" +
                    "<wp:extent cx=\"914400\" cy=\"914400\"/><wp:effectExtent l=\"0\" t=\"0\" r=\"0\" b=\"0\"/>" +
                    "<wp:docPr id=\"2\" name=\"Img2\"/><wp:cNvGraphicFramePr/>" +
                    "<a:graphic><a:graphicData uri=\"http://schemas.openxmlformats.org/drawingml/2006/picture\">" +
                    "<pic:pic><pic:nvPicPr><pic:cNvPr id=\"0\" name=\"Img2\"/><pic:cNvPicPr/></pic:nvPicPr>" +
                    "<pic:blipFill><a:blip r:embed=\"rId2\"/><a:stretch><a:fillRect/></a:stretch></pic:blipFill>" +
                    "<pic:spPr><a:xfrm><a:off x=\"0\" y=\"0\"/><a:ext cx=\"914400\" cy=\"914400\"/></a:xfrm>" +
                    "<a:prstGeom prst=\"rect\"><a:avLst/></a:prstGeom></pic:spPr>" +
                    "</pic:pic></a:graphicData></a:graphic>" +
                    "</wp:inline></w:drawing></w:r></w:p>" +
                    "</w:body></w:document>";
            zos.write(xml.getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createSimpleDocx(String text) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeContentTypes(zos, false);
            writeRels(zos);
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body><w:p><w:r><w:t>" + escapeXml(text) + "</w:t></w:r></w:p></w:body></w:document>").getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createMixedContentDocx() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeContentTypes(zos, true);
            writeRels(zos);
            writeDocRels(zos, false);
            writeStyles(zos);
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body>" +
                    "<w:p><w:pPr><w:pStyle w:val=\"Heading1\"/></w:pPr><w:r><w:t>主标题</w:t></w:r></w:p>" +
                    "<w:p><w:r><w:t>第一段正文。</w:t></w:r></w:p>" +
                    "<w:tbl><w:tblPr><w:tblW w:w=\"0\" w:type=\"auto\"/></w:tblPr><w:tblGrid><w:gridCol w:w=\"3000\"/><w:gridCol w:w=\"3000\"/></w:tblGrid>" +
                    "<w:tr><w:tc><w:p><w:r><w:t>单元格A</w:t></w:r></w:p></w:tc><w:tc><w:p><w:r><w:t>单元格B</w:t></w:r></w:p></w:tc></w:tr>" +
                    "</w:tbl>" +
                    "<w:p><w:r><w:t>表格后的段落。</w:t></w:r></w:p>" +
                    "</w:body></w:document>").getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    private byte[] createEmptyDocx() throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            writeContentTypes(zos, false);
            writeRels(zos);
            zos.putNextEntry(new ZipEntry("word/document.xml"));
            zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                    "<w:document xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                    "<w:body/></w:document>").getBytes("UTF-8"));
            zos.closeEntry();
        }
        return baos.toByteArray();
    }

    // ====== OOXML 公共部件 ======

    private void writeContentTypes(ZipOutputStream zos, boolean withStyles) throws IOException {
        zos.putNextEntry(new ZipEntry("[Content_Types].xml"));
        StringBuilder ct = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">" +
                "<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>" +
                "<Default Extension=\"xml\" ContentType=\"application/xml\"/>");
        if (withStyles) ct.append("<Override PartName=\"/word/styles.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.styles+xml\"/>");
        ct.append("<Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>");
        ct.append("</Types>");
        zos.write(ct.toString().getBytes("UTF-8"));
        zos.closeEntry();
    }

    private void writeRels(ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry("_rels/.rels"));
        zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">" +
                "<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument\" Target=\"word/document.xml\"/>" +
                "</Relationships>").getBytes("UTF-8"));
        zos.closeEntry();
    }

    private void writeDocRels(ZipOutputStream zos, boolean withImage) throws IOException {
        // 仅在需要时调用
    }

    private void writeStyles(ZipOutputStream zos) throws IOException {
        zos.putNextEntry(new ZipEntry("word/styles.xml"));
        zos.write(("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>" +
                "<w:styles xmlns:w=\"http://schemas.openxmlformats.org/wordprocessingml/2006/main\">" +
                "<w:style w:type=\"paragraph\" w:styleId=\"Heading1\"><w:name w:val=\"heading 1\"/></w:style>" +
                "<w:style w:type=\"table\" w:styleId=\"a5\"><w:name w:val=\"Table Grid\"/></w:style>" +
                "</w:styles>").getBytes("UTF-8"));
        zos.closeEntry();
    }

    /** 创建最小有效的 PNG（1x1 像素） */
    private byte[] createMinimalPng() {
        // 1x1 像素灰度 PNG
        return Base64.getDecoder().decode(
                "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAYAAAAfFcSJAAAADUlEQVR42mNk+M9QDwADhgGA" +
                        "WjRPTAAAAABJRU5ErkJggg=="
        );
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
    }
}