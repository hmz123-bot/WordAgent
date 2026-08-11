package com.subtlesight.word.util;

import java.util.Map;
import java.util.Set;

/**
 * OOXML 常量定义，用于文档解析、校验和节点类型映射。
 */
public final class OoxmlConstants {

    private OoxmlConstants() {
    }

    // ========== 命名空间 ==========
    public static final String NS_WORD = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";
    public static final String NS_RELATIONSHIPS = "http://schemas.openxmlformats.org/package/2006/relationships";
    public static final String NS_DRAWING = "http://schemas.openxmlformats.org/drawingml/2006/main";
    public static final String NS_PICTURE = "http://schemas.openxmlformats.org/drawingml/2006/picture";
    public static final String NS_OFFICE = "urn:schemas-microsoft-com:office:office";
    public static final String NS_VML = "urn:schemas-microsoft-com:vml";

    // ========== 必需部件 ==========
    public static final String PART_DOCUMENT = "word/document.xml";
    public static final String PART_CONTENT_TYPES = "[Content_Types].xml";
    public static final String PART_RELATIONSHIPS = "_rels/.rels";

    public static final Set<String> REQUIRED_PARTS = Set.of(
            PART_DOCUMENT, PART_CONTENT_TYPES, PART_RELATIONSHIPS
    );

    // ========== 样式类型映射 ==========
    public static final Set<String> HEADING_STYLES = Set.of(
            "heading1", "heading2", "heading3", "heading4", "heading5", "heading6",
            "1", "2", "3", "4", "5", "6"
    );

    public static final Set<String> LIST_STYLES = Set.of(
            "ListParagraph", "ListBullet", "ListNumber"
    );

    // ========== 支持的元素类型 ==========
    public static final Set<String> DIRECTLY_EDITABLE = Set.of(
            "paragraph", "run", "heading", "list", "listItem", "table",
            "tableRow", "tableCell", "image", "hyperlink", "bookmark"
    );

    public static final Set<String> READ_ONLY = Set.of(
            "chart", "equation", "smartArt", "oleObject", "3dModel"
    );

    public static final Set<String> UNSUPPORTED = Set.of(
            "activeX", "macro", "vba"
    );

    // ========== 图片类型映射 ==========
    public static final Map<String, String> IMAGE_EXTENSION_TO_CONTENT_TYPE = Map.of(
            "png", "image/png",
            "jpg", "image/jpeg",
            "jpeg", "image/jpeg",
            "gif", "image/gif",
            "svg", "image/svg+xml",
            "bmp", "image/bmp",
            "tiff", "image/tiff",
            "tif", "image/tiff"
    );

    public static final Map<String, Integer> IMAGE_CONTENT_TYPE_TO_POI_TYPE = Map.of(
            "image/png", 6,      // XWPFDocument.PICTURE_TYPE_PNG
            "image/jpeg", 5,     // XWPFDocument.PICTURE_TYPE_JPEG
            "image/gif", 8,      // XWPFDocument.PICTURE_TYPE_GIF
            "image/bmp", 7       // XWPFDocument.PICTURE_TYPE_BMP
    );

    // ========== 安全限制 ==========
    public static final long MAX_FILE_SIZE = 50 * 1024 * 1024L;       // 50MB
    public static final long MAX_UNZIPPED_SIZE = 500 * 1024 * 1024L;  // 500MB
    public static final int MAX_XML_DEPTH = 100;
    public static final int MAX_ZIP_ENTRIES = 5000;

    }