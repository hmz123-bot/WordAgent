package com.subtlesight.word.web.ai.agent;

import com.subtlesight.word.web.ai.agent.AgentConversationService.AgentMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Agent 模式控制器 — 侧边聊天面板让 AI 主动操作文档。
 * 类似 Word Copilot 侧边面板的体验。
 */
@RestController
@RequestMapping("/api/v2/agent")
public class AiAgentController {

    private static final Logger log = LoggerFactory.getLogger(AiAgentController.class);

    private final AgentConversationService agentService;

    public AiAgentController(AgentConversationService agentService) {
        this.agentService = agentService;
    }

    /**
     * 发起 Agent 对话
     */
    @PostMapping("/conversation")
    public Map<String, Object> startConversation(@RequestBody Map<String, Object> payload) {
        String conversationId = UUID.randomUUID().toString().substring(0, 8);
        String message = (String) payload.getOrDefault("message", "");
        @SuppressWarnings("unchecked")
        Map<String, Object> docContext = (Map<String, Object>) payload.getOrDefault("documentContext", Map.of());

        try {
            AgentMessage resp = agentService.sendMessage(conversationId, message, docContext);

            return Map.of(
                "conversationId", conversationId,
                "content", resp.content(),
                "toolCalls", resp.toolCalls(),
                "tokensUsed", resp.tokensUsed(),
                "latencyMs", resp.latencyMs()
            );
        } catch (IllegalStateException e) {
            log.error("Agent 对话失败 — Provider 不可用", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("Agent 对话失败", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Agent 请求处理失败: " + e.getMessage());
        }
    }

    /**
     * 继续对话（tool 执行结果回传）
     */
    @PostMapping("/conversation/{id}/continue")
    public Map<String, Object> continueConversation(@PathVariable String id,
                                                     @RequestBody Map<String, Object> payload) {
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> toolResults = (List<Map<String, Object>>) payload.getOrDefault("toolResults", List.of());
        @SuppressWarnings("unchecked")
        Map<String, Object> docContext = (Map<String, Object>) payload.getOrDefault("documentContext", Map.of());

        try {
            AgentMessage resp = agentService.continueConversation(id, toolResults, docContext);

            return Map.of(
                "content", resp.content(),
                "toolCalls", resp.toolCalls(),
                "tokensUsed", resp.tokensUsed()
            );
        } catch (IllegalStateException e) {
            log.error("Agent 继续对话失败 — Provider 不可用", e);
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, e.getMessage());
        } catch (Exception e) {
            log.error("Agent 继续对话失败", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Agent 请求处理失败: " + e.getMessage());
        }
    }

    /**
     * 获取可用 tool 定义
     */
    @GetMapping("/tools")
    public List<Map<String, Object>> getTools() {
        return agentService.getTools();
    }
}
