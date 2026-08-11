package com.subtlesight.word.web.ai.spell;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 双层纠错服务。
 *
 * Layer 1（本地）：规则 + 词表匹配，零延迟
 * Layer 2（后端 LLM）：深度语法+逻辑纠错
 *
 * 当前 Layer 1 为简易规则实现，生产可替换为 WASM 加载的 BERT 模型。
 */
@Service
public class SpellCheckService {

    private static final Logger log = LoggerFactory.getLogger(SpellCheckService.class);

    // 常见拼写错误映射（简化词表）
    private static final Map<String, String> TYPO_CORRECTIONS = new HashMap<>();
    static {
        TYPO_CORRECTIONS.put("teh", "the");
        TYPO_CORRECTIONS.put("recieve", "receive");
        TYPO_CORRECTIONS.put("adn", "and");
        TYPO_CORRECTIONS.put("thier", "their");
        TYPO_CORRECTIONS.put("alot", "a lot");
        TYPO_CORRECTIONS.put("your welcome", "you're welcome");
        TYPO_CORRECTIONS.put("its a", "it's a");
    }

    // 中文常见错误
    private static final Map<String, String> CN_CORRECTIONS = new HashMap<>();
    static {
        CN_CORRECTIONS.put("的地得", "地"); // 简化标记
        CN_CORRECTIONS.put("再在", "在");
        CN_CORRECTIONS.put("已以", "以");
    }

    /**
     * Layer 1：本地快速纠错（WASM BERT 的替代品）
     */
    public List<SpellSuggestion> quickCheck(String text, String language) {
        return checkWithMap(text, "zh".equals(language) ? CN_CORRECTIONS : TYPO_CORRECTIONS);
    }

    private List<SpellSuggestion> checkWithMap(String text, Map<String, String> corrections) {
        List<SpellSuggestion> suggestions = new ArrayList<>();
        String lower = text.toLowerCase();
        for (var entry : corrections.entrySet()) {
            if (lower.contains(entry.getKey())) {
                int start = lower.indexOf(entry.getKey());
                suggestions.add(new SpellSuggestion(
                    text.substring(start, start + entry.getKey().length()),
                    entry.getValue(),
                    start,
                    start + entry.getKey().length(),
                    "spelling"
                ));
            }
        }
        return suggestions;
    }

    /**
     * Layer 2 调 AI Gateway 做深度纠错（异步，不阻塞 UI）
     */
    public String buildDeepCheckPrompt(String text) {
        return text; // 实际调用由 Gateway 层的 fix-grammar 操作处理
    }

    // --- 类型 ---

    public record SpellSuggestion(
        String original,
        String suggestion,
        int startIndex,
        int endIndex,
        String type // spelling / grammar / style
    ) {}
}
