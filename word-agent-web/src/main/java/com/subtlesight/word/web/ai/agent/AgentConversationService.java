package com.subtlesight.word.web.ai.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.word.web.ai.gateway.AiGatewayService;
import com.subtlesight.word.web.ai.provider.AiProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Agent 多轮对话服务 — 让 AI 能主动操作文档（加批注、插段落、改样式）。
 *
 * 工作流：
 * 1. 用户消息 + tool definitions → Gateway → LLM
 * 2. LLM 决定调 tool → tool call 转到前端 useDocxAgentTools 在编辑器执行
 * 3. 执行结果回传 → 下一轮对话
 * 4. 所有修改先落为 suggestion，用户 approve 才进文档
 */
@Service
public class AgentConversationService {

    private static final Logger log = LoggerFactory.getLogger(AgentConversationService.class);
    private final AiGatewayService gateway;
    private final AiToolDefinition toolDefs;
    private final ObjectMapper mapper = new ObjectMapper();

    // 临时会话存储（生产应放 Redis）
    private final Map<String, List<Map<String, Object>>> conversations = new ConcurrentHashMap<>();

    public AgentConversationService(AiGatewayService gateway, AiToolDefinition toolDefs) {
        this.gateway = gateway;
        this.toolDefs = toolDefs;
    }

    /**
     * 开启新的 Agent 对话，发送首轮消息
     */
    public AgentMessage sendMessage(String conversationId, String userMessage, Map<String, Object> documentContext) {
        var history = conversations.computeIfAbsent(conversationId, k -> new ArrayList<>());

        // 构建包含 tool definitions 的 system prompt
        String systemPrompt = buildAgentSystemPrompt(documentContext);
        String userPrompt = userMessage + "\n\n你可以使用提供的 tools 来操作文档。如需修改文档，先创建 suggestion。";

        var request = new AiProvider.ChatRequest(
            systemPrompt,
            userPrompt,
            0.7,
            4096,
            "agent"
        );

        var resp = gateway.chat(request);

        // 解析 tool calls（简化版，生产应完整解析 OpenAI function-calling 响应）
        List<Map<String, Object>> toolCalls = parseToolCalls(resp.content());

        history.add(Map.of("role", "user", "content", userMessage));
        history.add(Map.of("role", "assistant", "content", resp.content(), "toolCalls", toolCalls));

        return new AgentMessage(
            resp.content(),
            toolCalls,
            resp.tokensUsed(),
            resp.latencyMs()
        );
    }

    /**
     * 多轮：将 tool 执行结果送回，继续对话
     */
    public AgentMessage continueConversation(String conversationId, List<Map<String, Object>> toolResults,
                                              Map<String, Object> documentContext) {
        var history = conversations.get(conversationId);
        if (history == null) throw new IllegalArgumentException("会话不存在: " + conversationId);

        for (var result : toolResults) {
            history.add(Map.of("role", "tool", "toolCallId", result.getOrDefault("id", ""), "content", result));
        }

        String systemPrompt = buildAgentSystemPrompt(documentContext);
        // 拼接历史为 user message（简化实现）
        StringBuilder historyText = new StringBuilder();
        history.forEach(msg -> {
            historyText.append(msg.get("role")).append(": ").append(msg.get("content")).append("\n---\n");
        });

        var resp = gateway.chat(new AiProvider.ChatRequest(
            systemPrompt,
            "对话历史：\n" + historyText + "\n\n请根据 tool 执行结果继续：",
            0.7,
            4096,
            "agent"
        ));

        List<Map<String, Object>> toolCalls = parseToolCalls(resp.content());
        history.add(Map.of("role", "assistant", "content", resp.content(), "toolCalls", toolCalls));

        return new AgentMessage(resp.content(), toolCalls, resp.tokensUsed(), resp.latencyMs());
    }

    private String buildAgentSystemPrompt(Map<String, Object> documentContext) {
        return """
            你是 Word Agent，一个能主动操作文档的 AI 助手。你可以：
            1. add_comment — 在段落上添加批注
            2. insert_paragraph — 在指定位置插入段落
            3. replace_text — 替换段落文本（会先创建建议）
            4. suggest_change — 建议修改
            5. apply_style — 应用样式（标题/加粗/引用）
            6. insert_table — 插入表格
            
            ** 关键规则 **：
            - 每次对文档的修改必须先创建 suggestion（建议），不要直接修改
            - 使用 paragraphId 精确定位段落
            - 回答要简洁专业，不要添加多余的说明文字
            - 需要改文档时，描述你的计划然后调用对应的 tool
            
            文档内容摘要：
            """ + documentContext.getOrDefault("docSummary", "未提供");
    }

    private List<Map<String, Object>> parseToolCalls(String content) {
        // 简化版：从文本中识别 tool call 意图
        // 生产环境应解析 OpenAI 的 function_call 结构化响应
        List<Map<String, Object>> calls = new ArrayList<>();
        if (content.contains("add_comment")) {
            calls.add(Map.of("tool", "add_comment", "intent", "添加批注"));
        }
        if (content.contains("insert_paragraph")) {
            calls.add(Map.of("tool", "insert_paragraph", "intent", "插入段落"));
        }
        if (content.contains("suggest_change") || content.contains("建议修改")) {
            calls.add(Map.of("tool", "suggest_change", "intent", "建议修改"));
        }
        if (content.contains("apply_style")) {
            calls.add(Map.of("tool", "apply_style", "intent", "应用样式"));
        }
        return calls;
    }

    // --- 消息类型 ---
    public record AgentMessage(
        String content,
        List<Map<String, Object>> toolCalls,
        int tokensUsed,
        long latencyMs
    ) {}

    /** 获取可用 tool 定义（前端使用） */
    public List<Map<String, Object>> getTools() {
        return toolDefs.getToolDefinitions();
    }
}
