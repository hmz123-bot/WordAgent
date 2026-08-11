package com.subtlesight.word.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 文档清洗工具。
 * <p>
 * 在导入时执行安全清洗：移除宏、ActiveX、VBA 等不安全元素。
 * 通过修改 OOXML 包内的部件实现。
 * </p>
 */
public final class DocumentSanitizer {

    private static final Logger log = LoggerFactory.getLogger(DocumentSanitizer.class);

    private DocumentSanitizer() {
    }

    /**
     * 清洗文档：移除不安全元素。
     */
    public static byte[] sanitize(byte[] fileContent) {
        log.info("清洗文档内容");

        try {
            Map<String, byte[]> entries = readZipEntries(fileContent);
            Map<String, byte[]> sanitized = new LinkedHashMap<>();

            // 过滤掉危险部件
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                String name = entry.getKey().toLowerCase();

                // 移除 VBA/宏
                if (name.contains("vba") || name.contains("macro") || name.endsWith(".bin")) {
                    log.warn("移除危险部件: {}", entry.getKey());
                    continue;
                }

                // 移除 ActiveX
                if (name.contains("activex") || name.contains("control")) {
                    log.warn("移除 ActiveX 部件: {}", entry.getKey());
                    continue;
                }

                sanitized.put(entry.getKey(), entry.getValue());
            }

            // 更新 [Content_Types].xml
            sanitized.put("[Content_Types].xml", buildCleanContentTypes(sanitized));

            return writeZipEntries(sanitized);

        } catch (Exception e) {
            log.error("清洗文档失败", e);
            return fileContent;
        }
    }

    /**
     * 检查是否包含 VBA 宏。
     */
    public static boolean hasMacros(byte[] fileContent) {
        try {
            Map<String, byte[]> entries = readZipEntries(fileContent);
            return entries.keySet().stream()
                    .anyMatch(name -> name.toLowerCase().contains("vba"));
        } catch (Exception e) {
            return false;
        }
    }

    private static Map<String, byte[]> readZipEntries(byte[] content) throws IOException {
        Map<String, byte[]> entries = new LinkedHashMap<>();
        try (ZipInputStream zis = new ZipInputStream(new ByteArrayInputStream(content))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int read;
                while ((read = zis.read(buffer)) != -1) {
                    baos.write(buffer, 0, read);
                }
                entries.put(entry.getName(), baos.toByteArray());
            }
        }
        return entries;
    }

    private static byte[] writeZipEntries(Map<String, byte[]> entries) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zos = new ZipOutputStream(baos)) {
            for (Map.Entry<String, byte[]> entry : entries.entrySet()) {
                ZipEntry ze = new ZipEntry(entry.getKey());
                ze.setSize(entry.getValue().length);
                zos.putNextEntry(ze);
                zos.write(entry.getValue());
                zos.closeEntry();
            }
        }
        return baos.toByteArray();
    }

    private static byte[] buildCleanContentTypes(Map<String, byte[]> entries) {
        StringBuilder xml = new StringBuilder();
        xml.append("<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n");
        xml.append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">\n");

        // 默认类型
        xml.append("  <Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>\n");
        xml.append("  <Default Extension=\"xml\" ContentType=\"application/xml\"/>\n");

        // 根据实际条目添加覆盖类型
        for (String name : entries.keySet()) {
            String ext = name.contains(".") ? name.substring(name.lastIndexOf('.') + 1) : "";
            if (!ext.equals("rels") && !ext.equals("xml")) {
                xml.append("  <Default Extension=\"").append(ext)
                        .append("\" ContentType=\"application/octet-stream\"/>\n");
            }
        }

        // 必需覆盖
        if (entries.containsKey("word/document.xml")) {
            xml.append("  <Override PartName=\"/word/document.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml\"/>\n");
        }

        xml.append("</Types>");
        return xml.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }
}