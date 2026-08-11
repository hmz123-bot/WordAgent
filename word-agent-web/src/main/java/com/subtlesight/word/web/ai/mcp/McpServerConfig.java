package com.subtlesight.word.web.ai.mcp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.*;

/**
 * MCP Server 配置 — 将编辑器工具通过 MCP 协议暴露。
 *
 * 任何支持 MCP 的客户端（Claude Desktop、Cursor 等）都能直接操作你这文档。
 *
 * MCP 协议使用 JSON-RPC over stdio，此处定义工具清单和 schema。
 * 实际传输层由上层 MCP 框架（如 mcp-java）处理。
 */
@Component
public class McpServerConfig {

    private static final Logger log = LoggerFactory.getLogger(McpServerConfig.class);

    @Value("${ai.mcp.enabled:false}")
    private boolean mcpEnabled;

    /**
     * 返回编辑器暴露为 MCP tool 的定义清单
     */
    public List<McpToolDefinition> getExposedTools() {
        if (!mcpEnabled) return List.of();

        return List.of(
            new McpToolDefinition("docx.read_document",
                "读取文档全部内容",
                Map.of()),
            new McpToolDefinition("docx.read_paragraph",
                "读取指定段落",
                Map.of("paragraphId", property("string", "段落 ID"))),
            new McpToolDefinition("docx.insert_paragraph",
                "在指定位置插入段落",
                Map.of(
                    "afterParagraphId", property("string", "插入位置（段落之后）"),
                    "text", property("string", "段落内容")
                )),
            new McpToolDefinition("docx.suggest_edit",
                "建议修改文档内容（需用户审核）",
                Map.of(
                    "paragraphId", property("string", "目标段落 ID"),
                    "description", property("string", "修改说明"),
                    "suggestedContent", property("string", "建议内容")
                )),
            new McpToolDefinition("docx.get_paragraphs_by_style",
                "按样式过滤段落",
                Map.of("style", property("string", "样式名: heading1/heading2/body")))
        );
    }

    public boolean isEnabled() { return mcpEnabled; }

    // --- 类型 ---

    public record McpToolDefinition(String name, String description, Map<String, McpProperty> parameters) {}
    public record McpProperty(String type, String description) {}

    private static McpProperty property(String type, String description) {
        return new McpProperty(type, description);
    }
}
