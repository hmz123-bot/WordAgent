package com.subtlesight.word.web.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 鉴权过滤器 — 所有 AI 接口要求带有效 token。
 * key 不在前端 → 前端拿的是后端签发的短期 token。
 */
@Component
public class JwtAuthFilter implements Filter {

    private static final Logger log = LoggerFactory.getLogger(JwtAuthFilter.class);

    @Value("${ai.gateway.jwt-secret:word-agent-default-secret-key-32chars!!}")
    private String secret;

    @Value("${ai.gateway.jwt-enabled:false}")
    private boolean enabled;

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
        throws IOException, ServletException {

        HttpServletRequest httpReq = (HttpServletRequest) request;
        HttpServletResponse httpResp = (HttpServletResponse) response;

        // 只拦截 AI 接口
        String path = httpReq.getRequestURI();
        if (!path.startsWith("/api/v2/")) {
            chain.doFilter(request, response);
            return;
        }

        if (!enabled) {
            chain.doFilter(request, response);
            return;
        }

        String authHeader = httpReq.getHeader("Authorization");
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            httpResp.setStatus(401);
            httpResp.getWriter().write("{\"error\":\"缺少认证令牌\"}");
            return;
        }

        try {
            String token = authHeader.substring(7);
            SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
            Claims claims = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();

            // 把用户信息挂到 request attribute
            httpReq.setAttribute("userId", claims.getSubject());
            httpReq.setAttribute("userRole", claims.get("role", String.class));

            chain.doFilter(request, response);
        } catch (Exception e) {
            log.warn("JWT 验签失败: {}", e.getMessage());
            httpResp.setStatus(401);
            httpResp.getWriter().write("{\"error\":\"令牌无效或已过期\"}");
        }
    }

    /**
     * 签发前端用的短期 token（登录或匿名访问时用）
     */
    public String issueToken(String userId, String role, long validityMinutes) {
        SecretKey key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        Date now = new Date();
        Date expiry = new Date(now.getTime() + validityMinutes * 60_000);
        return Jwts.builder()
            .subject(userId)
            .claim("role", role)
            .issuedAt(now)
            .expiration(expiry)
            .signWith(key)
            .compact();
    }
}
