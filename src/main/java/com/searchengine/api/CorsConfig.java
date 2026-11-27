package com.searchengine.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Browser origins allowed to call the API. Browsers send an Origin header on POST/DELETE even when
 * the UI is served through the same nginx host, so the Docker UI origin must be listed too —
 * otherwise Spring rejects those requests with 403.
 */
@Configuration
public class CorsConfig implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public CorsConfig(@Value("${lookit.cors.allowed-origins:http://localhost:5173,http://127.0.0.1:5173,"
            + "http://localhost:8081,http://127.0.0.1:8081}") String[] allowedOrigins) {
        this.allowedOrigins = allowedOrigins;
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins)
                .allowedMethods("GET", "POST", "DELETE");
    }
}
