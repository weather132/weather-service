package com.github.yun531.climate.infrastructure.remote.snapshotapi.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({
        SnapshotApiProperties.class
})
public class SnapshotApiInfraConfig {
}