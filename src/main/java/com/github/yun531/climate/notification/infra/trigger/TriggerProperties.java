package com.github.yun531.climate.notification.infra.trigger;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 트리거 전송에 필요한 설정.
 */
@ConfigurationProperties(prefix = "notification.trigger")
public record TriggerProperties(
        String hourlyTopic,
        String dailyTopicPrefix,
        long ttlSeconds
) {
    public TriggerProperties {
        if (ttlSeconds <= 0) ttlSeconds = 600;    // 기본 10분
    }

    public String dailyTopic(int hour) {
        return dailyTopicPrefix + String.format("%02d", hour);
    }

    public long ttlMillis() {
        return Math.multiplyExact(ttlSeconds, 1000L);
    }
}
