package com.github.yun531.climate.infrastructure.remote.warningapi.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "warning.api")
public record WarningApiProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs
) {
    public WarningApiProperties {
        if (connectTimeoutMs <= 0) connectTimeoutMs = 2000;
        if (readTimeoutMs <= 0) readTimeoutMs = 5000;
    }
}