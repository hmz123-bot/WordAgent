package com.subtlesight.word.util;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.xwpf.usermodel.*;
import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.apache.tika.metadata.Metadata;
import org.apache.tika.parser.AutoDetectParser;
import org.apache.tika.parser.ParseContext;
import org.apache.tika.parser.Parser;
import org.apache.tika.sax.BodyContentHandler;
import org.docx4j.TextUtils;
import org.docx4j.openpackaging.packages.WordprocessingMLPackage;
import org.docx4j.openpackaging.parts.WordprocessingML.MainDocumentPart;
import org.docx4j.wml.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.*;

/**
 * 文档分析工具类，集成 Apache POI、docx4j 和 Apache Tika 三大库。
 * <p>
 * <ul>
 *   <li><b>Apache POI</b> - 文档结构解析、格式信息提取、统计</li>
 *   <li><b>docx4j</b> - OOXML 深层分析、样式提取、文档树遍历</li>
 *   <li><b>Apache Tika</b> - 格式检测、元数据提取、多格式文本提取</li>
 * </ul>
 * </p>
 */
public final class DocumentAnalysisUtil {

    private static final Logger log = LoggerFactory.getLogger(DocumentAnalysisUtil.class);

    private DocumentAnalysisUtil() {
    }

    // ========================================================================
    //  Apache POI 部分 - 文档结构统计与格式信息
    // ========================================================================

    /**
     * 使用 Apache POI 提取详细的文档结构信息。
     */
    public static PoiDocumentInfo analyzeWithPoi(byte[] fileContent) {
        PoiDocumentInfo info = new PoiDocumentInfo();
        try (OPCPackage pkg = OPCPackage.open(new ByteArrayInputStream(fileContent));
             XWPFDocument doc = new XWPFDocument(pkg)) {

            // 段落统计
            List<XWPFParagraph> paragraphs = doc.getParagraphs();
            info.setTotalParagraphs(paragraphs.size());

            // 按段落样式统计
            Map<String, Integer> styleCount = new HashMap<>();
            int headingCount = 0;
            int emptyParagraphs = 0;
            for (XWPFParagraph para : paragraphs) {
                String style = para.getStyle();
                if (style != null) {
                    styleCount.merge(style, 1, Integer::sum);
                    if (style.startsWith("Heading") || style.matches("heading\\d+")) {
                        headingCount++;
                    }
                }
                if (para.getText().trim().isEmpty()) {
                    emptyParagraphs++;
                }
            }
            info.setStyleCounts(styleCount);
            info.setHeadingCount(headingCount);
            info.setEmptyParagraphs(emptyParagraphs);

            // 文本统计
            StringBuilder textBuilder = new StringBuilder();
            for (XWPFParagraph para : paragraphs) {
                textBuilder.append(para.getText()).append("\n");
            }
            String fullText = textBuilder.toString().trim();
            info.setCharacterCount(fullText.length());
            info.setWordCount(fullText.isEmpty() ? 0 : fullText.split("\\s+").length);

            // 表格统计
            info.setTableCount(doc.getTables().size());

            // 图片统计
            info.setImageCount(doc.getAllPictures().size());

            // 节统计
            info.setSectionCount(doc.getBodyElements().size());

            // 页眉页脚统计
            int headerCount = 0, footerCount = 0;
            for (XWPFHeader header : doc.getHeaderList()) {
                if (header != null) headerCount++;
            }
            for (XWPFFooter footer : doc.getFooterList()) {
                if (footer != null) footerCount++;
            }
            info.setHeaderCount(headerCount);
            info.setFooterCount(footerCount);

            // 列表统计
            info.setNumberingCount(doc.getNumbering() != null
                    ? doc.getNumbering().getAbstractNums().size() : 0);

            info.setSuccess(true);
        } catch (Exception e) {
            log.error("POI 分析文档失败", e);
            info.setSuccess(false);
            info.setErrorMessage(e.getMessage());
        }
        return info;
    }

    // ========================================================================
    //  docx4j 部分 - OOXML 深层结构遍历
    // ========================================================================

    /**
     * 使用 docx4j 分析 OOXML 文档结构，获取样式、字体、文档树等信息。
     */
    public static Docx4jDocumentInfo analyzeWithDocx4j(byte[] fileContent) {
        Docx4jDocumentInfo info = new Docx4jDocumentInfo();
        try {
            WordprocessingMLPackage wordPackage = WordprocessingMLPackage.load(new ByteArrayInputStream(fileContent));
            MainDocumentPart mainPart = wordPackage.getMainDocumentPart();

            // 获取文档对象
            org.docx4j.wml.Document wmlDoc = mainPart.getJaxbElement();
            info.setBodyElementCount(wmlDoc.getBody().getContent().size());

            // 解析样式
            List<Map<String, String>> styles = new ArrayList<>();
            try {
                org.docx4j.openpackaging.parts.WordprocessingML.StyleDefinitionsPart styleDefPart =
                        mainPart.getStyleDefinitionsPart();
                if (styleDefPart != null) {
                    org.docx4j.wml.Styles stylesObj = styleDefPart.getJaxbElement();
                    if (stylesObj != null && stylesObj.getStyle() != null) {
                        for (org.docx4j.wml.Style style : stylesObj.getStyle()) {
                            Map<String, String> styleInfo = new HashMap<>();
                            styleInfo.put("styleId", style.getStyleId());
                            try {
                                styleInfo.put("content", TextUtils.getText(style));
                            } catch (Exception e) {
                                styleInfo.put("content", "");
                            }
                            styles.add(styleInfo);
                        }
                    }
                }
            } catch (Exception e) {
                log.debug("解析样式失败: {}", e.getMessage());
            }
            info.setStyles(styles);

            // 遍历文档元素
            List<Map<String, Object>> elementTree = new ArrayList<>();
            int elementIndex = 0;
            for (Object content : wmlDoc.getBody().getContent()) {
                elementTree.add(describeDocx4jElement(content, elementIndex++));
            }
            info.setElementTree(elementTree);

            // 解析字体信息
            List<String> fonts = new ArrayList<>();
            for (Object content : wmlDoc.getBody().getContent()) {
                extractFonts(content, fonts);
            }
            info.setFonts(fonts);

            // 统计段落数
            long paragraphCount = wmlDoc.getBody().getContent().stream()
                    .filter(P.class::isInstance)
                    .count();
            info.setParagraphCount((int) paragraphCount);

            // 统计表格数
            long tableCount = wmlDoc.getBody().getContent().stream()
                    .filter(org.docx4j.wml.Tbl.class::isInstance)
                    .count();
            info.setTableCount((int) tableCount);

            info.setSuccess(true);
        } catch (Exception e) {
            log.error("docx4j 分析文档失败", e);
            info.setSuccess(false);
            info.setErrorMessage(e.getMessage());
        }
        return info;
    }

    private static Map<String, Object> describeDocx4jElement(Object element, int index) {
        Map<String, Object> desc = new LinkedHashMap<>();
        desc.put("index", index);
        desc.put("type", element.getClass().getSimpleName());

        if (element instanceof P) {
            P p = (P) element;
            // 提取段落样式
            if (p.getPPr() != null && p.getPPr().getPStyle() != null) {
                desc.put("style", p.getPPr().getPStyle().getVal());
            }
            // 提取段落文本
            StringBuilder text = new StringBuilder();
            if (p.getContent() != null) {
                for (Object run : p.getContent()) {
                    if (run instanceof R) {
                        R r = (R) run;
                        if (r.getContent() != null) {
                            for (Object rContent : r.getContent()) {
                                if (rContent instanceof Text) {
                                    text.append(((Text) rContent).getValue());
                                }
                            }
                        }
                    }
                }
            }
            desc.put("text", text.length() > 100 ? text.substring(0, 100) + "..." : text.toString());
            desc.put("runCount", p.getContent() != null ? p.getContent().size() : 0);
        } else if (element instanceof Tbl) {
            Tbl tbl = (Tbl) element;
            desc.put("rowCount", tbl.getContent() != null ? tbl.getContent().size() : 0);
        }

        return desc;
    }

    private static void extractFonts(Object element, List<String> fonts) {
        if (element instanceof P) {
            P p = (P) element;
            if (p.getContent() != null) {
                for (Object run : p.getContent()) {
                    if (run instanceof R) {
                        R r = (R) run;
                        if (r.getRPr() != null && r.getRPr().getRFonts() != null) {
                            RFonts rFonts = r.getRPr().getRFonts();
                            addIfNotNull(fonts, rFonts.getAscii());
                            addIfNotNull(fonts, rFonts.getEastAsia());
                            addIfNotNull(fonts, rFonts.getHAnsi());
                        }
                    }
                }
            }
        }
    }

    private static void addIfNotNull(List<String> list, String value) {
        if (value != null && !value.isEmpty() && !list.contains(value)) {
            list.add(value);
        }
    }

    // ========================================================================
    //  Apache Tika 部分 - 格式检测、元数据提取
    // ========================================================================

    /**
     * 使用 Apache Tika 检测文档格式。
     */
    public static String detectFormat(byte[] fileContent) {
        Tika tika = new Tika();
        try {
            return tika.detect(fileContent);
        } catch (Exception e) {
            log.warn("Tika 格式检测失败", e);
            return "unknown";
        }
    }

    /**
     * 使用 Apache Tika 提取文档元数据。
     */
    public static TikaMetadata extractMetadata(byte[] fileContent) {
        TikaMetadata result = new TikaMetadata();
        try {
            Parser parser = new AutoDetectParser();
            Metadata metadata = new Metadata();
            BodyContentHandler handler = new BodyContentHandler(-1);
            ParseContext context = new ParseContext();

            parser.parse(new ByteArrayInputStream(fileContent), handler, metadata, context);

            // 提取元数据
            Map<String, String> metadataMap = new LinkedHashMap<>();
            for (String name : metadata.names()) {
                metadataMap.put(name, metadata.get(name));
            }
            result.setMetadata(metadataMap);

            // 提取纯文本内容
            String text = handler.toString();
            result.setExtractedText(text);
            result.setCharacterCount(text.length());
            // 提取文本语言
            result.setDetectedFormat(metadata.get("Content-Type"));

            // 常用元数据字段
            result.setTitle(metadata.get("title"));
            result.setAuthor(metadata.get("Author") != null ? metadata.get("Author") : metadata.get("creator"));
            result.setCreatedDate(metadata.get("created") != null ? metadata.get("created") : metadata.get("dcterms:created"));
            result.setModifiedDate(metadata.get("modified"));
            result.setLastAuthor(metadata.get("Last-Author") != null ? metadata.get("Last-Author") : metadata.get("lastModifiedBy"));
            result.setApplication(metadata.get("Application") != null ? metadata.get("Application") : metadata.get("generator"));
            result.setRevisionNumber(metadata.get("revision"));
            result.setPageCount(metadata.get("xmpTPg:NPages"));
            result.setWordCount(metadata.get("wordCount"));
            result.setCharacterCountWithSpaces(metadata.get("charCountWithSpaces"));

            result.setSuccess(true);
        } catch (TikaException | SAXException | IOException e) {
            log.error("Tika 元数据提取失败", e);
            result.setSuccess(false);
            result.setErrorMessage(e.getMessage());
        }
        return result;
    }

    /**
     * 使用 Apache Tika 提取纯文本（支持多种格式：docx, pdf, txt, html 等）。
     */
    public static String extractText(byte[] fileContent) {
        Tika tika = new Tika();
        try {
            return tika.parseToString(new ByteArrayInputStream(fileContent));
        } catch (Exception e) {
            log.error("Tika 文本提取失败", e);
            return "";
        }
    }

    // ========================================================================
    //  综合文档分析报告
    // ========================================================================

    /**
     * 生成综合文档分析报告，整合三大库的分析结果。
     */
    public static DocumentAnalysisReport generateFullReport(byte[] fileContent) {
        DocumentAnalysisReport report = new DocumentAnalysisReport();

        // 1. Tika 格式检测
        report.setDetectedFormat(detectFormat(fileContent));
        report.setTikaMetadata(extractMetadata(fileContent));

        // 2. POI 结构分析（仅对 docx 格式）
        if (isDocx(fileContent)) {
            report.setPoiInfo(analyzeWithPoi(fileContent));
            report.setDocx4jInfo(analyzeWithDocx4j(fileContent));

            // 3. 综合指标（使用内部数据类整合）
            if (report.getPoiInfo() != null && report.getPoiInfo().isSuccess()) {
                Map<String, Object> combined = new LinkedHashMap<>();
                combined.put("paragraphCount", report.getPoiInfo().getTotalParagraphs());
                combined.put("tableCount", report.getPoiInfo().getTableCount());
                combined.put("imageCount", report.getPoiInfo().getImageCount());
                combined.put("headerCount", report.getPoiInfo().getHeaderCount());
                combined.put("footerCount", report.getPoiInfo().getFooterCount());
                report.setCombinedStats(combined);
            }
        }

        report.setSuccess(true);
        return report;
    }

    /**
     * 检查是否为 .docx 格式。
     */
    private static boolean isDocx(byte[] fileContent) {
        String format = detectFormat(fileContent);
        return format != null && format.contains("officedocument.wordprocessingml");
    }

    // ========================================================================
    //  内部数据类
    // ========================================================================

    /**
     * Apache POI 分析结果。
     */
    public static class PoiDocumentInfo {
        private boolean success;
        private String errorMessage;
        private int totalParagraphs;
        private int headingCount;
        private int emptyParagraphs;
        private int characterCount;
        private int wordCount;
        private int tableCount;
        private int imageCount;
        private int sectionCount;
        private int headerCount;
        private int footerCount;
        private int numberingCount;
        private Map<String, Integer> styleCounts = new HashMap<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getTotalParagraphs() { return totalParagraphs; }
        public void setTotalParagraphs(int totalParagraphs) { this.totalParagraphs = totalParagraphs; }
        public int getHeadingCount() { return headingCount; }
        public void setHeadingCount(int headingCount) { this.headingCount = headingCount; }
        public int getEmptyParagraphs() { return emptyParagraphs; }
        public void setEmptyParagraphs(int emptyParagraphs) { this.emptyParagraphs = emptyParagraphs; }
        public int getCharacterCount() { return characterCount; }
        public void setCharacterCount(int characterCount) { this.characterCount = characterCount; }
        public int getWordCount() { return wordCount; }
        public void setWordCount(int wordCount) { this.wordCount = wordCount; }
        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }
        public int getImageCount() { return imageCount; }
        public void setImageCount(int imageCount) { this.imageCount = imageCount; }
        public int getSectionCount() { return sectionCount; }
        public void setSectionCount(int sectionCount) { this.sectionCount = sectionCount; }
        public int getHeaderCount() { return headerCount; }
        public void setHeaderCount(int headerCount) { this.headerCount = headerCount; }
        public int getFooterCount() { return footerCount; }
        public void setFooterCount(int footerCount) { this.footerCount = footerCount; }
        public int getNumberingCount() { return numberingCount; }
        public void setNumberingCount(int numberingCount) { this.numberingCount = numberingCount; }
        public Map<String, Integer> getStyleCounts() { return styleCounts; }
        public void setStyleCounts(Map<String, Integer> styleCounts) { this.styleCounts = styleCounts; }
    }

    /**
     * docx4j 分析结果。
     */
    public static class Docx4jDocumentInfo {
        private boolean success;
        private String errorMessage;
        private int bodyElementCount;
        private int paragraphCount;
        private int tableCount;
        private List<Map<String, String>> styles = new ArrayList<>();
        private List<Map<String, Object>> elementTree = new ArrayList<>();
        private List<String> fonts = new ArrayList<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public int getBodyElementCount() { return bodyElementCount; }
        public void setBodyElementCount(int bodyElementCount) { this.bodyElementCount = bodyElementCount; }
        public int getParagraphCount() { return paragraphCount; }
        public void setParagraphCount(int paragraphCount) { this.paragraphCount = paragraphCount; }
        public int getTableCount() { return tableCount; }
        public void setTableCount(int tableCount) { this.tableCount = tableCount; }
        public List<Map<String, String>> getStyles() { return styles; }
        public void setStyles(List<Map<String, String>> styles) { this.styles = styles; }
        public List<Map<String, Object>> getElementTree() { return elementTree; }
        public void setElementTree(List<Map<String, Object>> elementTree) { this.elementTree = elementTree; }
        public List<String> getFonts() { return fonts; }
        public void setFonts(List<String> fonts) { this.fonts = fonts; }
    }

    /**
     * Apache Tika 元数据提取结果。
     */
    public static class TikaMetadata {
        private boolean success;
        private String errorMessage;
        private String title;
        private String author;
        private String createdDate;
        private String modifiedDate;
        private String lastAuthor;
        private String application;
        private String revisionNumber;
        private String pageCount;
        private String wordCount;
        private String characterCountWithSpaces;
        private int characterCount;
        private String detectedFormat;
        private String extractedText;
        private Map<String, String> metadata = new LinkedHashMap<>();

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getErrorMessage() { return errorMessage; }
        public void setErrorMessage(String errorMessage) { this.errorMessage = errorMessage; }
        public String getTitle() { return title; }
        public void setTitle(String title) { this.title = title; }
        public String getAuthor() { return author; }
        public void setAuthor(String author) { this.author = author; }
        public String getCreatedDate() { return createdDate; }
        public void setCreatedDate(String createdDate) { this.createdDate = createdDate; }
        public String getModifiedDate() { return modifiedDate; }
        public void setModifiedDate(String modifiedDate) { this.modifiedDate = modifiedDate; }
        public String getLastAuthor() { return lastAuthor; }
        public void setLastAuthor(String lastAuthor) { this.lastAuthor = lastAuthor; }
        public String getApplication() { return application; }
        public void setApplication(String application) { this.application = application; }
        public String getRevisionNumber() { return revisionNumber; }
        public void setRevisionNumber(String revisionNumber) { this.revisionNumber = revisionNumber; }
        public String getPageCount() { return pageCount; }
        public void setPageCount(String pageCount) { this.pageCount = pageCount; }
        public String getWordCount() { return wordCount; }
        public void setWordCount(String wordCount) { this.wordCount = wordCount; }
        public String getCharacterCountWithSpaces() { return characterCountWithSpaces; }
        public void setCharacterCountWithSpaces(String characterCountWithSpaces) { this.characterCountWithSpaces = characterCountWithSpaces; }
        public int getCharacterCount() { return characterCount; }
        public void setCharacterCount(int characterCount) { this.characterCount = characterCount; }
        public String getDetectedFormat() { return detectedFormat; }
        public void setDetectedFormat(String detectedFormat) { this.detectedFormat = detectedFormat; }
        public String getExtractedText() { return extractedText; }
        public void setExtractedText(String extractedText) { this.extractedText = extractedText; }
        public Map<String, String> getMetadata() { return metadata; }
        public void setMetadata(Map<String, String> metadata) { this.metadata = metadata; }
    }

    /**
     * 综合文档分析报告。
     */
    public static class DocumentAnalysisReport {
        private boolean success;
        private String detectedFormat;
        private TikaMetadata tikaMetadata;
        private PoiDocumentInfo poiInfo;
        private Docx4jDocumentInfo docx4jInfo;
        private Map<String, Object> combinedStats;

        public boolean isSuccess() { return success; }
        public void setSuccess(boolean success) { this.success = success; }
        public String getDetectedFormat() { return detectedFormat; }
        public void setDetectedFormat(String detectedFormat) { this.detectedFormat = detectedFormat; }
        public TikaMetadata getTikaMetadata() { return tikaMetadata; }
        public void setTikaMetadata(TikaMetadata tikaMetadata) { this.tikaMetadata = tikaMetadata; }
        public PoiDocumentInfo getPoiInfo() { return poiInfo; }
        public void setPoiInfo(PoiDocumentInfo poiInfo) { this.poiInfo = poiInfo; }
        public Docx4jDocumentInfo getDocx4jInfo() { return docx4jInfo; }
        public void setDocx4jInfo(Docx4jDocumentInfo docx4jInfo) { this.docx4jInfo = docx4jInfo; }
        public Map<String, Object> getCombinedStats() { return combinedStats; }
        public void setCombinedStats(Map<String, Object> combinedStats) { this.combinedStats = combinedStats; }
    }
}