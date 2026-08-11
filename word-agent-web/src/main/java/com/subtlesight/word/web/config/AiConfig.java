package com.subtlesight.word.web.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI 编辑功能配置。
 */
@Configuration
@ConfigurationProperties(prefix = "word.agent.ai")
public class AiConfig {

    /** 是否启用 AI 功能 */
    private boolean enabled = true;

    /** LLM API 端点（兼容 OpenAI 格式） */
    private String apiEndpoint = "https://api.deepseek.com/v1";

    /** API Key */
    private String apiKey = "";

    /** 模型名称 */
    private String model = "deepseek-chat";

    /** 最大 token 数 */
    private int maxTokens = 16384;

    /** 温度参数 */
    private double temperature = 0.3;

    /** 请求超时（毫秒） */
    private long timeoutMs = 60000;

    public boolean isEnabled() { return enabled; }
    public void setEnabled(boolean enabled) { this.enabled = enabled; }

    public String getApiEndpoint() { return apiEndpoint; }
    public void setApiEndpoint(String apiEndpoint) { this.apiEndpoint = apiEndpoint; }

    public String getApiKey() { return apiKey; }
    public void setApiKey(String apiKey) { this.apiKey = apiKey; }

    public String getModel() { return model; }
    public void setModel(String model) { this.model = model; }

    public int getMaxTokens() { return maxTokens; }
    public void setMaxTokens(int maxTokens) { this.maxTokens = maxTokens; }

    public double getTemperature() { return temperature; }
    public void setTemperature(double temperature) { this.temperature = temperature; }

    public long getTimeoutMs() { return timeoutMs; }
    public void setTimeoutMs(long timeoutMs) { this.timeoutMs = timeoutMs; }
}