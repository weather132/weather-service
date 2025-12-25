package com.github.yun531.climate.config.fcm;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fcm.triggers")
public record FcmTriggerProperties(
        String hourlyTopic,
        String dailyTopicPrefix,
        long ttlSeconds
) {}