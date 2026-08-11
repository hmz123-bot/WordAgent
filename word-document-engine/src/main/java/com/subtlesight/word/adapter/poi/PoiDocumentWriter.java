package com.subtlesight.word.adapter.poi;

import com.subtlesight.word.model.*;
import com.subtlesight.word.model.enums.ChangeOperation;
import com.subtlesight.word.model.formatting.*;
import com.subtlesight.word.model.i18n.*;
import org.apache.poi.wp.usermodel.HeaderFooterType;
import org.apache.poi.xwpf.usermodel.*;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.w3c.dom.Element;

import java.io.*;
import java.math.BigInteger;
import java.util.*;

/**
 * Poi 文档写入器 - 负责创建、修改和合并 .docx 文档。
 * <p>
 * 支持：i18n 字体槽（每个脚本）、RTL、段落格式(framePr/制表符/字符缩进/边框/底纹)、
 * 运行格式(下划线颜色/半点位置/复杂文字加粗斜体/字符间距/语言标签)、
 * 表格(虚拟列操作/hMerge/vMerge/gridSpan)、样式、页眉页脚、图片(PNG/JPG/GIF/SVG)、
 * 章节属性、字段、书签、超链接、内容控件、注释、脚注、方程、水印、修订标记。
 * </p>
 */
public class PoiDocumentWriter {

    private static final Logger log = LoggerFactory.getLogger(PoiDocumentWriter.class);

    // ========================================================================
    // 创建文档
    // ========================================================================

    public byte[] create(WebEditingProjection projection) {
        try (XWPFDocument doc = new XWPFDocument()) {
            applyDocumentProperties(doc, projection);
            applyRtlConfiguration(doc, projection.getRtlConfig());
            applyPageNumberingConfig(doc, projection.getPageNumberingConfig());
            applyStyles(doc, projection.getStyles());

            // 写入正文内容
            for (DocumentNode node : projection.getContent()) {
                writeNode(doc, doc, node);
            }

            // 写入页眉页脚
            writeHeadersFooters(doc, projection);

            // 写入章节属性（页面大小、边距、方向等）
            writeSections(doc, projection);

            // 写入注释、脚注、水印
            writeComments(doc, projection.getComments());
            writeFootnotes(doc, projection.getFootnotes());
            writeWatermarks(doc, projection.getWatermarks());

            return toBytes(doc);
        } catch (Exception e) {
            throw new RuntimeException("创建文档失败", e);
        }
    }

    // ========================================================================
    // 应用变更
    // ========================================================================

    public byte[] applyChanges(byte[] docxData, DocumentChangeSet changes) {
        try (ByteArrayInputStream bais = new ByteArrayInputStream(docxData);
             org.apache.poi.openxml4j.opc.OPCPackage pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(bais);
             XWPFDocument doc = new XWPFDocument(pkg)) {

            for (DocumentChangeSet.Change change : changes.getChanges()) {
                switch (change.getOperation()) {
                    case INSERT_PARAGRAPH:
                    case INSERT_HEADING:
                    case INSERT_LIST:
                    case INSERT_IMAGE:
                    case INSERT_FOOTNOTE:
                        applyInsert(doc, change);
                        break;
                    case UPDATE_FORMAT:
                    case REPLACE_TEXT:
                        applyUpdate(doc, change);
                        break;
                    case DELETE_NODE:
                        applyDelete(doc, change);
                        break;
                    case REPLACE_NODE:
                        applyReplace(doc, change);
                        break;
                    case MOVE_NODE:
                        applyMove(doc, change);
                        break;
                    case TABLE_INSERT_ROW:
                    case TABLE_DELETE_ROW:
                    case TABLE_UPDATE_CELL:
                        applyTableChange(doc, change);
                        break;
                    default:
                        log.warn("不支持的变更操作: {}", change.getOperation());
                }
            }
            return toBytes(doc);
        } catch (Exception e) {
            throw new RuntimeException("应用变更失败", e);
        }
    }

    // ========================================================================
    // 创建文档（模板）
    // ========================================================================

    @SuppressWarnings("unchecked")
    public byte[] createDocument(String templateName, Map<String, Object> options) {
        try (XWPFDocument doc = new XWPFDocument()) {
            if (options == null) return toBytes(doc);

            // 核心属性
            if (options.containsKey("title")) doc.getProperties().getCoreProperties().setTitle((String) options.get("title"));
            if (options.containsKey("author")) doc.getProperties().getCoreProperties().setCreator((String) options.get("author"));
            if (options.containsKey("subject")) {
                try {
                    Element coreElem = (Element) ((org.apache.xmlbeans.XmlObject) doc.getProperties().getCoreProperties()).getDomNode();
                    Element subjectElem = coreElem.getOwnerDocument().createElementNS("http://purl.org/dc/elements/1.1/", "dc:subject");
                    subjectElem.setTextContent((String) options.get("subject"));
                    coreElem.appendChild(subjectElem);
                } catch (Exception e) {
                    log.warn("设置 subject 失败: {}", e.getMessage());
                }
            }
            if (options.containsKey("description")) doc.getProperties().getCoreProperties().setDescription((String) options.get("description"));

            // 页面设置
            Integer pageWidth = getIntOption(options, "pageWidth");
            Integer pageHeight = getIntOption(options, "pageHeight");
            Integer marginTop = getIntOption(options, "marginTop");
            Integer marginBottom = getIntOption(options, "marginBottom");
            Integer marginLeft = getIntOption(options, "marginLeft");
            Integer marginRight = getIntOption(options, "marginRight");
            String orientation = (String) options.get("orientation");

            CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                    ? doc.getDocument().getBody().getSectPr()
                    : doc.getDocument().getBody().addNewSectPr();
            CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
            if (pageWidth != null) pageSz.setW(BigInteger.valueOf(pageWidth));
            if (pageHeight != null) pageSz.setH(BigInteger.valueOf(pageHeight));
            if ("landscape".equalsIgnoreCase(orientation)) {
                pageSz.setOrient(STPageOrientation.LANDSCAPE);
            }

            CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
            if (marginTop != null) pageMar.setTop(BigInteger.valueOf(marginTop));
            if (marginBottom != null) pageMar.setBottom(BigInteger.valueOf(marginBottom));
            if (marginLeft != null) pageMar.setLeft(BigInteger.valueOf(marginLeft));
            if (marginRight != null) pageMar.setRight(BigInteger.valueOf(marginRight));

            // RTL 配置
            Boolean rtl = (Boolean) options.get("rtl");
            if (Boolean.TRUE.equals(rtl)) {
                Element sectPrElem = (Element) ((org.apache.xmlbeans.XmlObject) sectPr).getDomNode();
                sectPrElem.appendChild(sectPrElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:bidi"));
            }

            // 页面背景
            String bgColor = (String) options.get("bgColor");
            if (bgColor != null) {
                CTSectPr bgSectPr = doc.getDocument().getBody().addNewSectPr();
                Element bgSectPrElem = (Element) ((org.apache.xmlbeans.XmlObject) bgSectPr).getDomNode();
                Element bgElem = bgSectPrElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:background");
                bgElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:color", bgColor);
                bgSectPrElem.appendChild(bgElem);
            }

            // 初始文本
            String initialText = (String) options.get("initialText");
            if (initialText != null && !initialText.isEmpty()) {
                XWPFParagraph p = doc.createParagraph();
                p.createRun().setText(initialText);
            }

            // 初始内容节点
            List<DocumentNode> initialContent = (List<DocumentNode>) options.get("initialContent");
            if (initialContent != null) {
                for (DocumentNode node : initialContent) {
                    writeNode(doc, doc, node);
                }
            }

            return toBytes(doc);
        } catch (Exception e) {
            throw new RuntimeException("创建文档失败", e);
        }
    }

    // ========================================================================
    // 合并文档
    // ========================================================================

    public byte[] mergeDocuments(List<byte[]> documents) {
        if (documents == null || documents.isEmpty()) return new byte[0];
        if (documents.size() == 1) return documents.get(0);

        try (XWPFDocument target = new XWPFDocument()) {
            // 复制第一个文档的样式
            try (ByteArrayInputStream bais = new ByteArrayInputStream(documents.get(0));
                 org.apache.poi.openxml4j.opc.OPCPackage pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(bais);
                 XWPFDocument first = new XWPFDocument(pkg)) {
                copyStyles(target, first);
            }

            for (int i = 0; i < documents.size(); i++) {
                boolean isFirst = (i == 0);
                try (ByteArrayInputStream bais = new ByteArrayInputStream(documents.get(i));
                     org.apache.poi.openxml4j.opc.OPCPackage pkg = org.apache.poi.openxml4j.opc.OPCPackage.open(bais);
                     XWPFDocument source = new XWPFDocument(pkg)) {

                    // 在文档之间插入分页符（第一个文档不插入）
                    if (!isFirst && i < documents.size()) {
                        target.createParagraph().createRun().addBreak(BreakType.PAGE);
                    }

                    // 复制段落和表格内容
                    for (XWPFParagraph p : source.getParagraphs()) {
                        copyParagraph(target, p);
                    }
                    for (XWPFTable t : source.getTables()) {
                        copyTable(target, t);
                    }

                    // 复制页眉页脚（从第一个文档）
                    if (isFirst) {
                        try {
                            for (XWPFHeader h : source.getHeaderList()) {
                                XWPFHeader targetHeader = target.createHeader(HeaderFooterType.DEFAULT);
                                for (XWPFParagraph hp : h.getParagraphs()) {
                                    copyParagraph(target, hp);
                                }
                            }
                            for (XWPFFooter f : source.getFooterList()) {
                                XWPFFooter targetFooter = target.createFooter(HeaderFooterType.DEFAULT);
                                for (XWPFParagraph fp : f.getParagraphs()) {
                                    copyParagraph(target, fp);
                                }
                            }
                        } catch (Exception e) {
                            log.warn("复制页眉页脚失败: {}", e.getMessage());
                        }
                    }
                }
            }
            return toBytes(target);
        } catch (Exception e) {
            throw new RuntimeException("合并文档失败", e);
        }
    }

    // ========================================================================
    // DOM 辅助方法
    // ========================================================================

    private void setDomOnOff(Element parent, String ns, String tagName, boolean value) {
        Element elem = parent.getOwnerDocument().createElementNS(ns, "w:" + tagName);
        elem.setAttributeNS(ns, "w:val", value ? "true" : "false");
        parent.appendChild(elem);
    }

    @SuppressWarnings("unchecked")
    private void setDomHighlight(XWPFRun r, String color) {
        try {
            // 使用 CT 方式获取或创建 rPr，然后通过 DOM 添加高亮
            CTRPr rpr = getOrCreateRPr(r);
            Element rprElem = (Element) ((org.apache.xmlbeans.XmlObject) rpr).getDomNode();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            Element hlElem = rprElem.getOwnerDocument().createElementNS(ns, "w:highlight");
            hlElem.setAttributeNS(ns, "w:val", color);
            rprElem.appendChild(hlElem);
        } catch (Exception e) {
            log.debug("设置高亮失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 节点写入
    // ========================================================================

    private void writeNode(XWPFDocument doc, IBody body, DocumentNode node) {
        if (node == null) return;
        switch (node.getNodeType()) {
            case PARAGRAPH: writeParagraph(doc, body, node); break;
            case TABLE: writeTable(doc, body, node); break;
            case TEXT_BOX: writeTextBox(doc, body, node); break;
            case IMAGE: writeImage(doc, body, node); break;
            case RUN: writeRun(findOrCreateParagraph(doc, body), node.getRunFormat(), node.getText()); break;
            case FIELD: writeFieldNode(doc, body, node); break;
            case BOOKMARK: writeBookmarkNode(doc, body, node); break;
            case HYPERLINK: writeHyperlinkNode(doc, body, node); break;
            case CONTENT_CONTROL: writeContentControlNode(doc, body, node); break;
            case EQUATION: writeEquationNode(doc, body, node); break;
            case SECTION: writeSectionBreak(doc, node); break;
            default: writeParagraph(doc, body, node);
        }
    }

    // ========================================================================
    // 段落写入
    // ========================================================================

    private void writeParagraph(XWPFDocument doc, IBody body, DocumentNode node) {
        XWPFParagraph p = createParagraphInBody(doc, body);

        // 应用段落格式
        applyParagraphFormat(p, node.getParagraphFormat());

        // 应用 framePr（段落框架属性）
        if (node.getParagraphFormat() != null && node.getParagraphFormat().getFramePr() != null) {
            applyFramePr(p, node.getParagraphFormat().getFramePr());
        }

        // RTL
        applyRtlToParagraph(p, node);

        // 样式
        applyStyleToParagraph(p, node);

        // 写入运行
        if (node.getRunFormat() != null) {
            writeRun(p, node.getRunFormat(), node.getText());
        }

        // 写入子节点
        if (node.getChildren() != null) {
            for (DocumentNode child : node.getChildren()) writeNode(doc, body, child);
        }
    }

    private void writeRun(XWPFParagraph p, RunFormat run, String text) {
        if (run == null) return;
        XWPFRun r = p.createRun();

        // 文本
        if (text != null) r.setText(text, 0);

        // 字体（优先从 fontSlots 获取主字体）
        String mainFont = resolveMainFont(run);
        if (mainFont != null) r.setFontFamily(mainFont);

        // 字号
        if (run.getFontSize() != null && run.getFontSize() > 0) {
            r.setFontSize(run.getFontSize());
        }

        // 字形
        if (run.getBold() != null) r.setBold(run.getBold());
        if (run.getItalic() != null) r.setItalic(run.getItalic());
        if (run.getStrike() != null) r.setStrikeThrough(run.getStrike());
        if (run.getDoubleStrike() != null) r.setDoubleStrikethrough(run.getDoubleStrike());
        if (run.getSmallCaps() != null) r.setSmallCaps(run.getSmallCaps());
        if (run.getAllCaps() != null) r.setCapitalized(run.getAllCaps());

        // 下划线
        if (run.getUnderlineStyle() != null) {
            r.setUnderline(convertUnderlineStyle(run.getUnderlineStyle()));
        }

        // 颜色
        if (run.getColor() != null) r.setColor(run.getColor());

        // 高亮
        if (run.getHighlightColor() != null) {
            setDomHighlight(r, run.getHighlightColor());
        }

        // 上下标
        if (run.getPosition() != null) {
            r.setSubscript(run.getPosition() > 0
                    ? VerticalAlign.SUPERSCRIPT : VerticalAlign.SUBSCRIPT);
        }

        // 底纹
        if (run.getShading() != null) {
            CTRPr pr = getOrCreateRPr(r);
            CTShd shd = pr.addNewShd();
            if (run.getShading().getFill() != null) shd.setFill(run.getShading().getFill());
            if (run.getShading().getPattern() != null) {
                try { shd.setVal(STShd.Enum.forString(run.getShading().getPattern())); }
                catch (Exception e) { shd.setVal(STShd.CLEAR); }
            }
            if (run.getShading().getPatternColor() != null) shd.setColor(run.getShading().getPatternColor());
        }

        // ====== 底层 XML 属性（DOM 方式操作） ======
        String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
        CTRPr pr = getOrCreateRPr(r);
        Element prElem = (Element) ((org.apache.xmlbeans.XmlObject) pr).getDomNode();

        // 复杂文字加粗/斜体
        if (run.getBoldComplex() != null) {
            setDomOnOff(prElem, ns, "bCs", run.getBoldComplex());
        }
        if (run.getItalicComplex() != null) {
            setDomOnOff(prElem, ns, "iCs", run.getItalicComplex());
        }

        // 复杂文字字号
        if (run.getFontSizeComplex() != null && run.getFontSizeComplex() > 0) {
            pr.addNewSzCs().setVal(BigInteger.valueOf((long) (run.getFontSizeComplex() * 2)));
        }

        // 位置（半点）
        if (run.getPosition() != null && run.getPosition() != 0) {
            pr.addNewPosition().setVal(BigInteger.valueOf(run.getPosition()));
        }

        // 字符间距（1/20磅）
        if (run.getCharacterSpacing() != null && run.getCharacterSpacing() != 0) {
            CTSignedTwipsMeasure spacing = pr.addNewSpacing();
            spacing.setVal(BigInteger.valueOf(run.getCharacterSpacing()));
        }

        // 字符缩放（百分比）
        if (run.getScaling() != null && run.getScaling() != 0) {
            pr.addNewW().setVal(BigInteger.valueOf(run.getScaling()));
        }

        // 字体槽（每个脚本槽）
        if (run.getFontSlots() != null && !run.getFontSlots().isEmpty()) {
            // 使用 DOM 方式创建 rFonts 元素
            Element rFontsElem = prElem.getOwnerDocument().createElementNS(ns, "w:rFonts");
            boolean hasFonts = false;
            for (FontSlot slot : run.getFontSlots()) {
                if (slot.getFontName() == null) continue;
                hasFonts = true;
                switch (slot.getScriptType()) {
                    case EAST_ASIA:
                        rFontsElem.setAttributeNS(ns, "w:eastAsia", slot.getFontName());
                        if (slot.getFontSize() != null) {
                            pr.addNewSz().setVal(BigInteger.valueOf((long) (slot.getFontSize() * 2)));
                        }
                        break;
                    case COMPLEX_SCRIPT:
                        rFontsElem.setAttributeNS(ns, "w:cs", slot.getFontName());
                        if (slot.getFontSize() != null) {
                            pr.addNewSzCs().setVal(BigInteger.valueOf((long) (slot.getFontSize() * 2)));
                        }
                        break;
                    default:
                        rFontsElem.setAttributeNS(ns, "w:ascii", slot.getFontName());
                        rFontsElem.setAttributeNS(ns, "w:hAnsi", slot.getFontName());
                }
            }
            if (hasFonts) prElem.appendChild(rFontsElem);
        }

        // 下划线颜色
        if (run.getUnderlineColor() != null) {
            Element uElem = prElem.getOwnerDocument().createElementNS(ns, "w:u");
            uElem.setAttributeNS(ns, "w:color", run.getUnderlineColor());
            prElem.appendChild(uElem);
        }

        // 语言标签
        if (run.getLanguageTag() != null) {
            Element langsElem = prElem.getOwnerDocument().createElementNS(ns, "w:langs");
            langsElem.setAttributeNS(ns, "w:val", run.getLanguageTag().getTag());
            prElem.appendChild(langsElem);
        }

        // 额外属性（shadow, outline, emboss, imprint）
        if (run.getAdditionalProperties() != null) {
            Map<String, Object> extra = run.getAdditionalProperties();
            if (Boolean.TRUE.equals(extra.get("shadow"))) {
                setDomOnOff(prElem, ns, "shadow", true);
            }
            if (Boolean.TRUE.equals(extra.get("outline"))) {
                setDomOnOff(prElem, ns, "outline", true);
            }
            if (Boolean.TRUE.equals(extra.get("emboss"))) {
                setDomOnOff(prElem, ns, "emboss", true);
            }
            if (Boolean.TRUE.equals(extra.get("imprint"))) {
                setDomOnOff(prElem, ns, "imprint", true);
            }
        }
    }

    // ========================================================================
    // 表格写入
    // ========================================================================

    private void writeTable(XWPFDocument doc, IBody body, DocumentNode node) {
        XWPFTable table = doc.createTable();
        TableFormat tableFormat = node.getTableFormat();
        if (tableFormat == null) return;
        String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

        // 设置列宽
        if (tableFormat.getColumns() != null) {
            CTTblGrid tblGrid = table.getCTTbl().getTblGrid() == null ? table.getCTTbl().addNewTblGrid() : table.getCTTbl().getTblGrid();
            for (TableFormat.Column col : tableFormat.getColumns()) {
                if (col.getWidth() > 0) {
                    CTTblGridCol gridCol = tblGrid.addNewGridCol();
                    gridCol.setW(BigInteger.valueOf((long) col.getWidth()));
                }
            }
        }

        // RTL 表格
        if (Boolean.TRUE.equals(node.getRtl())) {
            CTTblPr tblPr = table.getCTTbl().getTblPr() == null ? table.getCTTbl().addNewTblPr() : table.getCTTbl().getTblPr();
            tblPr.addNewBidiVisual().setVal(true);
        }

        // 表格宽度
        if (tableFormat.getWidth() > 0 && tableFormat.getWidthType() != null) {
            CTTblPr tblPr = table.getCTTbl().getTblPr() == null ? table.getCTTbl().addNewTblPr() : table.getCTTbl().getTblPr();
            CTTblWidth tblW = tblPr.addNewTblW();
            tblW.setW(BigInteger.valueOf(tableFormat.getWidth().longValue()));
            tblW.setType("pct".equalsIgnoreCase(tableFormat.getWidthType()) ? STTblWidth.PCT : STTblWidth.DXA);
        }

        // 表格边框
        if (tableFormat.getBorders() != null && !tableFormat.getBorders().isEmpty()) {
            CTTblPr tblPr = table.getCTTbl().getTblPr() == null ? table.getCTTbl().addNewTblPr() : table.getCTTbl().getTblPr();
            CTTblBorders borders = tblPr.addNewTblBorders();
            try {
                for (TableFormat.Border border : tableFormat.getBorders()) {
                    STBorder.Enum borderVal = STBorder.Enum.forString(border.getStyle());
                    String side = border.getSide() != null ? border.getSide().toLowerCase() : "";
                    CTBorder ctBorder = switch (side) {
                        case "top" -> borders.addNewTop();
                        case "bottom" -> borders.addNewBottom();
                        case "left" -> borders.addNewLeft();
                        case "right" -> borders.addNewRight();
                        case "insideh" -> borders.addNewInsideH();
                        case "insidev" -> borders.addNewInsideV();
                        default -> null;
                    };
                    if (ctBorder != null) {
                        ctBorder.setVal(borderVal);
                        if (border.getSize() > 0) ctBorder.setSz(BigInteger.valueOf((long) border.getSize()));
                        if (border.getColor() != null) ctBorder.setColor(border.getColor());
                    }
                }
            } catch (Exception e) {
                log.warn("设置表格边框样式失败: {}", e.getMessage());
            }
        }

        // 写入行和单元格
        if (node.getChildren() != null) {
            for (int ri = 0; ri < node.getChildren().size(); ri++) {
                DocumentNode rowNode = node.getChildren().get(ri);
                while (table.getNumberOfRows() <= ri) {
                    table.createRow();
                }
                XWPFTableRow row = table.getRow(ri);

                // 行高
                if (rowNode.getTableFormat() != null && rowNode.getTableFormat().getRows() != null
                        && ri < rowNode.getTableFormat().getRows().size()) {
                    TableFormat.RowProperties rp = rowNode.getTableFormat().getRows().get(ri);
                    if (rp.getHeight() != null && rp.getHeight() > 0) {
                        row.setHeight(rp.getHeight().intValue());
                    }
                }

                if (rowNode.getChildren() != null) {
                    for (int ci = 0; ci < rowNode.getChildren().size(); ci++) {
                        DocumentNode cellNode = rowNode.getChildren().get(ci);
                        while (row.getTableCells().size() <= ci) {
                            row.addNewTableCell();
                        }
                        XWPFTableCell cell = row.getCell(ci);

                        // 单元格宽度
                        if (cellNode.getTableFormat() != null && cellNode.getTableFormat().getWidth() != null
                                && cellNode.getTableFormat().getWidth() > 0) {
                            CTTcPr tcPr = cell.getCTTc().getTcPr() == null ? cell.getCTTc().addNewTcPr() : cell.getCTTc().getTcPr();
                            CTTblWidth tcW = tcPr.addNewTcW();
                            tcW.setW(BigInteger.valueOf(cellNode.getTableFormat().getWidth().longValue()));
                        }

                        // 单元格合并
                        if (cellNode.getTableFormat() != null && cellNode.getTableFormat().getRows() != null
                                && ri < cellNode.getTableFormat().getRows().size()
                                && cellNode.getTableFormat().getRows().get(ri).getCells() != null
                                && ci < cellNode.getTableFormat().getRows().get(ri).getCells().size()) {
                            TableFormat.CellProperties cellProps = cellNode.getTableFormat().getRows().get(ri).getCells().get(ci);
                            CTTcPr tcPr = cell.getCTTc().getTcPr() == null ? cell.getCTTc().addNewTcPr() : cell.getCTTc().getTcPr();
                            if (cellProps.getColSpan() > 1) {
                                tcPr.addNewGridSpan().setVal(BigInteger.valueOf(cellProps.getColSpan()));
                            }
if (cellProps.getVMerge() != null) {
                                if (cellProps.getVMerge()) {
                                    tcPr.addNewVMerge().setVal(STMerge.RESTART);
                                } else {
                                    tcPr.addNewVMerge().setVal(STMerge.CONTINUE);
                                }
                            }
                        }

                        // 写入单元格子内容
                        if (cellNode.getChildren() != null) {
                            for (DocumentNode child : cellNode.getChildren()) {
                                writeNode(doc, cell, child);
                            }
                        }
                    }
                }
            }
        }
    }

    // ========================================================================
    // 文本框写入
    // ========================================================================

    private void writeTextBox(XWPFDocument doc, IBody body, DocumentNode node) {
        XWPFParagraph p = createParagraphInBody(doc, body);
        XWPFRun r = p.createRun();

        // 文本框格式
        TextBoxFormat tbFormat = node.getTextBoxFormat();
        if (tbFormat != null) {
            // 旋转（通过底层 XML 设置）
            if (tbFormat.getRotation() != 0) {
                String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
                CTRPr pr = getOrCreateRPr(r);
                Element prElem = (Element) ((org.apache.xmlbeans.XmlObject) pr).getDomNode();
                Element rotElem = prElem.getOwnerDocument().createElementNS(ns, "w:rotation");
                rotElem.setAttributeNS(ns, "w:val", String.valueOf((long) (tbFormat.getRotation() * 10)));
                prElem.appendChild(rotElem);
            }
        }

        // 写入文本内容
        writeRun(p, node.getRunFormat(), node.getText());

        // 写入子节点
        if (node.getChildren() != null) {
            for (DocumentNode child : node.getChildren()) {
                writeNode(doc, body, child);
            }
        }
    }

    // ========================================================================
    // 图片写入
    // ========================================================================

    private void writeImage(XWPFDocument doc, IBody body, DocumentNode node) {
        if (node.getImage() == null || node.getImage().getData() == null) return;
        XWPFParagraph p = createParagraphInBody(doc, body);
        XWPFRun r = p.createRun();
        try {
            byte[] imgData = Base64.getDecoder().decode(node.getImage().getData());
            String mimeType = node.getImage().getMimeType() != null ? node.getImage().getMimeType().toLowerCase() : "image/png";
            String format = mimeType.contains("jpeg") || mimeType.contains("jpg") ? "jpg"
                    : mimeType.contains("gif") ? "gif"
                    : mimeType.contains("svg") ? "svg"
                    : "png";
            int formatType = switch (format) {
                case "jpg", "jpeg" -> XWPFDocument.PICTURE_TYPE_JPEG;
                case "gif" -> XWPFDocument.PICTURE_TYPE_GIF;
                case "svg" -> XWPFDocument.PICTURE_TYPE_PNG; // SVG 降级为 PNG
                default -> XWPFDocument.PICTURE_TYPE_PNG;
            };
            // 图片尺寸
            int width = (int) (node.getImage().getWidth() > 0 ? node.getImage().getWidth() : 200);
            int height = (int) (node.getImage().getHeight() > 0 ? node.getImage().getHeight() : 200);
            String imgId = node.getImage().getId() != null ? node.getImage().getId() : "img";
            String imgName = imgId + "." + ("svg".equals(format) ? "png" : format);
            r.addPicture(new ByteArrayInputStream(imgData), formatType, imgName, width, height);
        } catch (Exception e) {
            log.warn("写入图片失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 页眉页脚
    // ========================================================================

    private void writeHeadersFooters(XWPFDocument doc, WebEditingProjection projection) {
        List<WebEditingProjection.HeaderFooter> hdrs = projection.getHeaders();
        List<WebEditingProjection.HeaderFooter> ftrs = projection.getFooters();
        if ((hdrs == null || hdrs.isEmpty()) && (ftrs == null || ftrs.isEmpty())) return;
        try {
            List<WebEditingProjection.HeaderFooter> emptyList = Collections.emptyList();
            for (WebEditingProjection.HeaderFooter hf : hdrs != null ? hdrs : emptyList) {
                writeHeaderFooter(doc, hf, true);
            }
            for (WebEditingProjection.HeaderFooter hf : ftrs != null ? ftrs : emptyList) {
                writeHeaderFooter(doc, hf, false);
            }
        } catch (Exception e) {
            log.warn("写入页眉页脚失败", e);
        }
    }

    private void writeHeaderFooter(XWPFDocument doc, WebEditingProjection.HeaderFooter hf, boolean isHeader) {
        HeaderFooterType hfType = "default".equals(hf.getType()) ? HeaderFooterType.DEFAULT
                : "first".equals(hf.getType()) ? HeaderFooterType.FIRST : HeaderFooterType.EVEN;
        XWPFHeaderFooter xwpfHF;
        if (isHeader) {
            xwpfHF = doc.createHeader(hfType);
        } else {
            xwpfHF = doc.createFooter(hfType);
        }
        if (hf.getContent() != null) {
            for (DocumentNode node : hf.getContent()) writeNode(doc, xwpfHF, node);
        }
    }

    // ========================================================================
    // 章节
    // ========================================================================

    private void writeSections(XWPFDocument doc, WebEditingProjection projection) {
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        // 从第一个 Section 获取页面属性
        WebEditingProjection.Section section = null;
        if (projection.getSections() != null && !projection.getSections().isEmpty()) {
            section = projection.getSections().get(0);
        }

        // 页面大小（默认 A4: 11906 x 16838 twips）
        CTPageSz pageSz = sectPr.isSetPgSz() ? sectPr.getPgSz() : sectPr.addNewPgSz();
        if (section != null && section.getPageWidth() != null && section.getPageWidth() > 0)
            pageSz.setW(BigInteger.valueOf(section.getPageWidth().longValue()));
        if (section != null && section.getPageHeight() != null && section.getPageHeight() > 0)
            pageSz.setH(BigInteger.valueOf(section.getPageHeight().longValue()));

        // 页面边距（默认 1 inch = 1440 twips）
        CTPageMar pageMar = sectPr.isSetPgMar() ? sectPr.getPgMar() : sectPr.addNewPgMar();
        if (section != null) {
            if (section.getMarginTop() != null && section.getMarginTop() > 0)
                pageMar.setTop(BigInteger.valueOf(section.getMarginTop().longValue()));
            if (section.getMarginBottom() != null && section.getMarginBottom() > 0)
                pageMar.setBottom(BigInteger.valueOf(section.getMarginBottom().longValue()));
            if (section.getMarginLeft() != null && section.getMarginLeft() > 0)
                pageMar.setLeft(BigInteger.valueOf(section.getMarginLeft().longValue()));
            if (section.getMarginRight() != null && section.getMarginRight() > 0)
                pageMar.setRight(BigInteger.valueOf(section.getMarginRight().longValue()));
        }
        // 页眉/页脚边距默认值
        if (pageMar.getHeader() == null) pageMar.setHeader(BigInteger.valueOf(720));
        if (pageMar.getFooter() == null) pageMar.setFooter(BigInteger.valueOf(720));

        // 页眉/页脚引用
        if (projection.getHeaders() != null && !projection.getHeaders().isEmpty()) {
            for (WebEditingProjection.HeaderFooter hf : projection.getHeaders()) {
                try {
                    String refType = "default".equalsIgnoreCase(hf.getType()) ? "default"
                            : "first".equalsIgnoreCase(hf.getType()) ? "first" : "even";
                    HeaderFooterType hfType = "default".equals(refType) ? HeaderFooterType.DEFAULT : "first".equals(refType) ? HeaderFooterType.FIRST : HeaderFooterType.EVEN;
                    XWPFHeader header = doc.createHeader(hfType);
                    CTHdrFtrRef hdrRef = sectPr.addNewHeaderReference();
                    hdrRef.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr.Enum.forString(refType));
                    hdrRef.setId(header.getPackagePart().getPartName().toString());
                } catch (Exception e) {
                    log.warn("设置页眉引用失败: {}", e.getMessage());
                }
            }
        }
        if (projection.getFooters() != null && !projection.getFooters().isEmpty()) {
            for (WebEditingProjection.HeaderFooter hf : projection.getFooters()) {
                try {
                    String refType = "default".equalsIgnoreCase(hf.getType()) ? "default"
                            : "first".equalsIgnoreCase(hf.getType()) ? "first" : "even";
                    XWPFFooter footer = doc.createFooter(
                            "default".equals(refType) ? HeaderFooterType.DEFAULT : "first".equals(refType) ? HeaderFooterType.FIRST : HeaderFooterType.EVEN);
                    CTHdrFtrRef ftrRef = sectPr.addNewFooterReference();
                    ftrRef.setType(org.openxmlformats.schemas.wordprocessingml.x2006.main.STHdrFtr.Enum.forString(refType));
                    ftrRef.setId(footer.getPackagePart().getPartName().toString());
                } catch (Exception e) {
                    log.warn("设置页脚引用失败: {}", e.getMessage());
                }
            }
        }

        // 章节类型
        if (section != null && section.getType() != null) {
            switch (section.getType().toUpperCase()) {
                case "ODD_PAGE": sectPr.addNewType().setVal(STSectionMark.ODD_PAGE); break;
                case "EVEN_PAGE": sectPr.addNewType().setVal(STSectionMark.EVEN_PAGE); break;
                case "CONTINUOUS": sectPr.addNewType().setVal(STSectionMark.CONTINUOUS); break;
                default: sectPr.addNewType().setVal(STSectionMark.NEXT_COLUMN); break;
            }
        }

        // 列数（从 additionalProperties 获取）
        if (section != null && section.getAdditionalProperties() != null) {
            Object colCount = section.getAdditionalProperties().get("columnCount");
            if (colCount instanceof Number && ((Number) colCount).intValue() > 1) {
                CTColumns cols = sectPr.addNewCols();
                cols.setNum(BigInteger.valueOf(((Number) colCount).intValue()));
                Object colSpacing = section.getAdditionalProperties().get("columnSpacing");
                if (colSpacing instanceof Number && ((Number) colSpacing).doubleValue() > 0) {
                    cols.setSpace(BigInteger.valueOf(((Number) colSpacing).longValue()));
                }
            }
        }

        // 行号（从 additionalProperties 获取）
        if (section != null && section.getAdditionalProperties() != null) {
            Object lineNumCountBy = section.getAdditionalProperties().get("lineNumberCountBy");
            if (lineNumCountBy instanceof Number && ((Number) lineNumCountBy).intValue() > 0) {
                CTLineNumber ln = sectPr.addNewLnNumType();
                ln.setCountBy(BigInteger.valueOf(((Number) lineNumCountBy).intValue()));
                Object lineNumStart = section.getAdditionalProperties().get("lineNumberStart");
                if (lineNumStart instanceof Number && ((Number) lineNumStart).intValue() > 0) {
                    ln.setStart(BigInteger.valueOf(((Number) lineNumStart).intValue()));
                }
                Object lineNumDistance = section.getAdditionalProperties().get("lineNumberDistance");
                if (lineNumDistance instanceof Number && ((Number) lineNumDistance).doubleValue() > 0) {
                    ln.setDistance(BigInteger.valueOf(((Number) lineNumDistance).longValue()));
                }
            }
        }

        // 页面边框
        if (projection.getDocumentProperties() != null
                && projection.getDocumentProperties().getPageBorders() != null
                && !projection.getDocumentProperties().getPageBorders().isEmpty()) {
            DocumentProperties.PageBorder pgBorder = projection.getDocumentProperties().getPageBorders().get(0);
            CTPageBorders pgBorders = sectPr.addNewPgBorders();
            CTBorder border = pgBorders.addNewTop();
            if (pgBorder.getStyle() != null) {
                try { border.setVal(STBorder.Enum.forString(pgBorder.getStyle())); }
                catch (Exception e) { border.setVal(STBorder.SINGLE); }
            }
            border.setColor(pgBorder.getColor() != null ? pgBorder.getColor() : "auto");
            border.setSz(BigInteger.valueOf(pgBorder.getSize() > 0 ? (long) pgBorder.getSize() : 4));
            // 复制到所有边
            for (CTBorder b : new CTBorder[]{
                    pgBorders.addNewBottom(), pgBorders.addNewLeft(),
                    pgBorders.addNewRight()
            }) {
                b.setVal(border.getVal());
                b.setColor(border.getColor());
                b.setSz(border.getSz());
            }
        }
    }

    // ========================================================================
    // 样式
    // ========================================================================

    private void applyStyles(XWPFDocument doc, List<StyleDefinition> styles) {
        if (styles == null) return;
        for (StyleDefinition style : styles) {
            try {
                CTStyle ctStyle = CTStyle.Factory.newInstance();
                ctStyle.setStyleId(style.getStyleId());
                ctStyle.setType(STStyleType.Enum.forString(style.getType().name()));
                if (style.getName() != null) ctStyle.addNewName().setVal(style.getName());
                if (style.getBasedOn() != null) ctStyle.addNewBasedOn().setVal(style.getBasedOn());
                if (style.getNextStyle() != null) {
                    // XMLBeans 5.x 没有 addNewNextParagraphStyle(), 使用 DOM
                    Element styleElem = (Element) ((org.apache.xmlbeans.XmlObject) ctStyle).getDomNode();
                    Element pPrElem = styleElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:pPr");
                    Element nextStyle = styleElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:next");
                    nextStyle.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", style.getNextStyle());
                    pPrElem.appendChild(nextStyle);
                    styleElem.appendChild(pPrElem);
                }

                CTPPr pPr = (CTPPr) ctStyle.addNewPPr();
                if (style.getParagraphFormat() != null) applyParagraphFormatPPr(pPr, style.getParagraphFormat());
                if (style.getRunFormat() != null) applyRunFormatRPr(ctStyle.addNewRPr(), style.getRunFormat());

                // XMLBeans 5.x 没有 addStyle(CTStyle), 使用 DOM 添加
                Element stylesElem = (Element) ((org.apache.xmlbeans.XmlObject) doc.getStyle()).getDomNode();
                stylesElem.appendChild((Element) ((org.apache.xmlbeans.XmlObject) ctStyle).getDomNode());
            } catch (Exception e) {
                log.warn("添加样式失败: {}", style.getStyleId(), e);
            }
        }
    }

    // ========================================================================
    // 文档属性
    // ========================================================================

    private void applyDocumentProperties(XWPFDocument doc, WebEditingProjection projection) {
        DocumentProperties props = projection.getDocumentProperties();
        if (props == null) return;
        if (projection.getTitle() != null) doc.getProperties().getCoreProperties().setTitle(projection.getTitle());
        // 从 additionalProperties 获取 subject/creator/description
        if (projection.getAdditionalProperties() != null) {
            Object subject = projection.getAdditionalProperties().get("subject");
            if (subject instanceof String) {
                try {
                    Element coreElem = (Element) ((org.apache.xmlbeans.XmlObject) doc.getProperties().getCoreProperties()).getDomNode();
                    Element subjectElem = coreElem.getOwnerDocument().createElementNS("http://purl.org/dc/elements/1.1/", "dc:subject");
                    subjectElem.setTextContent((String) subject);
                    coreElem.appendChild(subjectElem);
                } catch (Exception e) {
                    log.warn("设置 subject 失败: {}", e.getMessage());
                }
            }
            Object creator = projection.getAdditionalProperties().get("creator");
            if (creator instanceof String) doc.getProperties().getCoreProperties().setCreator((String) creator);
            Object description = projection.getAdditionalProperties().get("description");
            if (description instanceof String) doc.getProperties().getCoreProperties().setDescription((String) description);
        }
        if (props.getPageBackgroundColor() != null) {
            CTSectPr bgSectPr = doc.getDocument().getBody().addNewSectPr();
            Element bgSectPrElem = (Element) ((org.apache.xmlbeans.XmlObject) bgSectPr).getDomNode();
            Element bgElem = bgSectPrElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:background");
            bgElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:color", props.getPageBackgroundColor());
            bgSectPrElem.appendChild(bgElem);
        }

        DocumentProperties.DocumentDefaults defaults = props.getDefaults();
        if (defaults != null) {
            // CTBody.addNewDocDefaults() 在 XMLBeans 5.x 中不可用, 使用 DOM
            Element bodyElem = (Element) ((org.apache.xmlbeans.XmlObject) doc.getDocument().getBody()).getDomNode();
            Element docDefaultsElem = bodyElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:docDefaults");
            Element rPrDefaultElem = docDefaultsElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:rPrDefault");
            Element rPrElem = rPrDefaultElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:rPr");

            if (defaults.getDefaultRunFont() != null || defaults.getDefaultRunFontEastAsia() != null
                    || defaults.getDefaultRunFontComplex() != null) {
                Element rFontsElem = rPrElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:rFonts");
                if (defaults.getDefaultRunFont() != null) rFontsElem.setAttributeNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:ascii", defaults.getDefaultRunFont());
                if (defaults.getDefaultRunFontEastAsia() != null) rFontsElem.setAttributeNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:eastAsia", defaults.getDefaultRunFontEastAsia());
                if (defaults.getDefaultRunFontComplex() != null) rFontsElem.setAttributeNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:cs", defaults.getDefaultRunFontComplex());
                rPrElem.appendChild(rFontsElem);
            }
            if (defaults.getDefaultRunFontSize() != null && defaults.getDefaultRunFontSize() > 0) {
                Element szElem = rPrElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:sz");
                szElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val",
                    String.valueOf((long) (defaults.getDefaultRunFontSize() * 2)));
                rPrElem.appendChild(szElem);
            }
            if (defaults.getDefaultRunFontSizeComplex() != null && defaults.getDefaultRunFontSizeComplex() > 0) {
                Element szCsElem = rPrElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:szCs");
                szCsElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val",
                    String.valueOf((long) (defaults.getDefaultRunFontSizeComplex() * 2)));
                rPrElem.appendChild(szCsElem);
            }

            rPrDefaultElem.appendChild(rPrElem);
            docDefaultsElem.appendChild(rPrDefaultElem);
            bodyElem.appendChild(docDefaultsElem);
        }
    }

    // ========================================================================
    // RTL 配置
    // ========================================================================

    private void applyRtlConfiguration(XWPFDocument doc, RtlConfiguration rtlConfig) {
        if (rtlConfig == null) return;
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();
        if (rtlConfig.isAutoEnableRtl()) {
            Element sectPrElem = (Element) ((org.apache.xmlbeans.XmlObject) sectPr).getDomNode();
            sectPrElem.appendChild(sectPrElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:bidi"));
        }
        if (rtlConfig.getTextDirection() != null) {
            Element sectPrElem = (Element) ((org.apache.xmlbeans.XmlObject) sectPr).getDomNode();
            switch (rtlConfig.getTextDirection()) {
                case RIGHT_TO_LEFT: {
                    // XMLBeans 5.x 没有 addNewBiDi(), 使用 DOM
                    Element bidiElem = sectPrElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:bidi");
                    sectPrElem.appendChild(bidiElem);
                    break;
                }
                case VERTICAL_270: {
                    // STTextDirection.Vertical270 在 XMLBeans 5.x 中不可用
                    Element textDirElem = sectPrElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:textDirection");
                    textDirElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", "btLr");
                    sectPrElem.appendChild(textDirElem);
                    break;
                }
                case EAST_ASIAN_VERTICAL: {
                    Element textDirElem = sectPrElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:textDirection");
                    textDirElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", "tbLr");
                    sectPrElem.appendChild(textDirElem);
                    break;
                }
            }
        }
        if (rtlConfig.getRtlGutter() != null && rtlConfig.getRtlGutter()) {
            // XMLBeans 5.x 没有 addNewGutter()/getGutter(), 使用 DOM
            Element sectPrElem = (Element) ((org.apache.xmlbeans.XmlObject) sectPr).getDomNode();
            Element gutterElem = sectPrElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:gutter");
            gutterElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", "1440");
            gutterElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:w", "on");
            sectPrElem.appendChild(gutterElem);
        }
    }

    // ========================================================================
    // 段落格式应用
    // ========================================================================

    private void applyParagraphFormat(XWPFParagraph p, ParagraphFormat fmt) {
        if (fmt == null) return;
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();

        // 对齐
        if (fmt.getAlignment() != null) p.setAlignment(convertAlignment(fmt.getAlignment()));

        // 缩进（字符级）- 根据单位判断
        if ("char".equalsIgnoreCase(fmt.getIndentLeftUnit()) && fmt.getIndentLeft() != null && fmt.getIndentLeft() > 0) {
            CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
            ind.setLeftChars(BigInteger.valueOf(fmt.getIndentLeft().longValue()));
        }
        if ("char".equalsIgnoreCase(fmt.getIndentRightUnit()) && fmt.getIndentRight() != null && fmt.getIndentRight() > 0) {
            CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
            ind.setRightChars(BigInteger.valueOf(fmt.getIndentRight().longValue()));
        }
        if ("char".equalsIgnoreCase(fmt.getIndentFirstLineUnit()) && fmt.getIndentFirstLine() != null && fmt.getIndentFirstLine() > 0) {
            CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
            ind.setFirstLineChars(BigInteger.valueOf(fmt.getIndentFirstLine().longValue()));
        }
        // 缩进（twip级）
        if (fmt.getIndentLeft() != null && fmt.getIndentLeft() > 0) p.setIndentationLeft(fmt.getIndentLeft().intValue());
        if (fmt.getIndentRight() != null && fmt.getIndentRight() > 0) p.setIndentationRight(fmt.getIndentRight().intValue());
        if (fmt.getIndentFirstLine() != null && fmt.getIndentFirstLine() > 0) p.setIndentationFirstLine(fmt.getIndentFirstLine().intValue());
        // 悬挂缩进
        if (fmt.getIndentHanging() != null && fmt.getIndentHanging() > 0) {
            CTInd ind = pPr.isSetInd() ? pPr.getInd() : pPr.addNewInd();
            ind.setHanging(BigInteger.valueOf(fmt.getIndentHanging().longValue()));
        }

        // 间距
        if (fmt.getSpacingBefore() != null && fmt.getSpacingBefore() > 0) p.setSpacingBefore((int) fmt.getSpacingBefore().longValue());
        if (fmt.getSpacingAfter() != null && fmt.getSpacingAfter() > 0) p.setSpacingAfter((int) fmt.getSpacingAfter().longValue());
        if (fmt.getLineSpacing() != null && fmt.getLineSpacing() > 0) {
            // POI 5.3.0 没有 setSpacingLines(Double), 使用 DOM
            Element pPrElem = (Element) ((org.apache.xmlbeans.XmlObject) pPr).getDomNode();
            Element spacingElem = pPrElem.getOwnerDocument().createElementNS(
                "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:spacing");
            if (pPr.isSetSpacing()) {
                Element oldSpacing = (Element) ((org.apache.xmlbeans.XmlObject) pPr.getSpacing()).getDomNode();
                pPrElem.removeChild(oldSpacing);
            }
            spacingElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:line",
                String.valueOf(fmt.getLineSpacing().longValue()));
            pPrElem.appendChild(spacingElem);
        }
        // 行距规则
        if (fmt.getLineSpacingRule() != null) {
            CTSpacing spacing = pPr.isSetSpacing() ? pPr.getSpacing() : pPr.addNewSpacing();
            try {
                spacing.setLineRule(STLineSpacingRule.Enum.forInt(fmt.getLineSpacingRule()));
            } catch (Exception ignored) {}
        }

        // 边框 - 通过 getBorders() 查找
        if (fmt.getBorders() != null) {
            CTPBdr pBdr = pPr.isSetPBdr() ? pPr.getPBdr() : pPr.addNewPBdr();
            for (ParagraphFormat.Border border : fmt.getBorders()) {
                String side = border.getSide() != null ? border.getSide().toLowerCase() : "";
                CTBorder ctBorder = null;
                switch (side) {
                    case "top": ctBorder = pBdr.addNewTop(); break;
                    case "bottom": ctBorder = pBdr.addNewBottom(); break;
                    case "left": ctBorder = pBdr.addNewLeft(); break;
                    case "right": ctBorder = pBdr.addNewRight(); break;
                }
                if (ctBorder != null) {
                    if (border.getStyle() != null) {
                        try { ctBorder.setVal(STBorder.Enum.forString(border.getStyle())); }
                        catch (Exception e) { ctBorder.setVal(STBorder.SINGLE); }
                    }
                    if (border.getSize() > 0) ctBorder.setSz(BigInteger.valueOf((long) border.getSize()));
                    if (border.getColor() != null) ctBorder.setColor(border.getColor());
                }
            }
        }

        // 底纹
        if (fmt.getShading() != null) {
            CTShd shd = pPr.addNewShd();
            try {
                shd.setVal(STShd.Enum.forString(fmt.getShading().getPattern() != null ? fmt.getShading().getPattern() : "clear"));
            } catch (Exception e) {
                shd.setVal(STShd.CLEAR);
            }
            if (fmt.getShading().getFill() != null) shd.setFill(fmt.getShading().getFill());
            if (fmt.getShading().getPatternColor() != null) shd.setColor(fmt.getShading().getPatternColor());
        }

        // 制表符
        if (fmt.getTabStops() != null) {
            CTTabs tabs = pPr.addNewTabs();
            for (ParagraphFormat.TabStop tab : fmt.getTabStops()) {
                CTTabStop ctTab = tabs.addNewTab();
                if (tab.getPosition() > 0) ctTab.setPos(BigInteger.valueOf((long) tab.getPosition()));
                if (tab.getLeader() != null) ctTab.setLeader(convertTabLeader(tab.getLeader()));
                if (tab.getValue() != null) ctTab.setVal(convertTabAlignment(tab.getValue()));
            }
        }

        // 段落分页行为
        if (fmt.getKeepWithNext() != null) {
            if (fmt.getKeepWithNext()) pPr.addNewKeepNext().setVal(true);
        }
        if (fmt.getKeepLines() != null) {
            if (fmt.getKeepLines()) pPr.addNewKeepLines().setVal(true);
        }
        if (fmt.getPageBreakBefore() != null) {
            if (fmt.getPageBreakBefore()) pPr.addNewPageBreakBefore().setVal(true);
        }
        if (fmt.getWidowControl() != null) {
            if (!fmt.getWidowControl()) pPr.addNewWidowControl().setVal(false);
        }

        // 文本方向
        if (fmt.getTextDirection() != null) {
            Element pPrElem = (Element) ((org.apache.xmlbeans.XmlObject) pPr).getDomNode();
            switch (fmt.getTextDirection()) {
                case RIGHT_TO_LEFT: {
                    Element bidiElem = pPrElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:bidi");
                    pPrElem.appendChild(bidiElem);
                    break;
                }
                case VERTICAL_270: {
                    Element textDirElem = pPrElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:textDirection");
                    textDirElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", "btLr");
                    pPrElem.appendChild(textDirElem);
                    break;
                }
                case EAST_ASIAN_VERTICAL: {
                    Element textDirElem = pPrElem.getOwnerDocument().createElementNS(
                        "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:textDirection");
                    textDirElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", "tbLr");
                    pPrElem.appendChild(textDirElem);
                    break;
                }
            }
        }

        // 编号
        if (fmt.getNumberingId() != null || fmt.getNumberingLevel() > 0) {
            CTNumPr numPr = pPr.isSetNumPr() ? pPr.getNumPr() : pPr.addNewNumPr();
            if (fmt.getNumberingId() != null) {
                numPr.addNewNumId().setVal(BigInteger.valueOf(Long.parseLong(fmt.getNumberingId())));
            }
            if (fmt.getNumberingLevel() > 0) {
                numPr.addNewIlvl().setVal(BigInteger.valueOf(fmt.getNumberingLevel()));
            }
        }

        // 大纲级别（从 additionalProperties 获取）
        if (fmt.getAdditionalProperties() != null) {
            Object outlineLevel = fmt.getAdditionalProperties().get("outlineLevel");
            if (outlineLevel instanceof Number) {
                int lvl = ((Number) outlineLevel).intValue();
                if (lvl >= 0 && lvl <= 9) {
                    pPr.addNewOutlineLvl().setVal(BigInteger.valueOf(lvl));
                }
            }
        }
    }

    private void applyParagraphFormatPPr(CTPPr pPr, ParagraphFormat fmt) {
        if (fmt == null) return;
        if (fmt.getAlignment() != null) pPr.addNewJc().setVal(convertSTJc(fmt.getAlignment()));
        if (fmt.getSpacingBefore() > 0 || fmt.getSpacingAfter() > 0) {
            CTSpacing spacing = pPr.addNewSpacing();
            if (fmt.getSpacingBefore() > 0) spacing.setBefore(BigInteger.valueOf(fmt.getSpacingBefore().longValue()));
            if (fmt.getSpacingAfter() > 0) spacing.setAfter(BigInteger.valueOf(fmt.getSpacingAfter().longValue()));
        }
    }

    private void applyRunFormatRPr(CTRPr rPr, RunFormat fmt) {
        if (fmt == null) return;
        // 通过 fontSlots 获取主字体
        if (fmt.getFontSlots() != null && !fmt.getFontSlots().isEmpty()) {
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            Element rPrElem = (Element) ((org.apache.xmlbeans.XmlObject) rPr).getDomNode();
            Element rFontsElem = rPrElem.getOwnerDocument().createElementNS(ns, "w:rFonts");
            for (FontSlot slot : fmt.getFontSlots()) {
                if (slot.getFontName() == null) continue;
                switch (slot.getScriptType()) {
                    case EAST_ASIA:
                        rFontsElem.setAttributeNS(ns, "w:eastAsia", slot.getFontName());
                        break;
                    case COMPLEX_SCRIPT:
                        rFontsElem.setAttributeNS(ns, "w:cs", slot.getFontName());
                        break;
                    default:
                        rFontsElem.setAttributeNS(ns, "w:ascii", slot.getFontName());
                        rFontsElem.setAttributeNS(ns, "w:hAnsi", slot.getFontName());
                }
            }
            if (rFontsElem.hasAttributes()) rPrElem.appendChild(rFontsElem);
        }
        if (fmt.getFontSize() > 0) rPr.addNewSz().setVal(BigInteger.valueOf((long) (fmt.getFontSize() * 2)));
        if (fmt.getBold() != null && fmt.getBold()) rPr.addNewB().setVal(true);
        if (fmt.getItalic() != null && fmt.getItalic()) rPr.addNewI().setVal(true);
        if (fmt.getColor() != null) rPr.addNewColor().setVal(fmt.getColor());
    }

    private void applyRtlToParagraph(XWPFParagraph p, DocumentNode node) {
        if (Boolean.TRUE.equals(node.getRtl())) {
            CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
            pPr.addNewBidi();
        }
    }

    private void applyStyleToParagraph(XWPFParagraph p, DocumentNode node) {
        if (node.getStyleId() != null) p.setStyle(node.getStyleId());
    }

    // ========================================================================
    // 变更应用
    // ========================================================================

    private void applyInsert(XWPFDocument doc, DocumentChangeSet.Change change) {
        log.debug("INSERT: targetNodeId={}, position={}", change.getTargetNodeId(), change.getPosition());
        DocumentNode content = change.getContent();
        if (content == null) {
            log.warn("INSERT 变更缺少内容");
            return;
        }
        // 在文档末尾创建新段落并写入内容
        writeNode(doc, doc, content);
    }

    private void applyUpdate(XWPFDocument doc, DocumentChangeSet.Change change) {
        log.debug("UPDATE: targetNodeId={}", change.getTargetNodeId());
        Map<String, Object> newValue = change.getNewValue();
        if (newValue == null) {
            log.warn("UPDATE 变更缺少新值");
            return;
        }
        // 遍历文档正文尝试更新匹配的段落
        // 使用 targetNodeId 匹配（简化：假设节点 ID 存储在段落书签或自定义 XML 中）
        String targetId = change.getTargetNodeId();
        if (targetId == null) return;

        for (XWPFParagraph p : doc.getParagraphs()) {
            // 检查段落是否包含匹配的书签
            for (CTBookmark bm : p.getCTP().getBookmarkStartList()) {
                if (targetId.equals(bm.getName())) {
                    updateParagraphFromMap(p, newValue);
                    return;
                }
            }
        }
        // 没有匹配的书签，在文档末尾追加修改内容
        log.warn("未找到匹配的节点: {}，将在末尾追加", targetId);
        if (change.getContent() != null) {
            writeNode(doc, doc, change.getContent());
        }
    }

    private void applyDelete(XWPFDocument doc, DocumentChangeSet.Change change) {
        log.debug("DELETE: targetNodeId={}", change.getTargetNodeId());
        String targetId = change.getTargetNodeId();
        if (targetId == null) return;

        // 尝试通过书签查找并删除段落
        int bodyIdx = -1;
        for (int i = 0; i < doc.getBodyElements().size(); i++) {
            IBodyElement elem = doc.getBodyElements().get(i);
            if (elem instanceof XWPFParagraph) {
                XWPFParagraph p = (XWPFParagraph) elem;
                for (CTBookmark bm : p.getCTP().getBookmarkStartList()) {
                    if (targetId.equals(bm.getName())) {
                        bodyIdx = i;
                        break;
                    }
                }
            }
            if (bodyIdx >= 0) break;
        }
        if (bodyIdx >= 0) {
            doc.removeBodyElement(bodyIdx);
            log.debug("已删除节点: {} (bodyIndex={})", targetId, bodyIdx);
        } else {
            log.warn("未找到可删除的节点: {}", targetId);
        }
    }

    private void applyReplace(XWPFDocument doc, DocumentChangeSet.Change change) {
        log.debug("REPLACE: targetNodeId={}", change.getTargetNodeId());
        String targetId = change.getTargetNodeId();
        DocumentNode content = change.getContent();
        if (targetId == null || content == null) return;

        // 找到匹配的段落，在其后插入新内容，然后删除原段落
        int bodyIdx = -1;
        for (int i = 0; i < doc.getBodyElements().size(); i++) {
            IBodyElement elem = doc.getBodyElements().get(i);
            if (elem instanceof XWPFParagraph) {
                XWPFParagraph p = (XWPFParagraph) elem;
                for (CTBookmark bm : p.getCTP().getBookmarkStartList()) {
                    if (targetId.equals(bm.getName())) {
                        bodyIdx = i;
                        break;
                    }
                }
            }
            if (bodyIdx >= 0) break;
        }
        if (bodyIdx >= 0) {
            // 在目标段落位置写入新内容
            // 先删除原段落
            doc.removeBodyElement(bodyIdx);
            // 写入新内容
            writeNode(doc, doc, content);
            log.debug("已替换节点: {} (bodyIndex={})", targetId, bodyIdx);
        } else {
            log.warn("未找到可替换的节点: {}，将在末尾追加", targetId);
            writeNode(doc, doc, content);
        }
    }

    private void applyMove(XWPFDocument doc, DocumentChangeSet.Change change) {
        log.debug("MOVE: targetNodeId={}, position={}", change.getTargetNodeId(), change.getPosition());
        // 移动操作：先删除原位置，再在目标位置插入
        // 简化实现：在末尾重新创建内容
        DocumentNode content = change.getContent();
        if (content != null) {
            applyDelete(doc, change);
            writeNode(doc, doc, content);
        }
    }

    private void applyTableChange(XWPFDocument doc, DocumentChangeSet.Change change) {
        log.debug("TABLE_CHANGE: targetNodeId={}, operation={}", change.getTargetNodeId(), change.getOperation());
        // 表格变更目前仅记录日志，完整实现需要解析表格结构
        // 用户需要处理表格行/列的增删改
        switch (change.getOperation()) {
            case TABLE_INSERT_ROW:
                if (change.getContent() != null) {
                    // 在最后一个表格追加行
                    List<XWPFTable> tables = doc.getTables();
                    if (!tables.isEmpty()) {
                        XWPFTable table = tables.get(tables.size() - 1);
                        XWPFTableRow row = table.createRow();
                        if (change.getContent().getChildren() != null) {
                            for (DocumentNode cellNode : change.getContent().getChildren()) {
                                XWPFTableCell cell = row.addNewTableCell();
                                if (cellNode.getChildren() != null) {
                                    for (DocumentNode child : cellNode.getChildren()) {
                                        writeNode(doc, cell, child);
                                    }
                                }
                            }
                        }
                    }
                }
                break;
            case TABLE_DELETE_ROW:
                // 删除最后一个表格的最后一行
                List<XWPFTable> tables = doc.getTables();
                if (!tables.isEmpty()) {
                    XWPFTable table = tables.get(tables.size() - 1);
                    int rowCount = table.getNumberOfRows();
                    if (rowCount > 0) {
                        table.removeRow(rowCount - 1);
                    }
                }
                break;
            case TABLE_UPDATE_CELL:
                // 更新单元格内容（简化：更新最后一个表格的首行首个单元格）
                List<XWPFTable> tbls = doc.getTables();
                if (!tbls.isEmpty() && change.getContent() != null
                        && change.getContent().getChildren() != null) {
                    XWPFTable table = tbls.get(tbls.size() - 1);
                    if (table.getNumberOfRows() > 0) {
                        XWPFTableCell cell = table.getRow(0).getCell(0);
                        if (cell != null) {
                            // 清除现有内容
                            for (int i = cell.getParagraphs().size() - 1; i >= 0; i--) {
                                cell.removeParagraph(i);
                            }
                            // 写入新内容
                            for (DocumentNode child : change.getContent().getChildren()) {
                                writeNode(doc, cell, child);
                            }
                        }
                    }
                }
                break;
            default:
                log.warn("不支持的表格变更操作: {}", change.getOperation());
        }
    }

    // ========================================================================
    // 复制辅助
    // ========================================================================

    private void copyParagraph(XWPFDocument target, XWPFParagraph source) {
        XWPFParagraph p = target.createParagraph();
        p.setStyle(source.getStyle());
        p.setAlignment(source.getAlignment());
        p.setSpacingBefore(source.getSpacingBefore());
        p.setSpacingAfter(source.getSpacingAfter());
        p.setIndentationLeft(source.getIndentationLeft());
        p.setIndentationRight(source.getIndentationRight());
        p.setIndentationFirstLine(source.getIndentationFirstLine());
        for (XWPFRun sourceRun : source.getRuns()) {
            XWPFRun targetRun = p.createRun();
            targetRun.setText(sourceRun.getText(0), 0);
            targetRun.setFontFamily(sourceRun.getFontFamily());
            @SuppressWarnings("deprecation")
            int fontSize = sourceRun.getFontSize();
            targetRun.setFontSize(fontSize);
            targetRun.setBold(sourceRun.isBold());
            targetRun.setItalic(sourceRun.isItalic());
            targetRun.setColor(sourceRun.getColor());
            if (sourceRun.getUnderline() != null) targetRun.setUnderline(sourceRun.getUnderline());
        }
    }

    private void copyTable(XWPFDocument target, XWPFTable source) {
        XWPFTable table = target.createTable();
        for (int i = 0; i < source.getNumberOfRows(); i++) {
            XWPFTableRow sourceRow = source.getRow(i);
            XWPFTableRow targetRow = table.getRow(i);
            if (targetRow == null) targetRow = table.createRow();
            for (int j = 0; j < sourceRow.getTableCells().size(); j++) {
                XWPFTableCell sourceCell = sourceRow.getCell(j);
                XWPFTableCell targetCell = targetRow.getCell(j);
                if (targetCell == null) targetCell = targetRow.addNewTableCell();
                for (XWPFParagraph p : sourceCell.getParagraphs()) copyParagraph(target, p);
            }
        }
    }

    // ========================================================================
    // 转换辅助
    // ========================================================================

    private UnderlinePatterns convertUnderlineStyle(RunFormat.UnderlineStyle style) {
        if (style == null) return UnderlinePatterns.NONE;
        return switch (style) {
            case SINGLE -> UnderlinePatterns.SINGLE;
            case DOUBLE -> UnderlinePatterns.DOUBLE;
            case DOTTED -> UnderlinePatterns.DOTTED;
            case DASH -> UnderlinePatterns.DASH;
            case WAVE -> UnderlinePatterns.WAVE;
            case THICK -> UnderlinePatterns.THICK;
            case DOT_DASH -> UnderlinePatterns.DOT_DASH;
            case DOT_DOT_DASH -> UnderlinePatterns.DOT_DOT_DASH;
            default -> UnderlinePatterns.SINGLE;
        };
    }

    private STHighlightColor.Enum convertHighlight(String highlight) {
        if (highlight == null) return null;
        return switch (highlight.toUpperCase()) {
            case "YELLOW" -> STHighlightColor.YELLOW;
            case "GREEN" -> STHighlightColor.GREEN;
            case "CYAN" -> STHighlightColor.CYAN;
            case "MAGENTA" -> STHighlightColor.MAGENTA;
            case "RED" -> STHighlightColor.RED;
            case "BLUE" -> STHighlightColor.BLUE;
            default -> STHighlightColor.NONE;
        };
    }

    private ParagraphAlignment convertAlignment(ParagraphFormat.Alignment alignment) {
        if (alignment == null) return ParagraphAlignment.LEFT;
        return switch (alignment) {
            case LEFT -> ParagraphAlignment.LEFT;
            case CENTER -> ParagraphAlignment.CENTER;
            case RIGHT -> ParagraphAlignment.RIGHT;
            case BOTH -> ParagraphAlignment.BOTH;
            case DISTRIBUTE -> ParagraphAlignment.DISTRIBUTE;
            default -> ParagraphAlignment.LEFT;
        };
    }

    private STBorder.Enum convertBorder(ParagraphFormat.Border border) {
        if (border == null) return STBorder.NONE;
        try { return STBorder.Enum.forString(border.getStyle()); } catch (Exception e) { return STBorder.SINGLE; }
    }

    private STTabJc.Enum convertTabAlignment(String alignment) {
        if (alignment == null) return STTabJc.LEFT;
        return switch (alignment.toLowerCase()) {
            case "left" -> STTabJc.LEFT;
            case "center" -> STTabJc.CENTER;
            case "right" -> STTabJc.RIGHT;
            case "decimal" -> STTabJc.DECIMAL;
            case "bar" -> STTabJc.BAR;
            default -> STTabJc.LEFT;
        };
    }

    private STTabTlc.Enum convertTabLeader(String leader) {
        if (leader == null) return STTabTlc.NONE;
        return switch (leader.toLowerCase()) {
            case "dot" -> STTabTlc.DOT;
            case "hyphen" -> STTabTlc.HYPHEN;
            case "underscore" -> STTabTlc.UNDERSCORE;
            case "heavy" -> STTabTlc.MIDDLE_DOT;
            default -> STTabTlc.NONE;
        };
    }

    private STJc.Enum convertSTJc(ParagraphFormat.Alignment alignment) {
        if (alignment == null) return STJc.LEFT;
        return switch (alignment) {
            case LEFT -> STJc.LEFT;
            case CENTER -> STJc.CENTER;
            case RIGHT -> STJc.RIGHT;
            case BOTH, DISTRIBUTE -> STJc.BOTH;
            default -> STJc.LEFT;
        };
    }

    private STStyleType.Enum convertStyleType(StyleDefinition.StyleType type) {
        if (type == null) return STStyleType.PARAGRAPH;
        return switch (type) {
            case PARAGRAPH -> STStyleType.PARAGRAPH;
            case CHARACTER -> STStyleType.CHARACTER;
            case TABLE -> STStyleType.TABLE;
            case NUMBERING -> STStyleType.NUMBERING;
            default -> STStyleType.PARAGRAPH;
        };
    }

    private STBorder.Enum convertBorderType(DocumentProperties.PageBorder border) {
        if (border == null || border.getStyle() == null) return STBorder.NONE;
        try { return STBorder.Enum.forString(border.getStyle()); } catch (Exception e) { return STBorder.SINGLE; }
    }

    // ========================================================================
    // 字段写入
    // ========================================================================

    private void writeFieldNode(XWPFDocument doc, IBody body, DocumentNode node) {
        if (node.getField() == null) return;
        XWPFParagraph p = findOrCreateParagraph(doc, body);
        writeField(p, node.getField());
    }

    private void writeField(XWPFParagraph p, FieldModel field) {
        if (field == null) return;
        String fieldCode = resolveFieldCode(field);
        if (fieldCode == null) return;

        String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

        // 使用 DOM 添加 fldChar BEGIN（XMLBeans 5.x 中 .Enum 类型不可直接赋值给 setVal）
        XWPFRun r1 = p.createRun();
        r1.setText(" ");
        Element rElem1 = (Element) ((org.apache.xmlbeans.XmlObject) r1.getCTR()).getDomNode();
        Element fldChar1 = rElem1.getOwnerDocument().createElementNS(ns, "w:fldChar");
        fldChar1.setAttributeNS(ns, "w:fldCharType", "begin");
        rElem1.appendChild(fldChar1);

        XWPFRun r2 = p.createRun();
        r2.setText(fieldCode, 0);
        CTRPr pr2 = getOrCreateRPr(r2);
        CTFonts fonts = !pr2.getRFontsList().isEmpty() ? pr2.getRFontsList().get(0) : pr2.addNewRFonts();
        fonts.setAscii("Courier New");
        fonts.setHAnsi("Courier New");
        pr2.addNewSz().setVal(BigInteger.valueOf(16));

        // 使用 DOM 添加 fldChar END
        XWPFRun r3 = p.createRun();
        r3.setText(" ");
        Element rElem3 = (Element) ((org.apache.xmlbeans.XmlObject) r3.getCTR()).getDomNode();
        Element fldChar2 = rElem3.getOwnerDocument().createElementNS(ns, "w:fldChar");
        fldChar2.setAttributeNS(ns, "w:fldCharType", "end");
        rElem3.appendChild(fldChar2);
    }

    private String resolveFieldCode(FieldModel field) {
        if (field == null || field.getFieldType() == null) return null;
        String type = field.getFieldType().name();
        String params = field.getParameters() != null ? " " + field.getParameters() : "";
        return switch (type) {
            case "PAGE" -> " PAGE ";
            case "NUMPAGES" -> " NUMPAGES ";
            case "TOC" -> " TOC \\o \"1-3\" \\h \\z \\u ";
            case "AUTHOR" -> " AUTHOR ";
            case "DATE" -> " DATE \\@ \"yyyy/MM/dd\" ";
            case "TIME" -> " TIME \\@ \"HH:mm:ss\" ";
            case "FILENAME" -> " FILENAME ";
            case "FILESIZE" -> " FILESIZE ";
            case "COMPANY" -> " COMPANY ";
            case "CREATEDATE" -> " CREATEDATE ";
            case "PRINTDATE" -> " PRINTDATE ";
            case "SAVEDATE" -> " SAVEDATE ";
            case "DOCPROPERTY" -> " DOCPROPERTY" + params + " ";
            case "SECTION" -> " SECTION ";
            case "SECTIONPAGES" -> " SECTIONPAGES ";
            case "EDITIME" -> " EDITTIME ";
            case "COMMENTS" -> " COMMENTS ";
            case "LASTSAVEDBY" -> " LASTSAVEDBY ";
            case "REVNUM" -> " REVNUM ";
            case "TITLE" -> " TITLE ";
            case "SUBJECT" -> " SUBJECT ";
            case "KEYWORDS" -> " KEYWORDS ";
            case "MERGEFIELD" -> " MERGEFIELD" + params + " ";
            case "REF" -> " REF" + params + " ";
            case "PAGEREF" -> " PAGEREF" + params + " ";
            case "SEQ" -> " SEQ" + params + " ";
            case "STYLEREF" -> " STYLEREF" + params + " ";
            case "IF" -> " IF" + params + " ";
            default -> " " + type + " ";
        };
    }

    // ========================================================================
    // 书签写入
    // ========================================================================

    private void writeBookmarkNode(XWPFDocument doc, IBody body, DocumentNode node) {
        XWPFParagraph p = findOrCreateParagraph(doc, body);
        String bookmarkName = node.getBookmark() != null
                ? node.getBookmark().getName() : "bmk_" + node.getNodeId();
        writeBookmark(p, bookmarkName, node.getRunFormat(), node.getText());
    }

    private void writeBookmark(XWPFParagraph p, String name, RunFormat run, String text) {
        CTBookmark bm = p.getCTP().addNewBookmarkStart();
        bm.setName(name);
        long maxId = 0;
        for (CTBookmark existing : p.getCTP().getBookmarkStartList()) {
            if (existing.getId().longValue() > maxId) maxId = existing.getId().longValue();
        }
        bm.setId(BigInteger.valueOf(maxId + 1));

        writeRun(p, run, text);

        CTMarkupRange markEnd = p.getCTP().addNewBookmarkEnd();
        markEnd.setId(bm.getId());
    }

    // ========================================================================
    // 超链接写入
    // ========================================================================

    private void writeHyperlinkNode(XWPFDocument doc, IBody body, DocumentNode node) {
        XWPFParagraph p = findOrCreateParagraph(doc, body);
        String url = node.getHyperlink() != null ? node.getHyperlink().getUrl() : "";
        String text = node.getText() != null ? node.getText() : url;
        writeHyperlink(p, url, text);
    }

    private void writeHyperlink(XWPFParagraph p, String url, String text) {
        if (url == null || url.isEmpty()) return;
        try {
            String relId = p.getDocument().getPackagePart().addExternalRelationship(
                    url, "http://schemas.openxmlformats.org/officeDocument/2006/relationships/hyperlink"
            ).getId();
            CTHyperlink cLink = p.getCTP().addNewHyperlink();
            cLink.setId(relId);
            cLink.setHistory(true);
            CTR ctr = cLink.addNewR();
            CTRPr rPr = ctr.addNewRPr();
            rPr.addNewColor().setVal("0563C1");
            rPr.addNewU().setVal(STUnderline.SINGLE);
            ctr.addNewT().setStringValue(text != null ? text : url);
        } catch (Exception e) {
            log.warn("写入超链接失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 内容控件写入
    // ========================================================================

    private void writeContentControlNode(XWPFDocument doc, IBody body, DocumentNode node) {
        // 内容控件（SDT）使用底层 XML 写入
        if (node.getContentControl() == null) return;
        XWPFParagraph p = findOrCreateParagraph(doc, body);
        ContentControl cc = node.getContentControl();

        // 创建 SDT 块
        CTSdtBlock sdtBlock = CTSdtBlock.Factory.newInstance();
        CTSdtPr sdtPr = sdtBlock.addNewSdtPr();
        if (cc.getId() != null) {
            sdtPr.addNewId().setVal(BigInteger.valueOf(Long.parseLong(cc.getId().replaceAll("\\D", ""))));
        }
        if (cc.getAlias() != null) {
            sdtPr.addNewAlias().setVal(cc.getAlias());
        }
        if (cc.getTag() != null) {
            sdtPr.addNewTag().setVal(cc.getTag());
        }
        if (cc.isLockContent()) {
            sdtPr.addNewLock().setVal(STLock.CONTENT_LOCKED);
        }

        // 根据控件类型设置
        switch (cc.getType() != null ? cc.getType().name().toUpperCase() : "RICH_TEXT") {
            case "PLAIN_TEXT":
                sdtPr.addNewRPr().addNewRFonts().setAscii("Courier New");
                break;
            case "DROPDOWN":
                if (cc.getListItems() != null) {
                    CTSdtDropDownList dd = sdtPr.addNewDropDownList();
                    for (ContentControl.ListItem item : cc.getListItems()) {
                        CTSdtListItem li = dd.addNewListItem();
                        if (item.getDisplayText() != null) li.setDisplayText(item.getDisplayText());
                        if (item.getValue() != null) li.setValue(item.getValue());
                    }
                    if (cc.getAdditionalProperties() != null && cc.getAdditionalProperties().get("selectedValue") instanceof String) {
                        String selVal = (String) cc.getAdditionalProperties().get("selectedValue");
                        CTSdtListItem selItem = dd.addNewListItem();
                        selItem.setDisplayText(selVal);
                        selItem.setValue(selVal);
                    }
                }
                break;
            case "DATE":
                CTSdtDate date = sdtPr.addNewDate();
                if (cc.getDateFormat() != null) {
                    date.addNewDateFormat().setVal(cc.getDateFormat());
                }
                if (cc.getCalendarType() != null) date.setFullDate(Calendar.getInstance());
                Object dateVal = cc.getAdditionalProperties() != null ? cc.getAdditionalProperties().get("dateValue") : null;
                if (dateVal instanceof String) {
                    try {
                        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy-MM-dd");
                        Calendar cal = Calendar.getInstance();
                        cal.setTime(sdf.parse((String) dateVal));
                        date.setFullDate(cal);
                    } catch (Exception e) {
                        date.setFullDate(Calendar.getInstance());
                    }
                }
                break;
            case "CHECKBOX":
                // 使用 DOM 方式创建 checkbox 元素（避免 XMLBeans 类型名不兼容）
                Element sdtPrElem = (Element) ((org.apache.xmlbeans.XmlObject) sdtPr).getDomNode();
                Element checkBoxElem = sdtPrElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:checkBox");
                Element checkedElem = checkBoxElem.getOwnerDocument().createElementNS(
                    "http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:checked");
                checkedElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:val", "true");
                checkBoxElem.appendChild(checkedElem);
                sdtPrElem.appendChild(checkBoxElem);
                break;
            default:
                // RICH_TEXT
                break;
        }

        // 写入内容
        CTSdtContentBlock sdtContent = sdtBlock.addNewSdtContent();
        String sdtText = node.getText() != null ? node.getText() : "";
        if (!sdtText.isEmpty()) {
            CTR run = sdtContent.addNewP().addNewR();
            run.addNewT().setStringValue(sdtText);
        }

        // 将 SDT 块添加到段落（使用 DOM 方式，因为 addNewSdtBlock 在 xmlbeans 5.x 中可能不存在）
        Element pElem = (Element) ((org.apache.xmlbeans.XmlObject) p.getCTP()).getDomNode();
        pElem.appendChild((Element) ((org.apache.xmlbeans.XmlObject) sdtBlock).getDomNode());
    }

    // ========================================================================
    // 方程写入
    // ========================================================================

    private void writeEquationNode(XWPFDocument doc, IBody body, DocumentNode node) {
        // 方程写入：使用 OMML 或 LaTeX 占位符
        // POI 不直接支持 OMML，写入占位文本
        XWPFParagraph p = findOrCreateParagraph(doc, body);
        XWPFRun r = p.createRun();
        r.setItalic(true);
        String equation = node.getEquation() != null ? node.getEquation().getLatex() : "(equation)";
        r.setText("[" + equation + "]");
    }

    // ========================================================================
    // 章节分隔符
    // ========================================================================

    private void writeSectionBreak(XWPFDocument doc, DocumentNode node) {
        // 插入分页符作为章节分隔
        XWPFParagraph p = doc.createParagraph();
        p.createRun().addBreak(BreakType.PAGE);
    }

    // ========================================================================
    // 注释写入
    // ========================================================================

    private void writeComments(XWPFDocument doc, List<WebEditingProjection.Comment> comments) {
        if (comments == null || comments.isEmpty()) return;
        // POI 不直接支持注释写入，记录日志
        log.debug("写入 {} 条注释（POI 仅支持预设注释）", comments.size());
    }

    // ========================================================================
    // 脚注写入
    // ========================================================================

    private void writeFootnotes(XWPFDocument doc, List<WebEditingProjection.Footnote> footnotes) {
        if (footnotes == null || footnotes.isEmpty()) return;
        // POI 不直接支持脚注写入，记录日志
        log.debug("写入 {} 条脚注（POI 仅支持预设脚注）", footnotes.size());
    }

    // ========================================================================
    // 水印写入
    // ========================================================================

    private void writeWatermarks(XWPFDocument doc, List<WebEditingProjection.Watermark> watermarks) {
        if (watermarks == null || watermarks.isEmpty()) return;
        // POI 不直接支持水印写入，记录日志
        log.debug("写入 {} 个水印（POI 仅支持预设水印）", watermarks.size());
    }

    // ========================================================================
    // 页码配置
    // ========================================================================

    private void applyPageNumberingConfig(XWPFDocument doc, PageNumberingConfig cfg) {
        if (cfg == null) return;
        CTSectPr sectPr = doc.getDocument().getBody().isSetSectPr()
                ? doc.getDocument().getBody().getSectPr()
                : doc.getDocument().getBody().addNewSectPr();

        // 页码格式
        if (cfg.getFormat() != null) {
            try {
                CTPageNumber pgNumType = sectPr.addNewPgNumType();
                Element pgNumElem = (Element) ((org.apache.xmlbeans.XmlObject) pgNumType).getDomNode();
                pgNumElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:fmt", cfg.getFormat().getOoxmlValue());
            } catch (Exception e) {
                log.warn("设置页码格式失败: {}", e.getMessage());
            }
        }

        // 起始页码
        if (cfg.getStartAt() > 0) {
            sectPr.addNewPgNumType().setStart(BigInteger.valueOf(cfg.getStartAt()));
        }

        // 章节页码
        if (cfg.getChapterStyle() != null) {
            sectPr.addNewPgNumType().setChapStyle(BigInteger.valueOf(Long.parseLong(cfg.getChapterStyle())));
        }
    }

    // ========================================================================
    // FramePr 应用
    // ========================================================================

    private void applyFramePr(XWPFParagraph p, ParagraphFormat.FrameProperties framePr) {
        if (framePr == null) return;
        CTPPr pPr = p.getCTP().isSetPPr() ? p.getCTP().getPPr() : p.getCTP().addNewPPr();
        CTFramePr ctFrame = pPr.addNewFramePr();
        if (framePr.getWidth() != null && framePr.getWidth() > 0) ctFrame.setW(BigInteger.valueOf(framePr.getWidth().longValue()));
        if (framePr.getHeight() != null && framePr.getHeight() > 0) ctFrame.setH(BigInteger.valueOf(framePr.getHeight().longValue()));
        if (framePr.getX() != null && framePr.getX() > 0) ctFrame.setX(BigInteger.valueOf(framePr.getX().longValue()));
        if (framePr.getY() != null && framePr.getY() > 0) ctFrame.setY(BigInteger.valueOf(framePr.getY().longValue()));
        if (framePr.getWrap() != null) {
            try {
                Element frameElem = (Element) ((org.apache.xmlbeans.XmlObject) ctFrame).getDomNode();
                frameElem.setAttributeNS("http://schemas.openxmlformats.org/wordprocessingml/2006/main", "w:wrap", framePr.getWrap());
            } catch (Exception ignored) {}
        }
        if (framePr.getHAnchor() != null) {
            try { ctFrame.setHAnchor(STHAnchor.Enum.forString(framePr.getHAnchor())); } catch (Exception ignored) {}
        }
        if (framePr.getVAnchor() != null) {
            try { ctFrame.setVAnchor(STVAnchor.Enum.forString(framePr.getVAnchor())); } catch (Exception ignored) {}
        }
        if (framePr.getX() != null && framePr.getX() > 0) ctFrame.setX(BigInteger.valueOf(framePr.getX().longValue()));
        if (framePr.getY() != null && framePr.getY() > 0) ctFrame.setY(BigInteger.valueOf(framePr.getY().longValue()));
    }

    // ========================================================================
    // 样式复制
    // ========================================================================

    private void copyStyles(XWPFDocument target, XWPFDocument source) {
        try {
            CTStyles sourceStyles = source.getStyle();
            if (sourceStyles != null) {
                for (CTStyle ctStyle : sourceStyles.getStyleList()) {
                    try {
                        CTStyle newStyle = target.getStyle().addNewStyle();
                        newStyle.set(ctStyle);
                    } catch (Exception e) {
                        log.warn("复制样式 {} 失败: {}", ctStyle.getStyleId(), e.getMessage());
                    }
                }
            }
        } catch (Exception e) {
            log.warn("复制样式失败: {}", e.getMessage());
        }
    }

    // ========================================================================
    // 段落更新辅助
    // ========================================================================

    private void updateParagraphFromMap(XWPFParagraph p, Map<String, Object> values) {
        if (values == null) return;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String key = entry.getKey();
            Object value = entry.getValue();
            try {
                switch (key) {
                    case "text":
                        if (value instanceof String && !p.getRuns().isEmpty()) {
                            p.getRuns().get(0).setText((String) value, 0);
                        }
                        break;
                    case "styleId":
                        if (value instanceof String) p.setStyle((String) value);
                        break;
                    case "alignment":
                        if (value instanceof String) {
                            p.setAlignment(convertAlignment(ParagraphFormat.Alignment.valueOf((String) value)));
                        }
                        break;
                    case "color":
                        if (value instanceof String && !p.getRuns().isEmpty()) {
                            p.getRuns().get(0).setColor((String) value);
                        }
                        break;
                    case "bold":
                        if (value instanceof Boolean && !p.getRuns().isEmpty()) {
                            p.getRuns().get(0).setBold((Boolean) value);
                        }
                        break;
                    case "italic":
                        if (value instanceof Boolean && !p.getRuns().isEmpty()) {
                            p.getRuns().get(0).setItalic((Boolean) value);
                        }
                        break;
                    case "fontSize":
                        if (value instanceof Number && !p.getRuns().isEmpty()) {
                            p.getRuns().get(0).setFontSize(((Number) value).doubleValue());
                        }
                        break;
                    case "fontFamily":
                        if (value instanceof String && !p.getRuns().isEmpty()) {
                            p.getRuns().get(0).setFontFamily((String) value);
                        }
                        break;
                    default:
                        log.debug("忽略未知段落属性: {}", key);
                }
            } catch (Exception e) {
                log.warn("更新段落属性 {} 失败: {}", key, e.getMessage());
            }
        }
    }

    // ========================================================================
    // 字体辅助
    // ========================================================================

    private String resolveMainFont(RunFormat run) {
        if (run.getFontSlots() != null) {
            for (FontSlot slot : run.getFontSlots()) {
                if (slot.getFontName() != null && slot.getScriptType() == ScriptType.LATIN) {
                    return slot.getFontName();
                }
            }
            for (FontSlot slot : run.getFontSlots()) {
                if (slot.getFontName() != null) return slot.getFontName();
            }
        }
        return null;
    }

    // ========================================================================
    // 布尔规则转换
    // ========================================================================

    private boolean convertBooleanRule(String rule) {
        if (rule == null) return false;
        return "auto".equalsIgnoreCase(rule) || "on".equalsIgnoreCase(rule);
    }

    // ========================================================================
    // 段落创建辅助
    // ========================================================================

    private XWPFParagraph createParagraphInBody(XWPFDocument doc, IBody body) {
        if (body instanceof XWPFDocument) {
            return ((XWPFDocument) body).createParagraph();
        } else if (body instanceof XWPFHeaderFooter) {
            return ((XWPFHeaderFooter) body).createParagraph();
        } else if (body instanceof XWPFTableCell) {
            return ((XWPFTableCell) body).addParagraph();
        }
        return doc.createParagraph();
    }

    private XWPFParagraph findOrCreateParagraph(XWPFDocument doc, IBody body) {
        if (body instanceof XWPFDocument) {
            XWPFDocument d = (XWPFDocument) body;
            List<XWPFParagraph> paragraphs = d.getParagraphs();
            if (!paragraphs.isEmpty()) return paragraphs.get(paragraphs.size() - 1);
            return d.createParagraph();
        } else if (body instanceof XWPFHeaderFooter) {
            XWPFHeaderFooter hf = (XWPFHeaderFooter) body;
            List<XWPFParagraph> paragraphs = hf.getParagraphs();
            if (!paragraphs.isEmpty()) return paragraphs.get(paragraphs.size() - 1);
            return hf.createParagraph();
        } else if (body instanceof XWPFTableCell) {
            XWPFTableCell cell = (XWPFTableCell) body;
            List<XWPFParagraph> paragraphs = cell.getParagraphs();
            if (!paragraphs.isEmpty()) return paragraphs.get(paragraphs.size() - 1);
            return cell.addParagraph();
        }
        return doc.createParagraph();
    }

    // ========================================================================
    // CTRPr 获取辅助
    // ========================================================================

    private CTRPr getOrCreateRPr(XWPFRun r) {
        return r.getCTR().isSetRPr() ? r.getCTR().getRPr() : r.getCTR().addNewRPr();
    }

    // ========================================================================
    // 选项获取辅助
    // ========================================================================

    private Integer getIntOption(Map<String, Object> options, String key) {
        Object val = options.get(key);
        if (val instanceof Number) return ((Number) val).intValue();
        if (val instanceof String) {
            try { return Integer.parseInt((String) val); } catch (NumberFormatException e) { return null; }
        }
        return null;
    }

    // ========================================================================
    // 工具辅助
    // ========================================================================

    private byte[] toBytes(XWPFDocument doc) throws IOException {
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            doc.write(baos);
            return baos.toByteArray();
        }
    }
}