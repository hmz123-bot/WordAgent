package com.subtlesight.word.web.security;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 简易限流器 — 按用户/租户限制 AI 调用频率。
 * 生产环境建议替换为 Redis + 令牌桶 / Guava RateLimiter。
 */
@Component
public class RateLimiterFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(RateLimiterFilter.class);

    // 内存存储：userId → window counts（每 60s 重置）
    private final Map<String, WindowCounter> counters = new ConcurrentHashMap<>();

    private static final int MAX_REQUESTS_PER_MINUTE = 30; // 每分钟最多 30 次
    private static final long WINDOW_MS = 60_000;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        String path = httpReq.getRequestURI();
        if (!path.startsWith("/api/v2/ai/")) {
            chain.doFilter(request, response);
            return;
        }

        // 从 request attribute 取 userId（由 JwtAuthFilter 设置）
        String userId = (String) httpReq.getAttribute("userId");
        if (userId == null) {
            userId = "anonymous";
        }

        WindowCounter counter = counters.computeIfAbsent(userId, k -> new WindowCounter());
        if (counter.incrementAndCheck() > MAX_REQUESTS_PER_MINUTE) {
            log.warn("限流触发 userId={} path={}", userId, path);
            httpResp.setStatus(429);
            httpResp.setHeader("Retry-After", "60");
            httpResp.getWriter().write("{\"error\":\"请求太频繁，请 1 分钟后重试\"}");
            return;
        }

        chain.doFilter(request, response);
    }

    private static class WindowCounter {
        private long windowStart = System.currentTimeMillis();
        private final AtomicInteger count = new AtomicInteger(0);

        synchronized int incrementAndCheck() {
            long now = System.currentTimeMillis();
            if (now - windowStart > WINDOW_MS) {
                windowStart = now;
                count.set(0);
            }
            return count.incrementAndGet();
        }
    }
}
