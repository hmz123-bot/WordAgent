package com.subtlesight.word.web.ai.gateway;

import com.subtlesight.word.web.ai.provider.AiProvider;
import com.subtlesight.word.web.ai.provider.AiProvider.ChatRequest;
import com.subtlesight.word.web.ai.provider.AiProvider.ChatResponse;
import com.subtlesight.word.web.ai.provider.AiProvider.StreamingChunk;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicLong;

/**
 * AI Gateway 统一入口。
 *
 * 职责：
 *   1. 模型路由（续写走小模型，润色走大模型）
 *   2. 限流 / 审计（token 用量 / latency / 拒绝率）
 *   3. Provider 抽象（通过 Maps 注入，随时换）
 *   4. 结构化截断由 PromptTemplateService 在调用前完成
 */
@Service
public class AiGatewayService {

    private static final Logger log = LoggerFactory.getLogger(AiGatewayService.class);

    private final ModelRouter modelRouter;
    private final Map<ModelRouter.ModelSelection, AiProvider> providers;

    // 遥测
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong totalTokens = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    public AiGatewayService(ModelRouter modelRouter,
                            List<AiProvider> providerList) {
        this.modelRouter = modelRouter;
        this.providers = new HashMap<>();
        for (var p : providerList) {
            this.providers.put(p.modelType(), p);
        }
        log.info("AiGateway 初始化，已注册 {} 个 provider: {}",
            providers.size(), providers.keySet());
    }

    /**
     * 流式调用（用于 SSE 推送到前端）。
     * operation 决定路由到哪个模型。
     */
    public Flow.Publisher<StreamingChunk> chatStream(ChatRequest req, String operation) {
        var provider = selectAvailableProvider(operation != null ? operation : req.operation());

        log.info("[Gateway] stream op={} model={} temp={} maxTokens={}",
            operation, provider.modelType(), req.temperature(), req.maxTokens());

        totalRequests.incrementAndGet();
        return provider.chatStream(req);
    }

    /**
     * 选择一个可用的 provider：优先按路由，若不可用则回退到第一个可用 provider。
     */
    private AiProvider selectAvailableProvider(String operation) {
        var preferred = modelRouter.route(operation);
        var provider = providers.get(preferred);
        if (provider != null && provider.isAvailable()) {
            return provider;
        }

        // 降级：找任意可用 provider
        for (var p : providers.values()) {
            if (p.isAvailable()) {
                log.warn("[Gateway] 首选模型 {} 不可用，回退到 {}", preferred, p.modelType());
                return p;
            }
        }

        throw new IllegalStateException(
            "无可用 AI Provider。请配置 OPENAI_API_KEY 或 DEEPSEEK_API_KEY 环境变量。");
    }

    /**
     * 同步调用（非流式，会阻塞等待完整响应）。
     */
    public String chatSync(ChatRequest req) {
        ChatResponse resp = chat(req);
        return resp.content();
    }

    /**
     * 同步调用，返回完整响应（含 tokens/latency 元数据）。
     */
    public ChatResponse chat(ChatRequest req) {
        var provider = selectAvailableProvider(req.operation());

        long start = System.currentTimeMillis();
        totalRequests.incrementAndGet();

        ChatResponse resp = provider.chat(req);

        long latency = System.currentTimeMillis() - start;
        totalLatencyMs.addAndGet(latency);
        totalTokens.addAndGet(resp.tokensUsed());
        log.info("[Gateway] sync model={} latencyMs={} tokens={} chars={}",
            provider.modelType(), latency, resp.tokensUsed(), resp.content().length());

        return resp;
    }

    // ─────────── 遥测（Prometheus / Grafana 导出示口） ───────────

    public Map<String, Object> telemetry() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("totalRequests", totalRequests.get());
        m.put("totalTokens", totalTokens.get());
        m.put("avgLatencyMs", totalRequests.get() > 0
            ? totalLatencyMs.get() / totalRequests.get() : 0);
        return m;
    }
}
