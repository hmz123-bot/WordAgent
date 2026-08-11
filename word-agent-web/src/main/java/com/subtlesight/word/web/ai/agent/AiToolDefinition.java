package com.subtlesight.word.web.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * AI Tool Definitions — 把编辑器的能力暴露成 OpenAI function-calling schema。
 *
 * Agent 模式的核心：模型决定调哪个 tool，结果回传前端执行到 live editor。
 * 每个修改先落为 "suggestion"，用户 approve 才真正写进文档。
 */
@Service
public class AiToolDefinition {

    private final ObjectMapper mapper = new ObjectMapper();

    // === 所有可以给 AI 用的编辑器工具 ===

    /**
     * 返回完整的 tool definitions（OpenAI function-calling 格式）
     */
    public List<Map<String, Object>> getToolDefinitions() {
        return List.of(
            defineAddComment(),
            defineInsertParagraph(),
            defineReplaceText(),
            defineSuggestChange(),
            defineApplyStyle(),
            defineInsertTable()
        );
    }

    private Map<String, Object> defineAddComment() {
        return toolDef("add_comment",
            "在文档指定段落或位置添加批注/评论",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "paragraphId", stringProp("段落 ID（paraId）"),
                    "text", stringProp("批注内容"),
                    "position", stringProp("批注位置: 'start' | 'end'")
                ),
                "required", List.of("paragraphId", "text")
            )
        );
    }

    private Map<String, Object> defineInsertParagraph() {
        return toolDef("insert_paragraph",
            "在指定位置插入新段落",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "afterParagraphId", stringProp("在此段落之后插入"),
                    "text", stringProp("新段落的内容")
                ),
                "required", List.of("afterParagraphId", "text")
            )
        );
    }

    private Map<String, Object> defineReplaceText() {
        return toolDef("replace_text",
            "替换指定段落的内容（注意：会触发建议模式，用户需 approve）",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "paragraphId", stringProp("目标段落 ID"),
                    "newText", stringProp("替换后的新文本"),
                    "reason", stringProp("替换原因")
                ),
                "required", List.of("paragraphId", "newText")
            )
        );
    }

    private Map<String, Object> defineSuggestChange() {
        return toolDef("suggest_change",
            "建议对文档进行修改（不会直接改，用户需 approve）",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "targetParagraphId", stringProp("目标段落 ID"),
                    "description", stringProp("修改说明"),
                    "proposedContent", stringProp("建议的内容")
                ),
                "required", List.of("description")
            )
        );
    }

    private Map<String, Object> defineApplyStyle() {
        return toolDef("apply_style",
            "对段落应用样式（标题、加粗、斜体等）",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "paragraphId", stringProp("目标段落 ID"),
                    "style", stringProp("样式名: 'heading1' | 'heading2' | 'bold' | 'italic' | 'quote'")
                ),
                "required", List.of("paragraphId", "style")
            )
        );
    }

    private Map<String, Object> defineInsertTable() {
        return toolDef("insert_table",
            "在指定位置插入表格",
            Map.of(
                "type", "object",
                "properties", Map.of(
                    "afterParagraphId", stringProp("在此段落之后插入"),
                    "rows", Map.of("type", "integer", "description", "行数"),
                    "cols", Map.of("type", "integer", "description", "列数"),
                    "headers", Map.of("type", "array", "items", Map.of("type", "string"),
                        "description", "表头列表")
                ),
                "required", List.of("afterParagraphId", "rows", "cols")
            )
        );
    }

    // --- 辅助方法 ---

    private Map<String, Object> toolDef(String name, String description, Object parameters) {
        return new LinkedHashMap<>(Map.of(
            "type", "function",
            "function", new LinkedHashMap<>(Map.of(
                "name", name,
                "description", description,
                "parameters", parameters
            ))
        ));
    }

    private Map<String, Object> stringProp(String description) {
        return Map.of("type", "string", "description", description);
    }
}
