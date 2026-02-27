package com.github.yun531.climate.snapshot.infra.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SnapshotCacheProperties.class,
        SnapshotApiProperties.class
})
public class SnapshotInfraConfig {
}