package com.github.yun531.climate.infrastructure.fcm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        String serviceAccountPath
) {}
