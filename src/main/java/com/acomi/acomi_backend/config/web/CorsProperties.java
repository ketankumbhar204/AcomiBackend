package com.acomi.acomi_backend.config.web;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Getter
@Setter
@Component
@ConfigurationProperties(prefix = "acomi.cors")
public class CorsProperties {

    /**
     * Comma-separated list of allowed browser origins.
     * Example: http://localhost:5173,https://example.com
     */
    private String allowedOrigins = "http://localhost:5173";
}
