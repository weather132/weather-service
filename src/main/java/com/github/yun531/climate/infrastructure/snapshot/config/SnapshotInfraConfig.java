package com.github.yun531.climate.infrastructure.snapshot.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SnapshotCacheProperties.class
})
public class SnapshotInfraConfig {
}