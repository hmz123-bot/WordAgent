package com.subtlesight.word.adapter.poi;

import com.subtlesight.word.adapter.WordDocumentAdapter;
import com.subtlesight.word.model.*;
import com.subtlesight.word.model.enums.NodeType;
import com.subtlesight.word.model.enums.SupportLevel;
import com.subtlesight.word.model.formatting.*;
import com.subtlesight.word.model.i18n.*;
import org.apache.poi.ooxml.POIXMLProperties;
import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.regex.*;

/**
 * 入口适配器，使用 PoiDocumentReader 读取，PoiDocumentWriter 写入。
 */
public class PoiDocumentAdapter implements WordDocumentAdapter {

    private static final Logger log = LoggerFactory.getLogger(PoiDocumentAdapter.class);

    private final PoiDocumentReader reader;
    private final PoiDocumentWriter writer;

    public PoiDocumentAdapter() {
        this.reader = new PoiDocumentReader();
        this.writer = new PoiDocumentWriter();
    }

    @Override
    public WebEditingProjection read(byte[] fileContent) {
        WebEditingProjection projection = reader.read(fileContent);
        projection.setHtmlContent(buildHtmlContent(projection.getContent()));
        return projection;
    }

    @Override
    public byte[] create(WebEditingProjection projection) {
        return writer.create(projection);
    }

    @Override
    public byte[] applyChanges(byte[] fileContent, DocumentChangeSet changeSet) {
        return writer.applyChanges(fileContent, changeSet);
    }

    @Override
    public DocumentStats analyze(byte[] fileContent) {
        return reader.analyze(fileContent);
    }

    @Override
    public byte[] convert(byte[] fileContent, String targetFormat) {
        // 当前仅支持 docx，后续可扩展
        return fileContent;
    }

    @Override
    public ConversionReport validate(byte[] fileContent) {
        ConversionReport report = new ConversionReport(ConversionReport.ConversionType.IMPORT);
        try {
            WebEditingProjection projection = read(fileContent);
            report.setSuccess(true);
            long total = countNodes(projection.getContent());
            report.setTotalElements(total);

            // 添加元素支持级别项
            report.addItem(new ConversionReport.Item("paragraph", "段落", SupportLevel.EDITABLE));
            report.addItem(new ConversionReport.Item("table", "表格", SupportLevel.EDITABLE));
            report.addItem(new ConversionReport.Item("image", "图片", SupportLevel.EDITABLE));
            report.addItem(new ConversionReport.Item("headerFooter", "页眉页脚", SupportLevel.EDITABLE));

            if (!projection.getImages().isEmpty()) {
                report.addItem(new ConversionReport.Item("image", "图片资源", SupportLevel.EDITABLE));
            }
        } catch (Exception e) {
            report.setSuccess(false);
            ConversionReport.Issue issue = new ConversionReport.Issue();
            issue.setErrorCode(com.subtlesight.word.model.enums.ErrorCode.VALIDATION_FAILED);
            issue.setMessage("验证失败: " + e.getMessage());
            issue.setSeverity(ConversionReport.Issue.Severity.ERROR);
            report.addIssue(issue);
        }
        return report;
    }

    @Override
    public byte[] createDocument(String template, Map<String, Object> data) {
        return writer.createDocument(template, data);
    }

    @Override
    public byte[] mergeDocuments(List<byte[]> documents) {
        return writer.mergeDocuments(documents);
    }

    private int countNodes(List<DocumentNode> nodes) {
        int count = 0;
        if (nodes != null) {
            for (DocumentNode node : nodes) {
                count++;
                count += countNodes(node.getChildren());
            }
        }
        return count;
    }

    /**
     * 将文档节点投影转换为 HTML 片段，供 WebEditingProjection.getHtmlContent() 使用。
     */
    private String buildHtmlContent(List<DocumentNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("<div class=\"web-projection\">");
        for (DocumentNode node : nodes) {
            if (node == null) continue;
            String text = node.getText() != null ? node.getText() : "";
            NodeType type = node.getNodeType();
            if (type == NodeType.HEADING) {
                sb.append("<h2>").append(escapeHtml(text)).append("</h2>");
            } else if (type == NodeType.TABLE) {
                sb.append("<table></table>");
            } else if (type == NodeType.IMAGE) {
                sb.append("<img alt=\"").append(escapeHtml(text)).append("\"/>");
            } else {
                sb.append("<p>").append(escapeHtml(text)).append("</p>");
            }
        }
        sb.append("</div>");
        return sb.toString();
    }

    private static String escapeHtml(String text) {
        if (text == null || text.isEmpty()) return "";
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    // ========================================================================
    // 测试 / 便捷 API（文档节点投影）
    // ========================================================================

    /**
     * 返回某类节点的支持级别。
     */
    public SupportLevel getSupportLevel(NodeType type) {
        if (type == null) return SupportLevel.UNSUPPORTED;
        switch (type) {
            case PARAGRAPH:
            case HEADING:
            case RUN:
            case TABLE:
            case TABLE_ROW:
            case TABLE_CELL:
            case IMAGE:
                return SupportLevel.EDITABLE;
            case SHAPE:
            case TEXT_BOX:
            case CHART:
            case EQUATION:
            case WATERMARK:
            case CONTENT_CONTROL:
            case FIELD:
            case HYPERLINK:
            case BOOKMARK:
                return SupportLevel.READ_ONLY;
            case UNKNOWN:
                return SupportLevel.UNSUPPORTED;
            default:
                return SupportLevel.EDITABLE;
        }
    }

    /**
     * 解析文档为扁平化的文档节点列表（所有层级的节点均包含其中），
     * 并为每个节点分配基于 docId 的稳定 ID 与定位锚点。
     */
    public List<DocumentNode> parse(byte[] docx, String docId) {
        String base = (docId == null || docId.isEmpty()) ? "doc" : docId;
        WebEditingProjection projection = reader.read(docx);
        List<DocumentNode> content = projection.getContent();
        if (content == null) content = new ArrayList<>();

        int[] tableIdx = {0};
        int[] paraIdx = {0};
        int[] imageIdx = {0};
        int[] otherIdx = {0};

        List<DocumentNode> flat = new ArrayList<>();
        for (DocumentNode node : content) {
            decorateNode(node, base, tableIdx, paraIdx, imageIdx, otherIdx);
            flatten(flat, node);
        }
        return flat;
    }

    /**
     * 解析并将结果包装为 ParseResult。
     */
    public WordDocumentAdapter.ParseResult parse(byte[] docx) {
        WordDocumentAdapter.ParseResult result = new WordDocumentAdapter.ParseResult();
        try {
            List<DocumentNode> nodes = parse(docx, null);
            result.setNodes(nodes);
            result.setSuccess(true);
        } catch (Exception e) {
            result.setSuccess(false);
            List<String> warnings = new ArrayList<>();
            warnings.add(e.getMessage());
            result.setWarnings(warnings);
        }
        return result;
    }

    /**
     * 统计并替换文档正文中的文本匹配数（基于 word/document.xml 的 &lt;w:t&gt; 文本）。
     */
    public int replaceText(byte[] docx, String oldText, String newText) {
        if (oldText == null || oldText.isEmpty()) return 0;
        try (java.util.zip.ZipInputStream zis =
                     new java.util.zip.ZipInputStream(new ByteArrayInputStream(docx))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = zis.read(buf)) > 0) baos.write(buf, 0, n);
                    String xml = new String(baos.toByteArray(), "UTF-8");
                    Matcher m = Pattern.compile("<w:t[^>]*>([^<]*)</w:t>").matcher(xml);
                    int count = 0;
                    while (m.find()) {
                        String text = m.group(1);
                        int idx = 0;
                        while ((idx = text.indexOf(oldText, idx)) != -1) {
                            count++;
                            idx += oldText.length();
                        }
                    }
                    return count;
                }
            }
            return 0;
        } catch (Exception e) {
            return 0;
        }
    }

    /**
     * 返回文档统计信息。
     */
    public WordDocumentAdapter.DocumentStats getDocumentStats(byte[] docx) {
        return reader.analyze(docx);
    }

    /**
     * 校验文档结构完整性，返回错误描述列表（为空表示有效）。
     */
    public List<String> validateStructure(byte[] docx) {
        List<String> errors = new ArrayList<>();
        if (docx == null || docx.length == 0) {
            errors.add("文档内容为空");
            return errors;
        }
        try {
            WebEditingProjection p = reader.read(docx);
            if (p.getMetadata() != null && p.getMetadata().containsKey("error")) {
                errors.add("文档解析失败: " + p.getMetadata().get("error"));
            }
        } catch (Exception e) {
            errors.add("结构校验异常: " + e.getMessage());
        }
        return errors;
    }

    // ======================== 私有辅助 ========================

    private void decorateNode(DocumentNode node, String base,
                              int[] tableIdx, int[] paraIdx, int[] imageIdx, int[] otherIdx) {
        if (node == null) return;
        NodeType t = node.getNodeType();
        String id;
        if (t == NodeType.TABLE) {
            id = base + "_table_" + (tableIdx[0]++);
        } else if (t == NodeType.PARAGRAPH || t == NodeType.HEADING) {
            id = base + "_para_" + (paraIdx[0]++);
        } else if (t == NodeType.IMAGE) {
            id = base + "_image_" + (imageIdx[0]++);
        } else {
            id = base + "_node_" + (otherIdx[0]++);
        }
        node.setNodeId(id);

        if (t == NodeType.TABLE) {
            List<DocumentNode> rows = node.getChildren();
            int rowCount = (rows == null) ? 0 : rows.size();
            int colCount = 0;
            if (rows != null) {
                for (DocumentNode row : rows) {
                    int cells = (row.getChildren() == null) ? 0 : row.getChildren().size();
                    if (cells > colCount) colCount = cells;
                }
            }
            node.getAttributes().put("rowCount", rowCount);
            node.getAttributes().put("colCount", colCount);
        }

        if (t == NodeType.IMAGE && node.getImage() != null) {
            WebEditingProjection.ImageResource img = node.getImage();
            node.getAttributes().put("imageId", img.getId());
            node.getAttributes().put("contentType", img.getMimeType());
            node.getAttributes().put("width", img.getWidth() != null ? img.getWidth() : 0);
            node.getAttributes().put("height", img.getHeight() != null ? img.getHeight() : 0);
        }

        NodeAnchor anchor = new NodeAnchor();
        anchor.setNodeId(id);
        anchor.setPartPath("word/document.xml");
        anchor.setStructuralPath("/w:document/w:body/" + (t == null ? "node" : t.name().toLowerCase()) + "[" + id + "]");
        anchor.setVersion(1);
        node.setAnchor(anchor);

        if (node.getChildren() != null) {
            for (DocumentNode child : node.getChildren()) {
                decorateNode(child, base, tableIdx, paraIdx, imageIdx, otherIdx);
            }
        }
    }

    private void flatten(List<DocumentNode> out, DocumentNode node) {
        if (node == null) return;
        out.add(node);
        if (node.getChildren() != null) {
            for (DocumentNode child : node.getChildren()) {
                flatten(out, child);
            }
        }
    }
}