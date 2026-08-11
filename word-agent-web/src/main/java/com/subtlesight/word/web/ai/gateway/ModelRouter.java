package com.subtlesight.word.web.ai.gateway;

import com.subtlesight.word.web.ai.provider.AiProvider;
import org.springframework.stereotype.Component;
import java.util.Map;

/**
 * Model Router — 按操作类型分流到大/小模型。
 *
 * 续写(continue)       → DEEPSEEK（便宜快模型）
 * 润色/翻译/总结/问答   → OPENAI（强模型）
 * 语法校正              → DEEPSEEK（便宜够用）
 */
@Component
public class ModelRouter {

    public enum ModelSelection {
        DEEPSEEK, OPENAI
    }

    /**
     * 根据操作类型返回模型选择。
     */
    public ModelSelection route(String operation) {
        return switch (operation) {
            case "continue", "fix-grammar" -> ModelSelection.DEEPSEEK;
            case "polish", "translate", "summarize", "qa", "rewrite", "agent" -> ModelSelection.OPENAI;
            default -> ModelSelection.OPENAI;
        };
    }
}
