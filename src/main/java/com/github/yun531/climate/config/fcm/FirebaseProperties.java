package com.github.yun531.climate.config.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "firebase")
public record FirebaseProperties(
        String serviceAccountPath
) {}
