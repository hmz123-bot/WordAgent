package com.subtlesight.word.web.ai.controller;

import com.subtlesight.word.web.ai.gateway.AiGatewayService;
import com.subtlesight.word.web.ai.gateway.PromptTemplateService;
import com.subtlesight.word.web.ai.gateway.PromptTemplateService.PromptResult;
import com.subtlesight.word.web.ai.provider.AiProvider.*;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

/**
 * 流式 AI 控制器 V2 — 接收前端结构化 blocks，
 * 由 PromptTemplateService 做结构化截断后流式返回。
 */
@RestController
@RequestMapping("/api/v2/ai")
public class StreamingAiController {

    private static final Logger log = LoggerFactory.getLogger(StreamingAiController.class);

    private final PromptTemplateService promptService;
    private final AiGatewayService gateway;

    public StreamingAiController(PromptTemplateService promptService, AiGatewayService gateway) {
        this.promptService = promptService;
        this.gateway = gateway;
    }

    /**
     * SSE 流式调用。
     * 请求体包含 operation + blocks[] + selectedParaId + instruction 等。
     * blocks[] 中每条包含 paraId/text/type/headingLevel/charCount。
     */
    @PostMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public void stream(@RequestBody Map<String, Object> request, HttpServletResponse response) {
        String operation = request.getOrDefault("operation", "polish").toString();

        // 异步处理
        response.setContentType("text/event-stream");
        response.setCharacterEncoding("UTF-8");
        response.setHeader("Cache-Control", "no-cache");
        response.setHeader("Connection", "keep-alive");
        response.setHeader("X-Accel-Buffering", "no");

        PrintWriter writer;
        try {
            writer = response.getWriter();
        } catch (IOException e) {
            log.error("获取 SSE writer 失败", e);
            return;
        }

        try {
            // 1. 构建 prompt（内部做结构化截断）
            PromptResult prompt = promptService.build(operation, request);
            log.info("[SSE] op={} systemChars={} userChars={}",
                operation, prompt.systemPrompt().length(), prompt.userMessage().length());

            // 2. 路由模型并流式调用
            ChatRequest chatReq = new ChatRequest(
                prompt.systemPrompt(),
                prompt.userMessage(),
                prompt.temperature(),
                prompt.maxTokens(),
                operation
            );

            Flow.Publisher<StreamingChunk> publisher = gateway.chatStream(chatReq, operation);

            writer.write("event: start\n");
            writer.write("data: {\"operation\":\"" + escapeJson(operation) + "\"}\n\n");
            writer.flush();

            publisher.subscribe(new Flow.Subscriber<>() {
                private Flow.Subscription subscription;

                @Override
                public void onSubscribe(Flow.Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(Long.MAX_VALUE);
                }

                @Override
                public void onNext(StreamingChunk chunk) {
                    try {
                        if (chunk.done()) {
                            writer.write("event: done\n");
                            writer.write("data: {\"tokens\":" + chunk.tokenCount() + "}\n\n");
                        } else {
                            writer.write("event: token\n");
                            writer.write("data: " + jsonEscape(chunk.text()) + "\n\n");
                        }
                        writer.flush();
                    } catch (Exception e) {
                        onError(e);
                    }
                }

                @Override
                public void onError(Throwable throwable) {
                    try {
                        writer.write("event: error\n");
                        writer.write("data: " + jsonEscape(throwable.getMessage()) + "\n\n");
                        writer.flush();
                    } catch (Exception ignored) {}
                }

                @Override
                public void onComplete() {
                    try { writer.close(); } catch (Exception ignored) {}
                }
            });

        } catch (Exception e) {
            log.error("SSE 流处理异常", e);
            try {
                writer.write("event: error\n");
                writer.write("data: " + jsonEscape(e.getMessage()) + "\n\n");
                writer.flush();
                writer.close();
            } catch (Exception ignored) {}
        }
    }

    /**
     * 同步调用（非流式）。
     */
    @PostMapping("/sync")
    public Map<String, Object> sync(@RequestBody Map<String, Object> request) {
        String operation = request.getOrDefault("operation", "polish").toString();
        PromptResult prompt = promptService.build(operation, request);

        String response = gateway.chatSync(
            new ChatRequest(prompt.systemPrompt(), prompt.userMessage(),
                prompt.temperature(), prompt.maxTokens(), operation));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operation", operation);
        result.put("response", response);
        return result;
    }

    /**
     * 列出可用操作。
     */
    @GetMapping("/operations")
    public Map<String, Object> operations() {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operations", promptService.availableOperations());
        return result;
    }

    // ─────────── helper ───────────

    private String jsonEscape(String s) {
        if (s == null) return "\"\"";
        return "\"" + escapeJson(s) + "\"";
    }

    private String escapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }
}
