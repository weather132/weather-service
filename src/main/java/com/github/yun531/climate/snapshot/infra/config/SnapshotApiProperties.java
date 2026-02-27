package com.github.yun531.climate.snapshot.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snapshot.api")
public record SnapshotApiProperties(
        String baseUrl,
        long connectTimeoutMs,
        long readTimeoutMs
) {
    public SnapshotApiProperties {
        if (connectTimeoutMs <= 0) connectTimeoutMs = 2000;
        if (readTimeoutMs <= 0) readTimeoutMs = 5000;
    }
}