package com.github.yun531.climate.snapshot.infra.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snapshot.cache")
public record SnapshotCacheProperties(
        int snapTtlMinutes,
        int recomputeThresholdMinutes
) {
    public SnapshotCacheProperties {
        if (snapTtlMinutes <= 0) snapTtlMinutes = 180;
        if (recomputeThresholdMinutes <= 0) recomputeThresholdMinutes = 165;
    }
}