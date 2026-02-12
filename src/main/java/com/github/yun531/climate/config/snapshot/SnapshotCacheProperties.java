package com.github.yun531.climate.config.snapshot;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "snapshot.cache")
public record SnapshotCacheProperties(
        int snapTtlMinutes,
        int dailyTtlMinutes,
        int recomputeThresholdMinutes
) {
    public SnapshotCacheProperties {
        if (snapTtlMinutes <= 0) snapTtlMinutes = 180;
        if (dailyTtlMinutes <= 0) dailyTtlMinutes = 60;
        if (recomputeThresholdMinutes <= 0) recomputeThresholdMinutes = 165;
    }
}