package com.subtlesight.word.web.security;

import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Auth Controller — 签发前端用的短期 JWT token。
 * key 不在前端 → 前端拿的是后端签发的 token。
 */
@RestController
@RequestMapping("/api/v2/auth")
public class AuthController {

    private final JwtAuthFilter jwtFilter;

    public AuthController(JwtAuthFilter jwtFilter) {
        this.jwtFilter = jwtFilter;
    }

    @PostMapping("/token")
    public Map<String, Object> getToken(@RequestBody Map<String, Object> body) {
        String userId = (String) body.getOrDefault("userId", "anonymous");
        String role = (String) body.getOrDefault("role", "user");

        String token = jwtFilter.issueToken(userId, role, 60); // 60 分钟有效期

        return Map.of(
            "token", token,
            "validityMinutes", 60,
            "userId", userId
        );
    }

    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }
}
