package com.github.yun531.climate.notification.domain.model;

/**
 * 알림 유형 + 식별자(source) 통합.
 */
public enum AlertTypeEnum {
    RAIN_ONSET,
    RAIN_FORECAST,
    WARNING_ISSUED;

    public String source() {
        return name().toLowerCase();
    }
}