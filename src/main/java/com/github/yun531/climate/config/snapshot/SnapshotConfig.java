package com.github.yun531.climate.config.snapshot;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SnapshotApiProperties.class,
        SnapshotCacheProperties.class
})
public class SnapshotConfig {
}