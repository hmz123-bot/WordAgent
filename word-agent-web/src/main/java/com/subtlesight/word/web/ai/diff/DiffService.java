package com.subtlesight.word.web.ai.diff;

import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Diff 引擎 — 字符级文本差异计算。
 *
 * 服务端版本：处理 AI 返回的文本与原文的差异，返回 diff 序列供前端挂 Decoration。
 * 算法：简化版 Myers diff，生产可替换为 google-diff-match-patch JVM 版。
 */
@Service
public class DiffService {

    /**
     * 比较 AI 修改前后的文本，生成 diff 操作列表。
     * 前端用 diff 结果挂 ProseMirror Decoration（红色删除线 / 绿色新增）。
     */
    public List<DiffOp> computeDiff(String originalText, String aiModifiedText) {
        if (originalText.equals(aiModifiedText)) return List.of();

        // 简化实现：逐行 diff
        // 生产级应用应引入 google-diff-match-patch
        List<DiffOp> ops = new ArrayList<>();
        String[] oldLines = originalText.split("\n");
        String[] newLines = aiModifiedText.split("\n");

        int i = 0, j = 0;
        while (i < oldLines.length && j < newLines.length) {
            if (oldLines[i].equals(newLines[j])) {
                ops.add(new DiffOp(DiffType.UNCHANGED, oldLines[i], i, j));
                i++; j++;
            } else {
                // 找同步点
                int matchInNew = -1;
                for (int k = j + 1; k < Math.min(j + 10, newLines.length); k++) {
                    if (oldLines[i].equals(newLines[k])) {
                        matchInNew = k; break;
                    }
                }
                if (matchInNew > j) {
                    // 新文本多出来的行 = 新增
                    for (int k = j; k < matchInNew; k++) {
                        ops.add(new DiffOp(DiffType.INSERTED, newLines[k], i, k));
                    }
                    j = matchInNew;
                    ops.add(new DiffOp(DiffType.UNCHANGED, oldLines[i], i, j));
                    i++; j++;
                } else {
                    // 有修改或删除
                    ops.add(new DiffOp(DiffType.DELETED, oldLines[i], i, j));
                    ops.add(new DiffOp(DiffType.INSERTED, newLines[j], i, j));
                    i++; j++;
                }
            }
        }
        // 剩余行
        while (i < oldLines.length) {
            ops.add(new DiffOp(DiffType.DELETED, oldLines[i], i, j));
            i++;
        }
        while (j < newLines.length) {
            ops.add(new DiffOp(DiffType.INSERTED, newLines[j], i, j));
            j++;
        }

        return ops;
    }

    /**
     * 生成单个"润色建议"的完整 diff payload
     */
    public DiffResult generateSuggestion(String originalText, String aiText, String paragraphId) {
        var ops = computeDiff(originalText, aiText);
        return new DiffResult(
            UUID.randomUUID().toString().substring(0, 8),
            paragraphId,
            originalText,
            aiText,
            ops
        );
    }

    /**
     * 批量生成文档级 diff（全文润色场景）
     */
    public List<DiffResult> generateFullDocumentDiff(Map<String, String> paragraphMap, // paraId → originalText
                                                      Map<String, String> aiParagraphMap // paraId → aiText
    ) {
        List<DiffResult> results = new ArrayList<>();
        for (var entry : aiParagraphMap.entrySet()) {
            String paraId = entry.getKey();
            String original = paragraphMap.getOrDefault(paraId, "");
            String modified = entry.getValue();
            var result = generateSuggestion(original, modified, paraId);
            results.add(result);
        }
        return results;
    }

    // === 类型定义 ===

    public enum DiffType { UNCHANGED, INSERTED, DELETED }
    public record DiffOp(DiffType type, String text, int lineOld, int lineNew) {}
    public record DiffResult(
        String suggestionId,
        String paragraphId,
        String originalText,
        String suggestedText,
        List<DiffOp> operations
    ) {}
}
