package com.subtlesight.word.web.ai.infra;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 简易 KV Cache — 缓存文档上下文，避免长文每轮重算 token。
 *
 * 生产环境应替换为 Redis：
 *   - key: "doc:context:{documentId}"
 *   - value: JSON 序列化的文档上下文
 *   - TTL: 文档编辑期间（30 分钟空闲）
 */
@Service
public class RedisCacheService {

    private static final Logger log = LoggerFactory.getLogger(RedisCacheService.class);
    private final Map<String, CachedEntry<Object>> cache = new ConcurrentHashMap<>();

    /**
     * 缓存文档上下文（节约 token 消耗）
     */
    public void cache(String key, Object value) {
        cache.put(key, new CachedEntry<>(value, System.currentTimeMillis()));
        log.debug("缓存写入 key={}", key);
    }

    @SuppressWarnings("unchecked")
    public <T> T get(String key, Class<T> type) {
        CachedEntry<Object> entry = cache.get(key);
        if (entry == null) return null;
        // TTL 30 分钟
        if (System.currentTimeMillis() - entry.timestamp > 30 * 60 * 1000) {
            cache.remove(key);
            return null;
        }
        return (T) entry.value;
    }

    public void invalidate(String key) {
        cache.remove(key);
    }

    public void invalidatePrefix(String prefix) {
        cache.keySet().removeIf(k -> k.startsWith(prefix));
    }

    /** 文档上下文缓存 key */
    public static String docContextKey(String documentId) {
        return "doc:context:" + documentId;
    }

    private record CachedEntry<T>(T value, long timestamp) {}
}
