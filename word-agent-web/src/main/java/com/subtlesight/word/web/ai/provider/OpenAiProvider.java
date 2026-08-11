package com.subtlesight.word.web.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.subtlesight.word.web.ai.gateway.ModelRouter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Flow;
import java.util.concurrent.SubmissionPublisher;

@Component
public class OpenAiProvider implements AiProvider {

    private static final Logger log = LoggerFactory.getLogger(OpenAiProvider.class);
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(30)).build();
    private final ObjectMapper mapper = new ObjectMapper();

    @Value("${ai.openai.api-key:}") private String apiKey;
    @Value("${ai.openai.endpoint:https://api.openai.com/v1/chat/completions}") private String endpoint;
    @Value("${ai.openai.model:gpt-4o-mini}") private String model;

    @Override
    public ChatResponse chat(ChatRequest req) {
        long start = System.currentTimeMillis();
        try {
            var body = buildRequestBody(req, false);
            var httpReq = HttpRequest.newBuilder()
                .uri(URI.create(endpoint))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + apiKey)
                .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                .timeout(Duration.ofSeconds(120))
                .build();
            var resp = http.send(httpReq, HttpResponse.BodyHandlers.ofString());
            var root = mapper.readTree(resp.body());
            var content = root.path("choices").get(0).path("message").path("content").asText();
            int tokens = root.path("usage").path("total_tokens").asInt();
            long latency = System.currentTimeMillis() - start;
            return new ChatResponse(content, tokens, latency);
        } catch (Exception e) {
            log.error("OpenAI sync call failed", e);
            throw new RuntimeException("AI 服务暂不可用: " + e.getMessage(), e);
        }
    }

    @Override
    public Flow.Publisher<StreamingChunk> chatStream(ChatRequest req) {
        var publisher = new SubmissionPublisher<StreamingChunk>();
        new Thread(() -> {
            try {
                var body = buildRequestBody(req, true);
                var httpReq = HttpRequest.newBuilder()
                    .uri(URI.create(endpoint))
                    .header("Content-Type", "application/json")
                    .header("Authorization", "Bearer " + apiKey)
                    .POST(HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(body)))
                    .timeout(Duration.ofSeconds(120))
                    .build();
                var resp = http.send(httpReq, HttpResponse.BodyHandlers.ofLines());
                int tokensSoFar = 0;
                for (String line : resp.body().toList()) {
                    if (line.startsWith("data: ")) {
                        String data = line.substring(6).trim();
                        if ("[DONE]".equals(data)) break;
                        JsonNode chunk = mapper.readTree(data);
                        var delta = chunk.path("choices").get(0).path("delta").path("content");
                        if (!delta.isMissingNode()) {
                            String text = delta.asText();
                            tokensSoFar++;
                            publisher.submit(new StreamingChunk(text, false, tokensSoFar));
                        }
                    }
                }
                publisher.submit(new StreamingChunk("", true, tokensSoFar));
            } catch (Exception e) {
                log.error("OpenAI stream failed", e);
                publisher.closeExceptionally(e);
                return;
            }
            publisher.close();
        }).start();
        return publisher;
    }

    @Override public ModelRouter.ModelSelection modelType() { return ModelRouter.ModelSelection.OPENAI; }
    @Override public boolean isAvailable() { return apiKey != null && !apiKey.isBlank(); }

    private Map<String, Object> buildRequestBody(ChatRequest req, boolean stream) {
        var messages = List.of(
            Map.of("role", "system", "content", req.systemPrompt()),
            Map.of("role", "user", "content", req.userMessage())
        );
        var body = new java.util.LinkedHashMap<String, Object>();
        body.put("model", model);
        body.put("messages", messages);
        body.put("temperature", req.temperature());
        body.put("max_tokens", req.maxTokens());
        body.put("stream", stream);
        return body;
    }
}
