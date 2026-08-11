package com.subtlesight.word.web.ai.provider;

import com.subtlesight.word.web.ai.gateway.ModelRouter;
import java.util.Map;
import java.util.concurrent.Flow;

/**
 * AI Provider 抽象接口 — 解耦具体模型厂商。
 */
public interface AiProvider {

    /** 同步调用（非流式） */
    ChatResponse chat(ChatRequest request);

    /** 流式调用（SSE 推送给前端） */
    Flow.Publisher<StreamingChunk> chatStream(ChatRequest request);

    /** 同步调用的便捷方法，直接返回文本 */
    default String chatSync(ChatRequest request) {
        return chat(request).content();
    }

    /** Provider 标识，映射到 ModelRouter.ModelSelection */
    ModelRouter.ModelSelection modelType();

    /** 活体检测 */
    boolean isAvailable();

    // ── 内嵌类型 ──

    record ChatRequest(
        String systemPrompt,
        String userMessage,
        double temperature,
        int maxTokens,
        String operation
    ) {
        public ChatRequest(String systemPrompt, String userMessage, double temperature, int maxTokens) {
            this(systemPrompt, userMessage, temperature, maxTokens, "");
        }
        public ChatRequest(String systemPrompt, String userMessage, double temperature, int maxTokens, String operation) {
            this.systemPrompt = systemPrompt;
            this.userMessage = userMessage;
            this.temperature = temperature;
            this.maxTokens = maxTokens;
            this.operation = operation != null ? operation : "";
        }
    }

    record ChatResponse(String content, int tokensUsed, long latencyMs) {}

    record StreamingChunk(String text, boolean done, int tokenCount) {}
}
