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
import org.docx4j.sharedtypes.STOnOff;
import org.openxmlformats.schemas.wordprocessingml.x2006.main.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;

/**
 * POI 文档读取器 - 读取 Word 文档生成 WebEditingProjection。
 * 支持：i18n（每个脚本字槽、BCP-47 语言标签、复杂文字加粗/斜体/大小）、
 * RTL、段落（framePr、制表表速记、基于字符的缩进）、运行（下划线.color、位置半点）、
 * 表格（hMerge）、样式、页眉/页脚、图片、注释、脚注、水印、书签、超链接、章节、修订、字段、SDT、方程、OLE。
 */
public class PoiDocumentReader {
    private static final Logger log = LoggerFactory.getLogger(PoiDocumentReader.class);

    public WebEditingProjection read(byte[] fileContent) {
        WebEditingProjection p = new WebEditingProjection();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(fileContent));
             XWPFDocument doc = new XWPFDocument(pkg)) {
            readDocProps(doc, p); readRtlConfig(doc, p); readPageNum(doc, p);
            readStyles(doc, p); readBody(doc, p); readHF(doc, p);
            p.setComments(readComments(doc)); p.setFootnotes(readFootnotes(doc)); p.setWatermarks(readWatermarks(doc));
            p.setBookmarks(readBookmarks(doc)); p.setHyperlinks(readHyperlinks(doc)); p.setSections(readSections(doc));
            p.setImages(readImages(doc)); p.setRevisions(readRevisions(doc)); p.setFields(readFields(doc));
            p.setContentControls(readContentControls(doc)); p.setEquations(readEquations(doc)); p.setOleObjects(readOleObjects(doc));
            p.setDocumentId(UUID.randomUUID().toString());
        } catch (Exception e) {
            log.error("读取文档失败", e);
            p.setDocumentId(UUID.randomUUID().toString());
            Map<String, String> meta = new LinkedHashMap<>();
            meta.put("error", e.getMessage()); p.setMetadata(meta);
        }
        return p;
    }

    public WordDocumentAdapter.DocumentStats analyze(byte[] fileContent) {
        WordDocumentAdapter.DocumentStats stats = new WordDocumentAdapter.DocumentStats();
        try (XWPFDocument doc = new XWPFDocument(new ByteArrayInputStream(fileContent))) {
            stats.setParagraphCount(doc.getParagraphs().size());
            stats.setTableCount(doc.getTables().size());
            stats.setImageCount(doc.getAllPictures().size());
            String allText = doc.getParagraphs().stream()
                    .map(XWPFParagraph::getText)
                    .filter(Objects::nonNull)
                    .collect(Collectors.joining("\n"));
            stats.setCharacterCount(allText.length());
            stats.setWordCount(allText.isEmpty() ? 0 : allText.split("\\s+").length);
        } catch (Exception e) { log.error("分析失败", e); }
        return stats;
    }

    // ======================== 文档属性 ========================
    private void readDocProps(XWPFDocument doc, WebEditingProjection p) {
        DocumentProperties props = new DocumentProperties();
        try {
            POIXMLProperties xmlProps = doc.getProperties();
            if (xmlProps != null) {
                POIXMLProperties.CoreProperties core = xmlProps.getCoreProperties();
                if (core != null) {
                    p.setTitle(core.getTitle());
                    Map<String, String> meta = new LinkedHashMap<>();
                    if (core.getCreator() != null) meta.put("creator", core.getCreator());
                    if (core.getDescription() != null) meta.put("description", core.getDescription());
                    if (core.getSubject() != null) meta.put("subject", core.getSubject());
                    if (core.getCreated() != null) meta.put("created", core.getCreated().toString());
                    if (core.getModified() != null) meta.put("modified", core.getModified().toString());
                    p.setMetadata(meta);
                }
            }
        } catch (Exception e) { log.debug("文档属性失败", e); }
        try {
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr != null) {
                if (sectPr.getBidi() != null) props.setRtl(true);
                if (sectPr.getPgBorders() != null) {
                    List<DocumentProperties.PageBorder> borders = new ArrayList<>();
                    addPgBorder(borders, "top", sectPr.getPgBorders().getTop());
                    addPgBorder(borders, "bottom", sectPr.getPgBorders().getBottom());
                    addPgBorder(borders, "left", sectPr.getPgBorders().getLeft());
                    addPgBorder(borders, "right", sectPr.getPgBorders().getRight());
                    props.setPageBorders(borders);
                }
                if (doc.getDocument().getBackground() != null) {
                        Object bgColor = doc.getDocument().getBackground().getColor();
                        if (bgColor != null) props.setPageBackgroundColor(bgColor.toString());
                    }
            }
            XWPFStyles styles = doc.getStyles();
            if (styles != null) {
                DocumentProperties.DocumentDefaults defs = new DocumentProperties.DocumentDefaults();
                XWPFDefaultRunStyle defaultRunStyle = styles.getDefaultRunStyle();
                if (defaultRunStyle != null) {
                    readDefaultRunStyleDOM(defaultRunStyle, defs);
                }
                XWPFDefaultParagraphStyle defaultParaStyle = styles.getDefaultParagraphStyle();
                if (defaultParaStyle != null) {
                    readDefaultParagraphStyleDOM(defaultParaStyle, defs);
                }
                props.setDefaults(defs);
            }
        } catch (Exception e) { log.debug("属性细节失败", e); }
        p.setDocumentProperties(props);
    }
    private void addPgBorder(List<DocumentProperties.PageBorder> b, String s, CTPageBorder x) {
        if (x == null) return;
        DocumentProperties.PageBorder pb = new DocumentProperties.PageBorder();
        pb.setSide(s); pb.setStyle(x.getVal() != null ? x.getVal().toString() : "none");
        pb.setSize(x.getSz() != null ? x.getSz().doubleValue() / 8.0 : 0);
        pb.setColor(x.getColor() != null ? x.getColor().toString() : null);
        pb.setSpace(x.getSpace() != null ? x.getSpace().toString() : "0");
        pb.setShadow(x.getShadow() != null ? (Boolean) x.getShadow() : null); b.add(pb);
    }

    private void readRtlConfig(XWPFDocument doc, WebEditingProjection p) {
        RtlConfiguration rtl = new RtlConfiguration();
        try {
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr != null) {
                if (sectPr.getBidi() != null) { rtl.setRtl(true); rtl.setAutoEnableRtl(true); rtl.setTextDirection(TextDirection.RIGHT_TO_LEFT); }
                if (sectPr.getRtlGutter() != null) rtl.setRtlGutter(sectPr.getRtlGutter().getVal() == STOnOff.ON);
            }
        } catch (Exception e) { log.debug("RTL失败", e); }
        p.setRtlConfig(rtl);
    }

    private void readPageNum(XWPFDocument doc, WebEditingProjection p) {
        PageNumberingConfig cfg = new PageNumberingConfig();
        try {
            CTSectPr sectPr = doc.getDocument().getBody().getSectPr();
            if (sectPr != null && sectPr.getPgNumType() != null) {
                CTPageNumber pn = sectPr.getPgNumType();
                if (pn.getFmt() != null) cfg.setFormat(PageNumberingConfig.PageNumberFormat.fromOoxml(pn.getFmt().toString()));
            }
        } catch (Exception e) { log.debug("页码失败", e); }
        p.setPageNumberingConfig(cfg);
    }

    // ======================== 样式 ========================
    private void readStyles(XWPFDocument doc, WebEditingProjection p) {
        List<StyleDefinition> styles = new ArrayList<>();
        try {
            XWPFStyles ds = doc.getStyles();
            if (ds != null) for (XWPFStyle s : getStyleMap(ds).values()) {
                StyleDefinition sd = readStyle(s);
                if (sd != null) styles.add(sd);
            }
        } catch (Exception e) { log.debug("样式失败", e); }
        p.setStyles(styles);
    }
    private StyleDefinition readStyle(XWPFStyle xs) {
        try {
            StyleDefinition s = new StyleDefinition();
            s.setStyleId(xs.getStyleId()); s.setName(xs.getName());
            CTStyle ct = xs.getCTStyle();
            org.w3c.dom.Element ctElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) ct).getDomNode();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            // 类型（DOM 方式获取，避免 XMLBeans 类型问题）
            String type = ctElem.getAttributeNS(ns, "type");
            if (type != null) {
                switch (type) {
                    case "paragraph": s.setType(StyleDefinition.StyleType.PARAGRAPH); break;
                    case "character": s.setType(StyleDefinition.StyleType.CHARACTER); break;
                    case "table": s.setType(StyleDefinition.StyleType.TABLE); break;
                    case "numbering": s.setType(StyleDefinition.StyleType.NUMBERING); break;
                }
            }
            // basedOn / next / link（DOM 方式）
            s.setBasedOn(getChildElementAttr(ctElem, ns, "basedOn", "val"));
            s.setNextStyle(getChildElementAttr(ctElem, ns, "next", "val"));
            s.setLinkStyle(getChildElementAttr(ctElem, ns, "link", "val"));
            // hidden（DOM 方式）
            s.setHidden(ctElem.getElementsByTagNameNS(ns, "hidden").getLength() > 0);
            // priority（DOM 方式）
            String prio = getChildElementAttr(ctElem, ns, "priority", "val");
            if (prio != null) s.setPriority(Integer.parseInt(prio));
            // pPr / rPr（DOM 方式，避免 CTPPrGeneral 与 CTPPr 类型不兼容）
            org.w3c.dom.NodeList pprNodes = ctElem.getElementsByTagNameNS(ns, "pPr");
            if (pprNodes.getLength() > 0) {
                String pprXml = ((org.apache.xmlbeans.XmlObject) ct.getPPr()).xmlText();
                s.setParagraphFormat(readParagraphFormat(CTPPr.Factory.parse(pprXml)));
            }
            org.w3c.dom.NodeList rprNodes = ctElem.getElementsByTagNameNS(ns, "rPr");
            if (rprNodes.getLength() > 0) {
                String rprXml = ((org.apache.xmlbeans.XmlObject) ct.getRPr()).xmlText();
                s.setRunFormat(readRunFormat(CTRPr.Factory.parse(rprXml)));
                // 字体（从 DOM 获取，避免 CTRPr 的 getRFonts 可能不可用）
                org.w3c.dom.NodeList rFontsList = ctElem.getElementsByTagNameNS(ns, "rFonts");
                if (rFontsList.getLength() > 0) {
                    org.w3c.dom.Element rFonts = (org.w3c.dom.Element) rFontsList.item(0);
                    addFontSlot(s.getFontSlots(), ScriptType.LATIN, rFonts.getAttributeNS(ns, "ascii"));
                    addFontSlot(s.getFontSlots(), ScriptType.EAST_ASIA, rFonts.getAttributeNS(ns, "eastAsia"));
                    addFontSlot(s.getFontSlots(), ScriptType.COMPLEX_SCRIPT, rFonts.getAttributeNS(ns, "cs"));
                }
            }
            return s;
        } catch (Exception e) { log.debug("单个样式失败", e); return null; }
    }
    private void addFontSlot(List<FontSlot> slots, ScriptType t, String name) {
        if (name != null) slots.add(new FontSlot(t, name));
    }

    // ======================== 文档内容 ========================
    private void readBody(XWPFDocument doc, WebEditingProjection p) {
        List<DocumentNode> content = new ArrayList<>();
        for (IBodyElement e : doc.getBodyElements()) {
            DocumentNode node = e instanceof XWPFParagraph ? readParagraph((XWPFParagraph) e) : readTable((XWPFTable) e);
            if (node != null) content.add(node);
        }
        p.setContent(content);
    }

    // ======================== 段落 ========================
    private DocumentNode readParagraph(XWPFParagraph para) {
        DocumentNode node = new DocumentNode();
        node.setNodeId(UUID.randomUUID().toString());
        node.setSupportLevel(SupportLevel.EDITABLE);
        String styleId = para.getStyle();
        node.setStyleId(styleId);
        // 根据样式判断节点类型
        if (styleId != null && styleId.toLowerCase().startsWith("heading")) {
            node.setNodeType(NodeType.HEADING);
        } else {
            node.setNodeType(NodeType.PARAGRAPH);
        }
        node.setParagraphFormat(readParagraphFormat(para.getCTPPr()));
        if (para.getCTPPr() != null && para.getCTPPr().getBidi() != null) { node.setRtl(true); node.setTextDirection(TextDirection.RIGHT_TO_LEFT); }
        for (XWPFRun run : para.getRuns()) {
            DocumentNode rn = readRun(run);
            if (rn != null) node.addChild(rn);
        }
        readParaHyperlinks(para, node);
        // 合并子 RUN 节点的文本到段落节点
        StringBuilder sb = new StringBuilder();
        for (DocumentNode child : node.getChildren()) {
            if (child.getText() != null) {
                sb.append(child.getText());
            }
        }
        node.setText(sb.toString());
        return node;
    }

    private ParagraphFormat readParagraphFormat(CTPPr ppr) {
        if (ppr == null) return null;
        ParagraphFormat fmt = new ParagraphFormat();
        if (ppr.getJc() != null) {
            switch (ppr.getJc().getVal().toString()) {
                case "left": fmt.setAlignment(ParagraphFormat.Alignment.LEFT); break;
                case "center": fmt.setAlignment(ParagraphFormat.Alignment.CENTER); break;
                case "right": fmt.setAlignment(ParagraphFormat.Alignment.RIGHT); break;
                case "both": fmt.setAlignment(ParagraphFormat.Alignment.BOTH); break;
                case "distribute": fmt.setAlignment(ParagraphFormat.Alignment.DISTRIBUTE); break;
            }
        }
        if (ppr.getInd() != null) {
            CTInd ind = ppr.getInd();
            if (ind.getLeft() != null) { fmt.setIndentLeft(((Number) ind.getLeft()).doubleValue() / 1440.0); fmt.setIndentLeftUnit("inch"); }
            if (ind.getLeftChars() != null) { fmt.setIndentLeft(((Number) ind.getLeftChars()).doubleValue()); fmt.setIndentLeftUnit("char"); }
            if (ind.getRight() != null) { fmt.setIndentRight(((Number) ind.getRight()).doubleValue() / 1440.0); fmt.setIndentRightUnit("inch"); }
            if (ind.getRightChars() != null) { fmt.setIndentRight(((Number) ind.getRightChars()).doubleValue()); fmt.setIndentRightUnit("char"); }
            if (ind.getFirstLine() != null) { fmt.setIndentFirstLine(((Number) ind.getFirstLine()).doubleValue() / 1440.0); fmt.setIndentFirstLineUnit("inch"); }
            if (ind.getFirstLineChars() != null) { fmt.setIndentFirstLine(((Number) ind.getFirstLineChars()).doubleValue()); fmt.setIndentFirstLineUnit("char"); }
            if (ind.getHanging() != null) fmt.setIndentHanging(((Number) ind.getHanging()).doubleValue() / 1440.0);
        }
        if (ppr.getSpacing() != null) {
            CTSpacing sp = ppr.getSpacing();
            if (sp.getBefore() != null) fmt.setSpacingBefore(((Number) sp.getBefore()).doubleValue() / 20.0);
            if (sp.getAfter() != null) fmt.setSpacingAfter(((Number) sp.getAfter()).doubleValue() / 20.0);
            if (sp.getLine() != null) { fmt.setLineSpacing(((Number) sp.getLine()).doubleValue() / 240.0); fmt.setLineSpacingRule(sp.getLineRule() != null ? sp.getLineRule().intValue() : 0); }
        }
        if (ppr.getTextDirection() != null) fmt.setTextDirection(TextDirection.fromOoxml(ppr.getTextDirection().getVal().toString()));
        if (ppr.getBidi() != null) fmt.setRtl(ppr.getBidi().getVal() == STOnOff.ON);
        if (ppr.getPStyle() != null) fmt.setStyleId(ppr.getPStyle().getVal());
        if (ppr.getKeepNext() != null) fmt.setKeepWithNext(true);
        if (ppr.getKeepLines() != null) fmt.setKeepLines(true);
        if (ppr.getPageBreakBefore() != null) fmt.setPageBreakBefore(true);
        if (ppr.getWidowControl() != null) fmt.setWidowControl(ppr.getWidowControl().getVal() == STOnOff.ON);
        if (ppr.getTabs() != null) {
            List<ParagraphFormat.TabStop> tabs = new ArrayList<>();
            for (CTTabStop ct : ppr.getTabs().getTabList()) {
                ParagraphFormat.TabStop t = new ParagraphFormat.TabStop();
                t.setPosition(((Number) ct.getPos()).doubleValue() / 1440.0);
                if (ct.getVal() != null) t.setValue(ct.getVal().toString());
                if (ct.getLeader() != null) t.setLeader(ct.getLeader().toString());
                tabs.add(t);
            }
            fmt.setTabStops(tabs);
        }
        if (ppr.getFramePr() != null) {
            CTFramePr fp = ppr.getFramePr();
            ParagraphFormat.FrameProperties f = new ParagraphFormat.FrameProperties();
            if (fp.getX() != null) f.setX(((Number) fp.getX()).doubleValue() / 1440.0);
            if (fp.getY() != null) f.setY(((Number) fp.getY()).doubleValue() / 1440.0);
            if (fp.getW() != null) f.setWidth(((Number) fp.getW()).doubleValue() / 1440.0);
            if (fp.getH() != null) f.setHeight(((Number) fp.getH()).doubleValue() / 1440.0);
            if (fp.getXAlign() != null) f.setXAlign(fp.getXAlign().toString());
            if (fp.getYAlign() != null) f.setYAlign(fp.getYAlign().toString());
            if (fp.getHAnchor() != null) f.setHAnchor(fp.getHAnchor().toString());
            if (fp.getVAnchor() != null) f.setVAnchor(fp.getVAnchor().toString());
            if (fp.getWrap() != null) f.setWrap(fp.getWrap().toString());
            fmt.setFramePr(f);
        }
        if (ppr.getPBdr() != null) {
            List<ParagraphFormat.Border> borders = new ArrayList<>();
            paraBorder(borders, "top", ppr.getPBdr().getTop());
            paraBorder(borders, "bottom", ppr.getPBdr().getBottom());
            paraBorder(borders, "left", ppr.getPBdr().getLeft());
            paraBorder(borders, "right", ppr.getPBdr().getRight());
            fmt.setBorders(borders);
        }
        if (ppr.getShd() != null) {
            ParagraphFormat.Shading shd = new ParagraphFormat.Shading();
            shd.setFill(ppr.getShd().getFill() != null ? ppr.getShd().getFill().toString() : null);
            if (ppr.getShd().getVal() != null) shd.setPattern(ppr.getShd().getVal().toString());
            shd.setPatternColor(ppr.getShd().getColor() != null ? ppr.getShd().getColor().toString() : null);
            fmt.setShading(shd);
        }
        if (ppr.getNumPr() != null) {
            if (ppr.getNumPr().getNumId() != null) fmt.setNumberingId(ppr.getNumPr().getNumId().toString());
            if (ppr.getNumPr().getIlvl() != null) fmt.setNumberingLevel(ppr.getNumPr().getIlvl().getVal().intValue());
        }
        return fmt;
    }
    private void paraBorder(List<ParagraphFormat.Border> b, String s, CTBorder x) {
        if (x == null) return;
        ParagraphFormat.Border border = new ParagraphFormat.Border();
        border.setSide(s); border.setStyle(x.getVal() != null ? x.getVal().toString() : "none");
        border.setSize(x.getSz() != null ? x.getSz().doubleValue() / 8.0 : 0);
        border.setColor(x.getColor() != null ? x.getColor().toString() : null);
        border.setSpace(x.getSpace() != null ? x.getSpace().toString() : "0");
        border.setShadow(x.getShadow() != null ? (Boolean) x.getShadow() : null); b.add(border);
    }
    private void readParaHyperlinks(XWPFParagraph para, DocumentNode node) {
        try {
            XWPFDocument doc = para.getDocument();
            for (XWPFRun run : para.getRuns()) {
                if (run instanceof XWPFHyperlinkRun) {
                    XWPFHyperlinkRun hlRun = (XWPFHyperlinkRun) run;
                    String id = hlRun.getHyperlinkId();
                    if (id != null) {
                        XWPFHyperlink link = doc.getHyperlinkByID(id);
                        if (link != null && link.getURL() != null) {
                            WebEditingProjection.Hyperlink hl = new WebEditingProjection.Hyperlink();
                            hl.setId(id); hl.setUrl(link.getURL()); hl.setExternal(true);
                            node.setField(FieldModel.parse("HYPERLINK \"" + link.getURL() + "\""));
                        }
                    }
                }
            }
        } catch (Exception e) { log.debug("超链接失败", e); }
    }

    // ======================== 运行 ========================
    private DocumentNode readRun(XWPFRun run) {
        if (run == null) return null;
        DocumentNode node = new DocumentNode();
        node.setNodeId(UUID.randomUUID().toString()); node.setNodeType(NodeType.RUN);
        node.setSupportLevel(SupportLevel.EDITABLE); node.setText(run.getText(0));
        node.setRunFormat(readRunFormat(run.getCTR().getRPr()));
        if (run.getCTR() != null && run.getCTR().getRPr() != null) {
            org.w3c.dom.Element rprElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) run.getCTR().getRPr()).getDomNode();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            org.w3c.dom.NodeList rFontsList = rprElem.getElementsByTagNameNS(ns, "rFonts");
            if (rFontsList.getLength() > 0) {
                org.w3c.dom.Element rFonts = (org.w3c.dom.Element) rFontsList.item(0);
                addFontSlot(node.getFontSlots(), ScriptType.LATIN, rFonts.getAttributeNS(ns, "ascii"));
                addFontSlot(node.getFontSlots(), ScriptType.EAST_ASIA, rFonts.getAttributeNS(ns, "eastAsia"));
                addFontSlot(node.getFontSlots(), ScriptType.COMPLEX_SCRIPT, rFonts.getAttributeNS(ns, "cs"));
            }
            // 语言标签（DOM 方式）
            org.w3c.dom.NodeList langList = rprElem.getElementsByTagNameNS(ns, "lang");
            if (langList.getLength() > 0) {
                org.w3c.dom.Element lang = (org.w3c.dom.Element) langList.item(0);
                String val = lang.getAttributeNS(ns, "val");
                if (val != null && !val.isEmpty()) node.setLanguageTag(new LanguageTag(val, ScriptType.LATIN));
                String ea = lang.getAttributeNS(ns, "eastAsia");
                if (ea != null && !ea.isEmpty()) node.setEastAsianLanguageTag(new LanguageTag(ea, ScriptType.EAST_ASIA));
                String bidi = lang.getAttributeNS(ns, "bidi");
                if (bidi != null && !bidi.isEmpty()) node.setComplexScriptLanguageTag(new LanguageTag(bidi, ScriptType.COMPLEX_SCRIPT));
            }
        }
        List<XWPFPicture> pics = run.getEmbeddedPictures();
        if (pics != null) for (XWPFPicture pic : pics) {
            DocumentNode pn = readPicture(pic);
            if (pn != null) node.addChild(pn);
        }
        return node;
    }

    private RunFormat readRunFormat(CTRPr rpr) {
        if (rpr == null) return null;
        RunFormat fmt = new RunFormat();
        try {
            org.w3c.dom.Element rprElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) rpr).getDomNode();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            // sz / szCs
            String sz = getChildElementAttr(rprElem, ns, "sz", "val");
            if (sz != null) fmt.setFontSize(Double.parseDouble(sz) / 2.0);
            String szCs = getChildElementAttr(rprElem, ns, "szCs", "val");
            if (szCs != null) fmt.setFontSizeComplex(Double.parseDouble(szCs) / 2.0);
            // b / i / bCs / iCs / strike / dstrike / smallCaps / caps
            setOnOff(fmt::setBold, rprElem, ns, "b");
            setOnOff(fmt::setItalic, rprElem, ns, "i");
            setOnOff(fmt::setBoldComplex, rprElem, ns, "bCs");
            setOnOff(fmt::setItalicComplex, rprElem, ns, "iCs");
            setOnOff(fmt::setStrike, rprElem, ns, "strike");
            setOnOff(fmt::setDoubleStrike, rprElem, ns, "dstrike");
            setOnOff(fmt::setSmallCaps, rprElem, ns, "smallCaps");
            setOnOff(fmt::setAllCaps, rprElem, ns, "caps");
            // underline
            org.w3c.dom.NodeList uList = rprElem.getElementsByTagNameNS(ns, "u");
            if (uList.getLength() > 0) {
                org.w3c.dom.Element u = (org.w3c.dom.Element) uList.item(0);
                String uVal = u.getAttributeNS(ns, "val");
                if (uVal != null && !uVal.isEmpty())
                    fmt.setUnderlineStyle(RunFormat.UnderlineStyle.fromOoxml(uVal));
                String uColor = u.getAttributeNS(ns, "color");
                if (uColor != null && !uColor.isEmpty()) fmt.setUnderlineColor(uColor);
            }
            // position
            String pos = getChildElementAttr(rprElem, ns, "position", "val");
            if (pos != null) fmt.setPosition(Integer.parseInt(pos) / 2);
            // spacing
            String spacing = getChildElementAttr(rprElem, ns, "spacing", "val");
            if (spacing != null) fmt.setCharacterSpacing(Integer.parseInt(spacing));
            // color
            String color = getChildElementAttr(rprElem, ns, "color", "val");
            if (color != null) fmt.setColor(color);
            // highlight
            String hl = getChildElementAttr(rprElem, ns, "highlight", "val");
            if (hl != null) fmt.setHighlightColor(hl);
            // lang
            String lang = getChildElementAttr(rprElem, ns, "lang", "val");
            if (lang != null) fmt.setLanguageTag(new LanguageTag(lang, ScriptType.LATIN));
        } catch (Exception e) {
            log.debug("读取运行格式失败", e);
        }
        return fmt;
    }

    // ======================== 表格 ========================
    private DocumentNode readTable(XWPFTable table) {
        DocumentNode node = new DocumentNode();
        node.setNodeId(UUID.randomUUID().toString()); node.setNodeType(NodeType.TABLE);
        node.setSupportLevel(SupportLevel.EDITABLE);
        TableFormat tf = new TableFormat();
        CTTbl ctTbl = table.getCTTbl();
        if (ctTbl.getTblPr() != null) {
            CTTblPr tp = ctTbl.getTblPr();
            if (tp.getTblW() != null && tp.getTblW().getW() != null)
                tf.setWidth(((Number) tp.getTblW().getW()).doubleValue() / 1440.0);
            if (tp.getBidiVisual() != null) tf.setRtl(true);
            if (tp.getJc() != null) {
                switch (tp.getJc().getVal().toString()) {
                    case "center": tf.setAlignment(TableFormat.Alignment.CENTER); break;
                    case "right": tf.setAlignment(TableFormat.Alignment.RIGHT); break;
                    default: tf.setAlignment(TableFormat.Alignment.LEFT);
                }
            }
        }
        if (ctTbl.getTblGrid() != null) {
            List<TableFormat.Column> cols = new ArrayList<>();
            int idx = 0;
            for (CTTblGridCol col : ctTbl.getTblGrid().getGridColList()) {
                TableFormat.Column c = new TableFormat.Column();
                c.setIndex(idx++);
                if (col.getW() != null) c.setWidth(((Number) col.getW()).doubleValue() / 1440.0);
                cols.add(c);
            }
            tf.setColumns(cols); tf.setColumnCount(cols.size());
        }
        List<TableFormat.RowProperties> rowProps = new ArrayList<>();
        for (int ri = 0; ri < table.getRows().size(); ri++) {
            XWPFTableRow row = table.getRow(ri);
            // 创建 TABLE_ROW 节点
            DocumentNode rowNode = new DocumentNode();
            rowNode.setNodeId(UUID.randomUUID().toString());
            rowNode.setNodeType(NodeType.TABLE_ROW);
            rowNode.setSupportLevel(SupportLevel.EDITABLE);

            TableFormat.RowProperties rp = new TableFormat.RowProperties();
            rp.setRowIndex(ri);
            if (row.getCtRow() != null && row.getCtRow().getTrPr() != null) {
                String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
                org.w3c.dom.Element trPrElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) row.getCtRow().getTrPr()).getDomNode();
                String trHeightVal = getChildElementAttr(trPrElem, ns, "trHeight", "val");
                if (trHeightVal != null) {
                    try { rp.setHeight(Double.parseDouble(trHeightVal) / 1440.0); }
                    catch (NumberFormatException ignored) {}
                }
                if (trPrElem.getElementsByTagNameNS(ns, "tblHeader").getLength() > 0) rp.setHeaderRow(true);
            }
            List<TableFormat.CellProperties> cells = new ArrayList<>();
            for (int ci = 0; ci < row.getTableCells().size(); ci++) {
                XWPFTableCell cell = row.getCell(ci);
                // 创建 TABLE_CELL 节点
                DocumentNode cellNode = new DocumentNode();
                cellNode.setNodeId(UUID.randomUUID().toString());
                cellNode.setNodeType(NodeType.TABLE_CELL);
                cellNode.setSupportLevel(SupportLevel.EDITABLE);

                TableFormat.CellProperties cp = new TableFormat.CellProperties();
                cp.setColumnIndex(ci);
                if (cell.getCTTc() != null && cell.getCTTc().getTcPr() != null) {
                    CTTcPr tcPr = cell.getCTTc().getTcPr();
                    if (tcPr.getGridSpan() != null) cp.setColSpan(tcPr.getGridSpan().getVal().intValue());
                    if (tcPr.getVMerge() != null) cp.setVMerge(tcPr.getVMerge().getVal() != STMerge.RESTART);
                    if (tcPr.getHMerge() != null) cp.setHMerge(tcPr.getHMerge().getVal() != STMerge.RESTART);
                    if (tcPr.getTcW() != null && tcPr.getTcW().getW() != null)
                        cp.setWidth(((Number) tcPr.getTcW().getW()).doubleValue() / 1440.0);
                }
                // 将单元格内的段落作为 TABLE_CELL 的子节点
                for (IBodyElement e : cell.getBodyElements()) {
                    DocumentNode cn = e instanceof XWPFParagraph ? readParagraph((XWPFParagraph) e) : null;
                    if (cn != null) cellNode.addChild(cn);
                }
                cells.add(cp);
                rowNode.addChild(cellNode);
            }
            rp.setCells(cells);
            rowProps.add(rp);
            node.addChild(rowNode);
        }
        tf.setRows(rowProps);
        node.setTableFormat(tf);
        return node;
    }

    // ======================== 图片 ========================
    private DocumentNode readPicture(XWPFPicture pic) {
        try {
            DocumentNode node = new DocumentNode();
            node.setNodeId(UUID.randomUUID().toString()); node.setNodeType(NodeType.IMAGE);
            node.setSupportLevel(SupportLevel.EDITABLE);
            WebEditingProjection.ImageResource img = new WebEditingProjection.ImageResource();
            img.setId(UUID.randomUUID().toString()); img.setName(pic.getDescription());
            XWPFPictureData picData = pic.getPictureData();
            if (picData != null) {
                img.setMimeType(picData.getPackagePart().getContentType().toString());
                img.setData(Base64.getEncoder().encodeToString(picData.getData()));
                img.setEncoding("base64");
                try {
                    org.w3c.dom.Element picElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) pic.getCTPicture()).getDomNode();
                    String aNs = "http://schemas.openxmlformats.org/drawingml/2006/main";
                    org.w3c.dom.NodeList extList = picElem.getElementsByTagNameNS(aNs, "ext");
                    if (extList.getLength() > 0) {
                        org.w3c.dom.Element ext = (org.w3c.dom.Element) extList.item(0);
                        String cx = ext.getAttributeNS(aNs, "cx");
                        String cy = ext.getAttributeNS(aNs, "cy");
                        if (cx != null && !cx.isEmpty()) img.setWidth(Double.parseDouble(cx) / 914400.0 * 72.0);
                        if (cy != null && !cy.isEmpty()) img.setHeight(Double.parseDouble(cy) / 914400.0 * 72.0);
                    }
                } catch (Exception e) { log.debug("读取图片尺寸失败", e); }
            }
            node.setImage(img);
            return node;
        } catch (Exception e) { log.debug("图片失败", e); return null; }
    }

    // ======================== 页眉/页脚 ========================
    private void readHF(XWPFDocument doc, WebEditingProjection p) {
        List<WebEditingProjection.HeaderFooter> headers = new ArrayList<>();
        List<WebEditingProjection.HeaderFooter> footers = new ArrayList<>();
        try {
            for (XWPFHeader header : doc.getHeaderList()) {
                if (header != null) {
                    readHFItem(headers, "default", header);
                }
            }
            for (XWPFFooter footer : doc.getFooterList()) {
                if (footer != null) {
                    readHFItem(footers, "default", footer);
                }
            }
        } catch (Exception e) { log.debug("页眉页脚失败", e); }
        p.setHeaders(headers); p.setFooters(footers);
    }
    private void readHFItem(List<WebEditingProjection.HeaderFooter> list, String type, XWPFHeaderFooter hf) {
        if (hf == null) return;
        WebEditingProjection.HeaderFooter item = new WebEditingProjection.HeaderFooter();
        item.setId(UUID.randomUUID().toString()); item.setType(type);
        List<DocumentNode> content = new ArrayList<>();
        for (IBodyElement e : hf.getBodyElements()) {
            DocumentNode node = e instanceof XWPFParagraph ? readParagraph((XWPFParagraph) e) : null;
            if (node != null) content.add(node);
        }
        item.setContent(content);
        list.add(item);
    }

    // ========================================================================
    // 存根方法 - 后续实现完整逻辑
    // ========================================================================

    private List<WebEditingProjection.Comment> readComments(XWPFDocument doc) {
        // TODO: 读取注释
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Footnote> readFootnotes(XWPFDocument doc) {
        // TODO: 读取脚注
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Watermark> readWatermarks(XWPFDocument doc) {
        // TODO: 读取水印
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Bookmark> readBookmarks(XWPFDocument doc) {
        // TODO: 读取书签
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Hyperlink> readHyperlinks(XWPFDocument doc) {
        // TODO: 读取超链接
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Section> readSections(XWPFDocument doc) {
        // TODO: 读取章节
        return new ArrayList<>();
    }

    private List<WebEditingProjection.ImageResource> readImages(XWPFDocument doc) {
        // TODO: 读取图片资源
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Equation> readEquations(XWPFDocument doc) {
        // TODO: 读取方程
        return new ArrayList<>();
    }

    private List<WebEditingProjection.Chart> readCharts(XWPFDocument doc) {
        // TODO: 读取图表
        return new ArrayList<>();
    }

    private List<RevisionModel> readRevisions(XWPFDocument doc) {
        // TODO: 读取修订
        return new ArrayList<>();
    }

    private List<FieldModel> readFields(XWPFDocument doc) {
        // TODO: 读取字段
        return new ArrayList<>();
    }

    private List<ContentControl> readContentControls(XWPFDocument doc) {
        // TODO: 读取内容控件
        return new ArrayList<>();
    }

    private List<WebEditingProjection.OleObject> readOleObjects(XWPFDocument doc) {
        // TODO: 读取 OLE 对象
        return new ArrayList<>();
    }

    private int countNonNull(List<?> list) {
        int count = 0;
        for (Object o : list) if (o != null) count++;
        return count;
    }

    // 通过反射 + DOM API 读取 XWPFDefaultRunStyle 的默认字体属性
    private static void readDefaultRunStyleDOM(XWPFDefaultRunStyle style, DocumentProperties.DocumentDefaults defs) {
        try {
            java.lang.reflect.Method m = findDeclaredMethod(style.getClass(), "getRPr");
            if (m == null) return;
            m.setAccessible(true);
            Object rpr = m.invoke(style);
            if (rpr == null) return;
            org.w3c.dom.Element rprElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) rpr).getDomNode();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            // 字体
            org.w3c.dom.NodeList rFontsList = rprElem.getElementsByTagNameNS(ns, "rFonts");
            if (rFontsList.getLength() > 0) {
                org.w3c.dom.Element rFonts = (org.w3c.dom.Element) rFontsList.item(0);
                String ascii = rFonts.getAttributeNS(ns, "ascii");
                if (ascii != null && !ascii.isEmpty()) defs.setDefaultRunFont(ascii);
                String ea = rFonts.getAttributeNS(ns, "eastAsia");
                if (ea != null && !ea.isEmpty()) defs.setDefaultRunFontEastAsia(ea);
                String cs = rFonts.getAttributeNS(ns, "cs");
                if (cs != null && !cs.isEmpty()) defs.setDefaultRunFontComplex(cs);
            }
            // 字号
            org.w3c.dom.NodeList szList = rprElem.getElementsByTagNameNS(ns, "sz");
            if (szList.getLength() > 0) {
                String val = ((org.w3c.dom.Element) szList.item(0)).getAttributeNS(ns, "val");
                if (val != null && !val.isEmpty()) defs.setDefaultRunFontSize(Double.parseDouble(val) / 2.0);
            }
            // 加粗
            if (rprElem.getElementsByTagNameNS(ns, "b").getLength() > 0) defs.setDefaultRunBold(true);
            // 斜体
            if (rprElem.getElementsByTagNameNS(ns, "i").getLength() > 0) defs.setDefaultRunItalic(true);
        } catch (Exception e) { /* ignore */ }
    }

    // 通过反射 + DOM API 读取 XWPFDefaultParagraphStyle 的默认段落属性
    private static void readDefaultParagraphStyleDOM(XWPFDefaultParagraphStyle style, DocumentProperties.DocumentDefaults defs) {
        try {
            java.lang.reflect.Method m = findDeclaredMethod(style.getClass(), "getPPr");
            if (m == null) return;
            m.setAccessible(true);
            Object ppr = m.invoke(style);
            if (ppr == null) return;
            org.w3c.dom.Element pprElem = (org.w3c.dom.Element) ((org.apache.xmlbeans.XmlObject) ppr).getDomNode();
            String ns = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
            if (pprElem.getElementsByTagNameNS(ns, "bidi").getLength() > 0) defs.setDefaultParagraphRtl(true);
        } catch (Exception e) { /* ignore */ }
    }

    // 获取子元素的指定属性值（DOM 辅助方法）
    private static String getChildElementAttr(org.w3c.dom.Element parent, String ns, String tag, String attr) {
        org.w3c.dom.NodeList list = parent.getElementsByTagNameNS(ns, tag);
        if (list.getLength() > 0) {
            String val = ((org.w3c.dom.Element) list.item(0)).getAttributeNS(ns, attr);
            if (val != null && !val.isEmpty()) return val;
        }
        return null;
    }

    // 设置布尔开关属性（DOM 方式，处理 OOXML on/off 元素）
    private static void setOnOff(java.util.function.Consumer<Boolean> setter, org.w3c.dom.Element parent, String ns, String tag) {
        org.w3c.dom.NodeList list = parent.getElementsByTagNameNS(ns, tag);
        if (list.getLength() == 0) return;
        String val = ((org.w3c.dom.Element) list.item(0)).getAttributeNS(ns, "val");
        if (val == null || val.isEmpty()) {
            setter.accept(true);
        } else {
            setter.accept("on".equalsIgnoreCase(val) || "true".equalsIgnoreCase(val) || "1".equals(val));
        }
    }

    // 在类继承层次中查找指定名称的方法（包括 protected 方法）
    private static java.lang.reflect.Method findDeclaredMethod(Class<?> clazz, String name) {
        for (Class<?> c = clazz; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                return c.getDeclaredMethod(name);
            } catch (NoSuchMethodException e) {
                // continue walking up
            }
        }
        return null;
    }

    // 通过反射获取 XWPFStyles 内部的所有样式 Map
    @SuppressWarnings("unchecked")
    private static java.util.Map<String, XWPFStyle> getStyleMap(XWPFStyles styles) {
        // 尝试常见的字段名：stylesMap, listStyle, styles
        for (String fieldName : new String[]{"stylesMap", "listStyle", "styles"}) {
            try {
                java.lang.reflect.Field f = XWPFStyles.class.getDeclaredField(fieldName);
                f.setAccessible(true);
                Object val = f.get(styles);
                if (val instanceof java.util.Map) {
                    return (java.util.Map<String, XWPFStyle>) val;
                }
            } catch (Exception e) { /* try next */ }
        }
        return java.util.Collections.emptyMap();
    }
}