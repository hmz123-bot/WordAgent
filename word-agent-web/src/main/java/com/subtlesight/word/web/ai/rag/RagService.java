package com.subtlesight.word.web.ai.rag;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RAG 个人知识库服务 — 用户历史文档/风格样本入库。
 *
 * 生产应接入 Milvus / Zilliz / Pinecone 等向量数据库。
 * 当前为内存实现，存储文档摘要向量用于快速检索。
 */
@Service
public class RagService {

    private static final Logger log = LoggerFactory.getLogger(RagService.class);

    // 简易向量存储：docId → { content, embedding, metadata }
    private final Map<String, DocumentVector> store = new ConcurrentHashMap<>();

    /**
     * 将文档入库（含向量嵌入）
     */
    public void indexDocument(String documentId, String content, Map<String, String> metadata) {
        float[] embedding = simpleEmbed(content); // 生产应调 embedding API
        store.put(documentId, new DocumentVector(documentId, content, embedding, metadata));
        log.info("RAG 入库 documentId={} embeddingDim={}", documentId, embedding.length);
    }

    /**
     * 根据查询检索最相关的文档片段
     */
    public List<RetrievedChunk> retrieve(String query, int topK) {
        float[] queryVec = simpleEmbed(query);

        // 余弦相似度排序
        PriorityQueue<Map.Entry<String, Double>> pq = new PriorityQueue<>(
            Comparator.comparingDouble(Map.Entry::getValue));

        for (var entry : store.entrySet()) {
            double sim = cosineSimilarity(queryVec, entry.getValue().embedding());
            pq.offer(Map.entry(entry.getKey(), sim));
            if (pq.size() > topK) pq.poll();
        }

        List<RetrievedChunk> results = new ArrayList<>();
        while (!pq.isEmpty()) {
            var e = pq.poll();
            var doc = store.get(e.getKey());
            results.add(0, new RetrievedChunk(
                doc.content().substring(0, Math.min(500, doc.content().length())),
                e.getValue(),
                doc.metadata()
            ));
        }
        return results;
    }

    /**
     * 用检索结果填充 prompt 模板（"越写越像你"）
     */
    public String buildStyleContext(String query) {
        var chunks = retrieve(query, 3);
        if (chunks.isEmpty()) return "";
        StringBuilder sb = new StringBuilder("\n\n参考以下历史写作风格（请注意采纳但不需完全复制）：\n");
        for (var chunk : chunks) {
            sb.append("- ").append(chunk.content()).append("\n");
        }
        return sb.toString();
    }

    /** 删除文档 */
    public void removeDocument(String documentId) {
        store.remove(documentId);
    }

    // --- 简易向量实现（生产应替换为真实 embedding API） ---

    private float[] simpleEmbed(String text) {
        // 基于词频的简易向量（64 维）
        float[] vec = new float[64];
        text = text.toLowerCase();
        for (int i = 0; i < text.length(); i++) {
            int dim = text.charAt(i) % 64;
            vec[dim] += 1.0f;
        }
        // 归一化
        double norm = 0;
        for (float v : vec) norm += v * v;
        norm = Math.sqrt(norm) + 1e-8;
        for (int i = 0; i < vec.length; i++) vec[i] /= norm;
        return vec;
    }

    private double cosineSimilarity(float[] a, float[] b) {
        double dot = 0;
        for (int i = 0; i < a.length; i++) dot += a[i] * b[i];
        return dot;
    }

    // --- 类型 ---

    private record DocumentVector(String id, String content, float[] embedding, Map<String, String> metadata) {}
    public record RetrievedChunk(String content, double score, Map<String, String> metadata) {}
}
