package com.github.yun531.climate.fcm.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 외부 설정(application.yml/properties)의 값을 타입 안전하게 담는 컨테이너
 *  application.properties를 “타입 있는 객체”로 변환 */
@ConfigurationProperties(prefix = "fcm.triggers")
public record FcmTriggerProperties(
        String hourlyTopic,
        String dailyTopicPrefix,
        long ttlSeconds
) {
    public FcmTriggerProperties {
        if (ttlSeconds <= 0) ttlSeconds = 600; // 기본 10분
    }
}