package com.subtlesight.word.web.service.ai;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.subtlesight.word.web.config.AiConfig;
import com.subtlesight.word.web.dto.response.AiEditResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * AI 编辑服务 — 调用 LLM API 处理文档编辑指令。
 */
@Service
public class AiService {

    private static final Logger log = LoggerFactory.getLogger(AiService.class);

    private final AiConfig config;
    private final ObjectMapper mapper;
    private final HttpClient httpClient;

    public AiService(AiConfig config, ObjectMapper mapper) {
        this.config = config;
        this.mapper = mapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    /**
     * 执行 AI 编辑。
     *
     * @param documentId  文档 ID
     * @param instruction 用户的编辑指令
     * @param context     文档上下文文本
     * @param nodeIds     限定节点 ID（可选）
     * @return AI 编辑响应
     */
    public AiEditResponse edit(String documentId, String instruction, String context, List<String> nodeIds) {
        if (!config.isEnabled()) {
            AiEditResponse resp = new AiEditResponse();
            resp.setSummary("AI 功能未启用");
            resp.setSuggestions(List.of());
            return resp;
        }

        String systemPrompt = buildSystemPrompt();
        String userPrompt = buildUserPrompt(instruction, context, nodeIds);
        String llmResponse = callLlmApi(systemPrompt, userPrompt);
        log.debug("LLM 原始响应 ({} chars): {}", llmResponse.length(), llmResponse);
        return parseResponse(llmResponse);
    }

    /**
     * 构建系统提示词。
     */
    private String buildSystemPrompt() {
        return """
                你是一个文档编辑助手，负责根据用户指令修改文档内容。
                文档由多个节点组成，每个节点有 nodeId、type（heading/paragraph/list/list_item/table 等）和 text 内容。
                
                你的任务：
                1. 理解用户的编辑指令
                2. 分析文档节点内容
                3. 返回 JSON 格式的修改建议
                
                返回格式（严格 JSON，不要包含 markdown 代码块标记）：
                {
                  "summary": "本次编辑的简要说明",
                  "suggestions": [
                    {
                      "nodeId": "节点 ID",
                      "originalText": "原始文本",
                      "suggestedText": "修改后的文本",
                      "description": "修改说明",
                      "operation": "replace_text"
                    }
                  ]
                }
                
                严格规则：
                - 只返回 JSON，不要包含任何其他文字
                - 如果不需要修改任何节点，返回空 suggestions 数组
                - nodeId 必须从文档上下文中精确复制，绝对不要自己编造
                - 最多返回 8 条建议，只返回最重要的修改
                - 保持文档结构不变，只修改文本内容
                - 每条的 suggestedText 控制在 200 字以内
                """;
    }

    /**
     * 构建用户提示词。
     */
    private String buildUserPrompt(String instruction, String context, List<String> nodeIds) {
        StringBuilder sb = new StringBuilder();
        sb.append("编辑指令：").append(instruction).append("\n\n");
        sb.append("文档内容（节点列表）：\n").append(context).append("\n\n");
        if (nodeIds != null && !nodeIds.isEmpty()) {
            sb.append("限定操作的节点 ID：").append(String.join(", ", nodeIds)).append("\n");
        }
        sb.append("请严格按照 JSON 格式返回修改建议。");
        return sb.toString();
    }

    /**
     * 调用 LLM API（兼容 OpenAI 格式）。
     */
    private String callLlmApi(String systemPrompt, String userPrompt) {
        try {
            ObjectNode requestBody = mapper.createObjectNode();
            requestBody.put("model", config.getModel());
            requestBody.put("max_tokens", config.getMaxTokens());
            requestBody.put("temperature", config.getTemperature());

            ArrayNode messages = requestBody.putArray("messages");
            ObjectNode sysMsg = messages.addObject();
            sysMsg.put("role", "system");
            sysMsg.put("content", systemPrompt);

            ObjectNode userMsg = messages.addObject();
            userMsg.put("role", "user");
            userMsg.put("content", userPrompt);

            String json = mapper.writeValueAsString(requestBody);

            HttpRequest httpRequest = HttpRequest.newBuilder()
                    .uri(URI.create(config.getApiEndpoint() + "/chat/completions"))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + config.getApiKey())
                    .timeout(Duration.ofMillis(config.getTimeoutMs()))
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();

            HttpResponse<String> httpResponse = httpClient.send(httpRequest, HttpResponse.BodyHandlers.ofString());

            if (httpResponse.statusCode() != 200) {
                log.error("LLM API 返回错误: status={}, body={}", httpResponse.statusCode(), httpResponse.body());
                throw new RuntimeException("AI 服务调用失败: HTTP " + httpResponse.statusCode());
            }

            JsonNode root = mapper.readTree(httpResponse.body());
            String content = root.path("choices").get(0).path("message").path("content").asText();
            return content;

        } catch (Exception e) {
            log.error("调用 LLM API 失败", e);
            throw new RuntimeException("AI 服务调用失败: " + e.getMessage(), e);
        }
    }

    /**
     * 解析 LLM 响应为 AiEditResponse。
     */
    private AiEditResponse parseResponse(String llmResponse) {
        AiEditResponse resp = new AiEditResponse();
        resp.setRawResponse(llmResponse);

        try {
            // 尝试提取 JSON（兼容 LLM 返回 markdown 代码块的情况）
            String json = extractJson(llmResponse);

            JsonNode root = mapper.readTree(json);
            resp.setSummary(root.path("summary").asText(""));

            List<AiEditResponse.Suggestion> suggestions = new ArrayList<>();
            JsonNode suggestionsNode = root.path("suggestions");
            if (suggestionsNode.isArray()) {
                for (JsonNode item : suggestionsNode) {
                    AiEditResponse.Suggestion suggestion = new AiEditResponse.Suggestion();
                    suggestion.setNodeId(item.path("nodeId").asText(""));
                    suggestion.setOriginalText(item.path("originalText").asText(""));
                    suggestion.setSuggestedText(item.path("suggestedText").asText(""));
                    suggestion.setDescription(item.path("description").asText(""));
                    suggestion.setOperation(item.path("operation").asText("replace_text"));
                    suggestions.add(suggestion);
                }
            }
            resp.setSuggestions(suggestions);

        } catch (JsonProcessingException e) {
            log.info("LLM JSON 被截断，自动修复中: {}", e.getMessage());
            try {
                // 尝试修复被 max_tokens 截断的 JSON
                String json = extractJson(llmResponse);
                String repaired = repairTruncatedJson(json);
                log.debug("修复后的 JSON: {}", repaired);

                JsonNode root = mapper.readTree(repaired);
                resp.setSummary(root.path("summary").asText("AI 响应被截断，已尽力恢复可解析的部分"));

                List<AiEditResponse.Suggestion> suggestions = new ArrayList<>();
                JsonNode suggestionsNode = root.path("suggestions");
                if (suggestionsNode.isArray()) {
                    for (JsonNode item : suggestionsNode) {
                        AiEditResponse.Suggestion suggestion = new AiEditResponse.Suggestion();
                        suggestion.setNodeId(item.path("nodeId").asText(""));
                        suggestion.setOriginalText(item.path("originalText").asText(""));
                        suggestion.setSuggestedText(item.path("suggestedText").asText(""));
                        suggestion.setDescription(item.path("description").asText("(截断)"));
                        suggestion.setOperation(item.path("operation").asText("replace_text"));
                        suggestions.add(suggestion);
                    }
                }
                resp.setSuggestions(suggestions);
            } catch (Exception ex) {
                log.error("修复截断 JSON 也失败", ex);
                resp.setSummary("AI 响应解析失败: " + e.getMessage());
                resp.setSuggestions(List.of());
            }
        }

        return resp;
    }

    /**
     * 从 LLM 响应中提取纯 JSON。
     */
    private String extractJson(String llmResponse) {
        String json = llmResponse;
        if (json.contains("```json")) {
            json = json.substring(json.indexOf("```json") + 7);
            int end = json.indexOf("```");
            if (end > 0) json = json.substring(0, end);
        } else if (json.contains("```")) {
            json = json.substring(json.indexOf("```") + 3);
            int end = json.lastIndexOf("```");
            if (end > 0) json = json.substring(0, end);
        }
        return json.trim();
    }

    /**
     * 修复被 max_tokens 截断的 JSON。
     *
     * 策略：去掉尾部不完整行 → 找最后一个完整 suggestion 对象 → 截断并闭合。
     */
    private String repairTruncatedJson(String json) {
        // Step 1: 去掉尾部不完整的最后一行
        json = trimIncompleteTail(json);

        // Step 2: 定位 suggestions 数组，找最后一个完整对象
        int suggStart = json.indexOf("\"suggestions\"");
        if (suggStart < 0) {
            return closeJson(json);
        }

        int arrayStart = json.indexOf('[', suggStart);
        if (arrayStart < 0) return closeJson(json);

        int lastCompleteObj = findLastCompleteObject(json, arrayStart + 1);

        if (lastCompleteObj > 0) {
            // 截断：只保留到最后一个完整对象
            json = json.substring(0, lastCompleteObj + 1);
        }

        // Step 3: 闭合 JSON（只补缺失的 ] }，不做重复截断）
        return closeJson(json);
    }

    /**
     * 去掉尾部不完整的行（被截断的字符串值所在行）。
     */
    private String trimIncompleteTail(String json) {
        int lastNewline = json.lastIndexOf('\n');
        if (lastNewline < 0) return json;

        String lastLine = json.substring(lastNewline).trim();

        // 完整行以这些字符结束
        if (lastLine.endsWith("}") || lastLine.endsWith("]") || lastLine.endsWith(",")
            || lastLine.endsWith("\"") || lastLine.isEmpty()) {
            return json;
        }

        // 不完整行：去掉它
        return json.substring(0, lastNewline);
    }

    /**
     * 在指定范围内找最后一个完整 JSON 对象的位置。
     */
    private int findLastCompleteObject(String json, int start) {
        int lastCompleteObj = -1;
        int braceDepth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);

            if (escaped) {
                escaped = false;
                continue;
            }
            if (c == '\\') {
                escaped = true;
                continue;
            }
            if (c == '"') {
                inString = !inString;
                continue;
            }
            if (inString) continue;

            if (c == '{') braceDepth++;
            else if (c == '}') {
                braceDepth--;
                if (braceDepth == 0) {
                    lastCompleteObj = i;
                }
            }
        }

        return lastCompleteObj;
    }

    /**
     * 尝试闭合未完成的 JSON（补全缺失的括号和引号）。
     */
    private String closeJson(String json) {
        StringBuilder sb = new StringBuilder(json);

        // 计算未闭合的括号和引号状态
        int braceDepth = 0;
        int bracketDepth = 0;
        boolean inString = false;
        boolean escaped = false;

        for (int i = 0; i < sb.length(); i++) {
            char c = sb.charAt(i);
            if (escaped) { escaped = false; continue; }
            if (c == '\\') { escaped = true; continue; }
            if (c == '"') { inString = !inString; continue; }
            if (inString) continue;

            if (c == '{') braceDepth++;
            else if (c == '}') braceDepth--;
            else if (c == '[') bracketDepth++;
            else if (c == ']') bracketDepth--;
        }

        // 如果还在字符串中，先闭合字符串（添加转义引号模拟自然结束）
        if (inString) {
            sb.append("…\"");  // 截断标记 + 闭合引号
        }

        // 补全缺失的 ]
        for (int i = 0; i < bracketDepth; i++) {
            sb.append(']');
        }
        // 补全缺失的 }
        for (int i = 0; i < braceDepth; i++) {
            sb.append('}');
        }

        return sb.toString();
    }
}