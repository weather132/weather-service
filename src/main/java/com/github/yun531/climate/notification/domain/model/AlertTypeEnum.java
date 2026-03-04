package com.github.yun531.climate.notification.domain.model;

/**
 * 알림 유형 + 룰 식별자(srcRule) 통합.
 */
public enum AlertTypeEnum {

    RAIN_ONSET("RainOnsetComputer"),
    WARNING_ISSUED("WarningIssuedComputer"),
    RAIN_FORECAST("RainForecastComputer");

    private final String ruleId;

    AlertTypeEnum(String ruleId) {
        this.ruleId = ruleId;
    }

    /** payload.srcRule() 에 넣을 안정적인 문자열 */
    public String ruleId() {
        return ruleId;
    }
}