package com.subtlesight.word.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：CORS、文件上传大小等。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("*")
                .allowedMethods("GET", "POST", "PUT", "DELETE", "PATCH", "OPTIONS")
                .allowedHeaders("*")
                .maxAge(3600);
    }

    /**
     * 配置文件上传大小限制（默认 50MB）。
     */
    @Bean
    public WebMvcConfigurer multipartConfig() {
        return new WebMvcConfigurer() {
            // 通过 application.yml 配置
        };
    }
}