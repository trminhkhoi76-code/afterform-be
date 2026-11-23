package com.afterform.catalog.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/** Tight public storefront origin allowlist; credentials are never accepted for public bootstrap. */
@Configuration
public class PublicCorsConfiguration implements WebMvcConfigurer {
    private final String[] allowedOrigins;

    public PublicCorsConfiguration(
        @Value("${afterform.cors.allowed-origins}") String rawOrigins
    ) {
        this.allowedOrigins = java.util.Arrays.stream(rawOrigins.split(","))
            .map(String::trim)
            .filter(value -> !value.isBlank())
            .toArray(String[]::new);
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/v1/public/**")
            .allowedOrigins(allowedOrigins)
            .allowedMethods("GET", "HEAD", "OPTIONS")
            .allowCredentials(false)
            .maxAge(600);
    }
}
