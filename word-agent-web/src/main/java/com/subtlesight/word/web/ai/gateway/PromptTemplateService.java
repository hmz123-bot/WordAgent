package com.subtlesight.word.web.ai.gateway;

import com.subtlesight.word.web.ai.rag.RagService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Prompt 模板化 + 结构化截断服务。
 *
 * 每种操作有独立的 system/user prompt 骨架。
 * 根据操作类型自动选择截断模式：
 *   polish/translate/rewrite/fix-grammar → SELECTION_CONTEXT（选区 ± 各 3 段）
 *   continue → SELECTION_CONTEXT（选区末尾 ± 各 2 段）
 *   summarize/qa → CHAPTER_BOUNDARY（标题树 + 各章节首段）
 *                 或 RAG_RETRIEVAL（全文任务走向量检索）
 */
@Service
public class PromptTemplateService {

    private static final Logger log = LoggerFactory.getLogger(PromptTemplateService.class);

    private final ContextTruncator truncator;
    private final RagService ragService;

    public PromptTemplateService(ContextTruncator truncator, RagService ragService) {
        this.truncator = truncator;
        this.ragService = ragService;
    }

    // ─────────── 操作 → 截断策略 ───────────

    /**
     * 操作分类：inline（选区上下文）、full-doc（全文/RAG）
     */
    record OpStrategy(TruncationType type, int surroundParagraphs, int maxContextChars) {}

    enum TruncationType { SELECTION_CONTEXT, CHAPTER_HEADING, RAG_TOP_K }

    private static final Map<String, OpStrategy> STRATEGIES = Map.of(
        "polish",       new OpStrategy(TruncationType.SELECTION_CONTEXT, 3, 3000),
        "continue",     new OpStrategy(TruncationType.SELECTION_CONTEXT, 2, 2000),
        "translate",    new OpStrategy(TruncationType.SELECTION_CONTEXT, 2, 4000),
        "fix-grammar",  new OpStrategy(TruncationType.SELECTION_CONTEXT, 1, 2000),
        "rewrite",      new OpStrategy(TruncationType.SELECTION_CONTEXT, 3, 3000),
        "summarize",    new OpStrategy(TruncationType.RAG_TOP_K, 0, 4000),
        "qa",           new OpStrategy(TruncationType.RAG_TOP_K, 0, 4000)
    );

    // ─────────── Prompt 模板 ───────────

    private static final Map<String, PromptTemplate> TEMPLATES = Map.of(
        "polish", new PromptTemplate("""
            你是一个专业的文档润色助手。对用户提供的文本进行润色，使其更流畅、专业、准确。
            
            输出规则（极其重要）：
            1. 只返回润色后的纯文本
            2. 保持原文本的段落结构
            3. 不要添加"润色后："、"修改建议："之类的前缀
            4. 如果原文没问题，原样返回
            """,
            "请润色以下文本：\n\n---\n{originalText}\n---",
            0.3, 2048
        ),
        "continue", new PromptTemplate("""
            你是一个专业写作续写助手。根据上下文自然地续写。
            
            输出规则（极其重要）：
            1. 只返回续写的纯文本，不带前缀或标记
            2. 风格与原文保持一致
            3. 续写内容直接接在原文末尾即可
            """,
            "上文：\n---\n{surroundingText}\n---\n\n请从最后一句话自然续写（不要重复上文内容）：",
            0.7, 1024
        ),
        "translate", new PromptTemplate("""
            你是一个专业翻译助手，将用户文本翻译为目标语言。
            
            输出规则（极其重要）：
            1. 只返回翻译后的纯文本
            2. 保持原文的段落结构
            3. 不要添加解释或注释
            """,
            "请将以下文本翻译成中文：\n\n---\n{originalText}\n---",
            0.2, 4096
        ),
        "summarize", new PromptTemplate("""
            你是一个专业文档总结助手。根据提供的文档片段总结核心要点。
            
            输出规则（极其重要）：
            1. 返回 HTML 格式总结，可使用 <p><ul><li><strong> 标签
            2. 按章节/主题分点总结
            3. 不要用 markdown
            4. 不要添加"总结："前缀
            """,
            "文档结构及内容摘录（已按章节边界裁剪）：\n\n{structuredContext}",
            0.3, 2048
        ),
        "rewrite", new PromptTemplate("""
            你是一个专业写作助手。根据用户指令重写文本。
            
            输出规则（极其重要）：
            1. 只返回重写后的纯文本
            2. 严格遵循用户的改写要求
            3. 不要添加解释或说明
            """,
            "改写要求：{instruction}\n\n原文：\n---\n{originalText}\n---",
            0.5, 2048
        ),
        "fix-grammar", new PromptTemplate("""
            你是一个语法校正助手。只修正语法、拼写和标点错误，不动文风和表达。
            
            输出规则：
            1. 返回 JSON 数组：[{ "original": "错误片段", "corrected": "修正后" }]
            2. 如果没有错误，返回 []
            """,
            "检查以下文本的语法错误，返回 JSON 格式修正列表：\n\n---\n{originalText}\n---",
            0.1, 2048
        ),
        "qa", new PromptTemplate("""
            你是一个文档问答助手。根据文档内容回答问题。
            
            输出规则（极其重要）：
            1. 用 HTML 格式回复（<p><ul><li><strong>）
            2. 如果答案不在文档中，明确说明"文档中未涉及该内容"
            3. 不要用 markdown
            """,
            "文档内容摘录（已按相关性检索）：\n\n{structuredContext}\n\n用户问题：{question}",
            0.3, 2048
        )
    );

    // ─────────── 公开 API ───────────

    /**
     * 根据操作类型和前端传来的结构化上下文构建 prompt。
     * context 中包含 blocks（List<Map>）、selectedParaId、instruction 等。
     */
    public PromptResult build(String operation, Map<String, Object> context) {
        var tpl = TEMPLATES.getOrDefault(operation, TEMPLATES.get("polish"));
        var strategy = STRATEGIES.getOrDefault(operation,
            new OpStrategy(TruncationType.SELECTION_CONTEXT, 2, 2000));

        // 1. 从前端 JSON 反序列化 DocumentBlock 列表
        List<ContextTruncator.DocumentBlock> blocks = deserializeBlocks(context);

        Map<String, String> variables = new HashMap<>(extractRawVariables(context));

        // 2. 结构化截断
        if (!blocks.isEmpty() && strategy.type() == TruncationType.SELECTION_CONTEXT) {
            applySelectionTruncation(blocks, context, strategy, variables);
        } else if (!blocks.isEmpty() && strategy.type() == TruncationType.RAG_TOP_K) {
            applyRagTruncation(blocks, context, strategy, operation, variables);
        } else if (!blocks.isEmpty()) {
            // CHAPTER_HEADING fallback
            var result = truncator.chapterBoundary(blocks, strategy.maxContextChars());
            variables.put("structuredContext", result.context());
            variables.put("fullDoc", result.context());
            log.info("operation={} truncated: {}/{} chars kept, cut at: {}",
                operation, result.keptChars(), result.totalChars(), result.cutPoint());
        }

        // 3. 插值
        String userMessage = interpolate(tpl.userTemplate(), variables);
        return new PromptResult(tpl.systemPrompt(), userMessage, tpl.temperature(), tpl.maxTokens());
    }

    /**
     * 暴露给前端：列出所有可用操作
     */
    public Map<String, String> availableOperations() {
        return Map.of(
            "polish", "润色改写",
            "continue", "智能续写",
            "translate", "翻译",
            "summarize", "文档总结",
            "rewrite", "按指令重写",
            "fix-grammar", "语法校正",
            "qa", "文档问答"
        );
    }

    // ─────────── 截断逻辑 ───────────

    /**
     * 选区模式：selection ± N 段落
     */
    private void applySelectionTruncation(
            List<ContextTruncator.DocumentBlock> blocks,
            Map<String, Object> context,
            OpStrategy strategy,
            Map<String, String> variables) {

        // 从 context 取 selection 文本（兼容旧格式）
        String originalText = variables.getOrDefault("originalText", "");
        String selectedParaId = context.getOrDefault("selectedParaId", "").toString();

        if (blocks.isEmpty()) {
            variables.putIfAbsent("surroundingText", originalText);
            return;
        }

        String effectiveParaId = selectedParaId.isEmpty()
            ? blocks.get(0).paraId()
            : selectedParaId;

        var result = truncator.selectionContext(
            blocks, effectiveParaId, strategy.surroundParagraphs(), strategy.maxContextChars());

        // 填充模板变量
        if (!result.context().isEmpty()) {
            variables.put("surroundingText", result.context());
        }
        // 确保 originalText 存在（取选区段落文本）
        variables.putIfAbsent("originalText", variables.getOrDefault("surroundingText", ""));
        // 结构化结果
        variables.put("structuredContext", result.context());

        log.info("selection-truncation paraId={} surround={} kept={}/{}-chars excluded={}",
            effectiveParaId, strategy.surroundParagraphs(),
            result.keptChars(), result.totalChars(), result.excludedParaIds().size());
    }

    /**
     * RAG 模式：全文检索 top-K 段落
     */
    private void applyRagTruncation(
            List<ContextTruncator.DocumentBlock> blocks,
            Map<String, Object> context,
            OpStrategy strategy,
            String operation,
            Map<String, String> variables) {

        String query = variables.getOrDefault("question",
            variables.getOrDefault("instruction",
                variables.getOrDefault("originalText", "")));
        if (query.isBlank()) {
            // 连 query 都没有，退化为章节截断
            var result = truncator.chapterBoundary(blocks, strategy.maxContextChars());
            variables.put("structuredContext", result.context());
            variables.put("fullDoc", result.context());
            return;
        }

        // 为全文任务，先把段落索引入 RAG
        indexBlocksIfNeeded(blocks);

        // RAG 检索
        var ragChunks = ragService.retrieve(query, 8);
        if (ragChunks.isEmpty()) {
            var result = truncator.chapterBoundary(blocks, strategy.maxContextChars());
            variables.put("structuredContext", result.context());
            variables.put("fullDoc", result.context());
            return;
        }

        // 映射为 ContextTruncator.RagRetrievedChunk
        List<ContextTruncator.RagRetrievedChunk> retrieved = ragChunks.stream()
            .map(c -> {
                // 从内容中提取 paraId（RagService 存的 content 前 500 字符）
                String paraId = extractParaIdFromChunk(c.content(), blocks);
                return new ContextTruncator.RagRetrievedChunk(paraId, c.content(), c.score());
            })
            .filter(r -> !r.paraId().isBlank())
            .toList();

        var result = truncator.ragRetrieval(blocks, retrieved, strategy.maxContextChars());
        variables.put("structuredContext", result.context());
        variables.put("fullDoc", result.context());

        log.info("rag-truncation op={} topK={} kept={}/{}-chars excluded={}",
            operation, ragChunks.size(), result.keptChars(), result.totalChars(),
            result.excludedParaIds().size());
    }

    // ─────────── 辅助方法 ───────────

    @SuppressWarnings("unchecked")
    private List<ContextTruncator.DocumentBlock> deserializeBlocks(Map<String, Object> context) {
        Object blocksObj = context.get("blocks");
        if (!(blocksObj instanceof List<?> rawList)) return List.of();

        List<ContextTruncator.DocumentBlock> blocks = new ArrayList<>();
        int pos = 0;
        for (var item : rawList) {
            if (item instanceof Map<?, ?> m) {
                Map<String, Object> map = (Map<String, Object>) m;
                String paraId = map.getOrDefault("paraId", "p_" + pos).toString();
                String text = map.getOrDefault("text", "").toString();
                String type = map.getOrDefault("type", "paragraph").toString();
                int headingLevel = 0;
                Object hl = map.get("headingLevel");
                if (hl instanceof Number) headingLevel = ((Number) hl).intValue();
                blocks.add(new ContextTruncator.DocumentBlock(
                    paraId, text, type, headingLevel, text.length(), pos));
                pos++;
            }
        }
        return blocks;
    }

    private Map<String, String> extractRawVariables(Map<String, Object> context) {
        Map<String, String> vars = new HashMap<>();
        for (var entry : context.entrySet()) {
            if (!"blocks".equals(entry.getKey()) && entry.getValue() != null) {
                vars.put(entry.getKey(), entry.getValue().toString());
            }
        }
        return vars;
    }

    private String interpolate(String template, Map<String, String> ctx) {
        String result = template;
        for (var entry : ctx.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }
        return result;
    }

    private String extractParaIdFromChunk(String chunkContent, List<ContextTruncator.DocumentBlock> blocks) {
        // 用文本前缀匹配找到对应的 paraId
        String normalized = chunkContent.trim().substring(0, Math.min(100, chunkContent.trim().length()));
        for (var b : blocks) {
            if (b.text().trim().startsWith(normalized.substring(0, Math.min(50, normalized.length())))) {
                return b.paraId();
            }
        }
        // fallback：第一个段落
        return blocks.isEmpty() ? "" : blocks.get(0).paraId();
    }

    private void indexBlocksIfNeeded(List<ContextTruncator.DocumentBlock> blocks) {
        // RagService 是内存存储，幂等索引即可
        for (var b : blocks) {
            ragService.indexDocument(b.paraId(), b.text(), Map.of("type", b.type()));
        }
    }

    // ─────────── 内嵌类型 ───────────

    record PromptTemplate(String systemPrompt, String userTemplate, double temperature, int maxTokens) {}

    public record PromptResult(String systemPrompt, String userMessage, double temperature, int maxTokens) {}
}
