package com.subtlesight.word.web.ai.gateway;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 结构化上下文截断 — 不做 token 盲砍，按文档语义单元切。
 *
 * 三种截断模式：
 *   SELECTION_CONTEXT  → 用户选区 ± 前后各 N 段（段落边界对齐）
 *   CHAPTER_BOUNDARY   → 按标题层级 / 章节边界切，优先保留 H1/H2 结构
 *   RAG_RETRIEVAL       → 全文任务走段落向量索引 retrieve(top-k) 拼 context
 */
@Service
public class ContextTruncator {

    private static final Logger log = LoggerFactory.getLogger(ContextTruncator.class);

    /** 估算：中文约 1 char ≈ 1 token，英文约 4 char ≈ 1 token，取保守值 */
    private static final int CHARS_PER_TOKEN_ESTIMATE = 2;

    public enum TruncationMode {
        SELECTION_CONTEXT,   // 选区 ± N 段落
        CHAPTER_BOUNDARY,    // 按标题层级 / 章节边界切
        RAG_RETRIEVAL        // 全文任务用段落向量检索代替全塞
    }

    /**
     * 文档结构块 — 从前端 contextCollector 传来，已带 paraId + 标题层级
     */
    public record DocumentBlock(
        String paraId,           // 段落唯一 ID
        String text,             // 段落纯文本
        String type,             // heading / paragraph / list_item / table
        int headingLevel,        // 标题层级 1-6，非标题为 0
        int charCount,           // 字符数
        int position             // 在文档中的序号
    ) {
        public boolean isHeading() { return "heading".equals(type) && headingLevel > 0; }
    }

    /**
     * 截断结果
     */
    public record TruncationResult(
        String context,                    // 截断后的文本
        List<String> includedParaIds,       // 被包含的 paragraph ID
        List<String> excludedParaIds,       // 被裁剪掉的 paragraph ID
        int totalChars,                     // 原始总字符数
        int keptChars,                      // 保留字符数
        String cutPoint                     // 截断点描述
    ) {}

    // ─────────── 三种模式的主入口 ───────────

    /**
     * 模式 1：选区上下文 — 用户选中段落 ± 前后各 surroundCount 段
     * 段落边界对齐，不会截断到段落中间。
     */
    public TruncationResult selectionContext(
            List<DocumentBlock> blocks,
            String selectedParaId,
            int surroundCount,
            int maxContextChars) {

        if (blocks.isEmpty()) return emptyResult();

        int selIdx = -1;
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).paraId().equals(selectedParaId)) { selIdx = i; break; }
        }
        if (selIdx < 0) {
            log.warn("selectedParaId={} not found in blocks, using first paragraph", selectedParaId);
            selIdx = 0;
        }

        // 向前扩展 surroundCount 段（段落边界对齐）
        int from = selIdx;
        int segmentsBefore = 0;
        while (from > 0 && segmentsBefore < surroundCount) {
            from--;
            segmentsBefore++;
            // 遇到标题层级更小的，当作章节边界，多扩展一段
            if (blocks.get(from).isHeading() && blocks.get(from).headingLevel() <= 2) {
                if (from > 0) { from--; }
            }
        }

        // 向后扩展 surroundCount 段
        int to = selIdx;
        int segmentsAfter = 0;
        while (to < blocks.size() - 1 && segmentsAfter < surroundCount) {
            to++;
            segmentsAfter++;
            // 遇到同级别标题，在这里停下（新章节开始）
            if (blocks.get(to).isHeading() && blocks.get(to).headingLevel() <= 2 && segmentsAfter > 1) {
                break;
            }
        }

        // 如果超预算，优先裁掉离选区最远的段落
        int budget = maxContextChars;
        List<DocumentBlock> window = new ArrayList<>(blocks.subList(from, to + 1));
        // 贪心：从两端向中间收缩直到不超预算
        int left = 0, right = window.size() - 1;
        int selRelIdx = selIdx - from;

        while (charSum(window.subList(left, right + 1)) > budget && left < selRelIdx && right > selRelIdx) {
            // 优先裁掉非标题的、离选区更远的
            boolean leftIsHeading = window.get(left).isHeading();
            boolean rightIsHeading = window.get(right).isHeading();
            int leftDist = selRelIdx - left;
            int rightDist = right - selRelIdx;

            if (rightDist > leftDist && !rightIsHeading) {
                right--;
            } else if (leftDist > rightDist && !leftIsHeading) {
                left++;
            } else if (rightIsHeading && !leftIsHeading) {
                left++;
            } else if (leftIsHeading && !rightIsHeading) {
                right--;
            } else {
                // 同级别：裁远的那端
                if (rightDist >= leftDist) right--; else left++;
            }
        }

        // 最终只保留从 left 到 right
        List<DocumentBlock> kept;
        List<DocumentBlock> excluded = new ArrayList<>();
        if (left > 0) excluded.addAll(window.subList(0, left));
        if (right < window.size() - 1) excluded.addAll(window.subList(right + 1, window.size()));
        kept = window.subList(left, right + 1);

        return buildResult(blocks, kept, excluded, from + left);
    }

    /**
     * 模式 2：章节边界截断 — 保留高层级标题（H1-H2）+ 每个标题下的首段，
     * 其余按字符预算贪心添加，在章节边界处切。
     */
    public TruncationResult chapterBoundary(
            List<DocumentBlock> blocks,
            int maxContextChars) {

        if (blocks.isEmpty()) return emptyResult();

        // 阶段 1：必须保留所有 H1-H2 标题
        List<DocumentBlock> mandatory = new ArrayList<>();
        for (var b : blocks) {
            if (b.isHeading() && b.headingLevel() <= 2) {
                mandatory.add(b);
            }
        }
        int mandatoryChars = charSum(mandatory);

        // 阶段 2：剩余预算按章节贪心添加
        int remainingBudget = maxContextChars - mandatoryChars;
        List<DocumentBlock> bonus = new ArrayList<>();
        int currentChars = 0;

        // 找到每个 H1/H2 标题后面的段落（每个章节取前几个段落）
        Set<String> takenIds = mandatory.stream()
            .map(DocumentBlock::paraId).collect(Collectors.toSet());

        // 计算每个章节的边界
        List<Integer> chapterStarts = new ArrayList<>();
        for (int i = 0; i < blocks.size(); i++) {
            if (blocks.get(i).isHeading() && blocks.get(i).headingLevel() <= 2) {
                chapterStarts.add(i);
            }
        }

        // 每个章节取 body 段落直到用完预算或章节尾
        for (int c = 0; c < chapterStarts.size() && currentChars < remainingBudget; c++) {
            int start = chapterStarts.get(c);
            // 章节在本章开始，下一章标题处（或文档尾）结束
            int end = (c + 1 < chapterStarts.size()) ? chapterStarts.get(c + 1) : blocks.size();
            if (end == start) end = blocks.size(); // 连着的标题

            for (int i = start + 1; i < end && currentChars < remainingBudget; i++) {
                var b = blocks.get(i);
                if (takenIds.contains(b.paraId())) continue;
                // 每个标题下的前 3 个段落按比例取
                int bodyIdx = i - start - 1;
                if (bodyIdx > 2 && remainingBudget < 2000) break; // 预算紧张时每章只取前 2 段
                if (currentChars + b.charCount() > remainingBudget) break;
                bonus.add(b);
                currentChars += b.charCount();
                takenIds.add(b.paraId());
            }

            // 在章节边界处检查预算，跨章节时严格按字符预算切
            if (c < chapterStarts.size() - 1) {
                int nextStart = chapterStarts.get(c + 1);
                int headingCharCount = blocks.get(nextStart).charCount();
                if (currentChars + headingCharCount > remainingBudget) {
                    break; // 下一章标题装不下，此处截断
                }
            }
        }

        List<DocumentBlock> kept = new ArrayList<>(mandatory);
        kept.addAll(bonus);
        Set<String> keptIds = kept.stream().map(DocumentBlock::paraId).collect(Collectors.toSet());
        List<DocumentBlock> excluded = blocks.stream()
            .filter(b -> !keptIds.contains(b.paraId()))
            .toList();

        return buildResult(blocks, kept, excluded, findCutPoint(blocks, keptIds));
    }

    /**
     * 模式 3：RAG 检索截断 — 对全文任务，不塞整个文档，
     * 而是根据用户查询检索 top-K 相关段落，拼成 context。
     *
     * 由调用方先通过 RagService.retrieve 拿到 chunks，
     * 本方法只负责把检索结果格式化为上下文文本。
     */
    public TruncationResult ragRetrieval(
            List<DocumentBlock> blocks,
            List<RagRetrievedChunk> retrievedChunks,
            int maxContextChars) {

        if (blocks.isEmpty() || retrievedChunks.isEmpty()) return emptyResult();

        int budget = maxContextChars;
        List<DocumentBlock> kept = new ArrayList<>();
        int charUsed = 0;

        Set<String> usedIds = new HashSet<>();
        for (var chunk : retrievedChunks) {
            if (charUsed >= budget) break;
            // 找到对应的 DocumentBlock
            for (var b : blocks) {
                if (usedIds.contains(b.paraId())) continue;
                if (b.paraId().equals(chunk.paraId())) {
                    kept.add(b);
                    usedIds.add(b.paraId());
                    charUsed += b.charCount();
                    break;
                }
            }
        }

        // 给每个被检索到的段落前后各加一段相邻段落作为上下文
        List<DocumentBlock> enriched = new ArrayList<>(kept);
        for (var b : kept) {
            int idx = b.position();
            // 前一段
            if (idx > 0 && !usedIds.contains(blocks.get(idx - 1).paraId()) && charUsed < budget) {
                var prev = blocks.get(idx - 1);
                enriched.add(prev);
                usedIds.add(prev.paraId());
                charUsed += prev.charCount();
            }
            // 后一段
            if (idx < blocks.size() - 1 && !usedIds.contains(blocks.get(idx + 1).paraId()) && charUsed < budget) {
                var next = blocks.get(idx + 1);
                enriched.add(next);
                usedIds.add(next.paraId());
                charUsed += next.charCount();
            }
        }

        Set<String> keptIds = enriched.stream().map(DocumentBlock::paraId).collect(Collectors.toSet());
        List<DocumentBlock> excluded = blocks.stream()
            .filter(b -> !keptIds.contains(b.paraId()))
            .toList();

        return buildResult(blocks, enriched, excluded, 0);
    }

    // ─────────── 工具方法 ───────────

    /**
     * 将 blocks 列表拼接成上下文文本。
     * 标题前加空行，同一标题下的段落连续拼接。
     */
    public static String formatContext(List<DocumentBlock> blocks) {
        if (blocks.isEmpty()) return "";

        // 按 position 排序
        var sorted = new ArrayList<>(blocks);
        sorted.sort(Comparator.comparingInt(DocumentBlock::position));

        StringBuilder sb = new StringBuilder();
        String lastHeadingText = null;

        for (int i = 0; i < sorted.size(); i++) {
            var b = sorted.get(i);

            if (b.isHeading()) {
                if (sb.length() > 0) sb.append("\n\n");
                sb.append("#".repeat(b.headingLevel())).append(" ").append(b.text());
                lastHeadingText = b.text();
            } else {
                if (sb.length() > 0) {
                    // 前一个是标题 → 直接接；前一个是正文 → 换行接
                    var prev = sorted.get(i - 1);
                    sb.append(prev.isHeading() ? "\n" : "\n\n");
                }
                sb.append(b.text());
            }
        }
        return sb.toString();
    }

    /**
     * RAG 检索到的段落
     */
    public record RagRetrievedChunk(String paraId, String content, double score) {}

    // ─────────── private helpers ───────────

    private TruncationResult emptyResult() {
        return new TruncationResult("", List.of(), List.of(), 0, 0, "无内容");
    }

    private int charSum(List<DocumentBlock> list) {
        return list.stream().mapToInt(DocumentBlock::charCount).sum();
    }

    private TruncationResult buildResult(
            List<DocumentBlock> allBlocks,
            List<DocumentBlock> kept, List<DocumentBlock> excluded,
            int cutPosition) {

        List<String> includedIds = kept.stream().map(DocumentBlock::paraId).toList();
        List<String> excludedIds = excluded.stream().map(DocumentBlock::paraId).toList();
        int totalChars = charSum(allBlocks);
        int keptChars = charSum(kept);

        String cutPoint = "";
        if (excluded.size() > 0 && cutPosition < allBlocks.size()) {
            var lastExcluded = excluded.get(excluded.size() - 1);
            cutPoint = "在段落「" + lastExcluded.paraId() + "」之后截断，"
                + "保留 " + includedIds.size() + " 段 / " + keptChars + " 字符";
        }

        String context = formatContext(kept);

        return new TruncationResult(context, includedIds, excludedIds, totalChars, keptChars, cutPoint);
    }

    private int findCutPoint(List<DocumentBlock> blocks, Set<String> keptIds) {
        for (int i = blocks.size() - 1; i >= 0; i--) {
            if (keptIds.contains(blocks.get(i).paraId())) return i;
        }
        return 0;
    }
}
