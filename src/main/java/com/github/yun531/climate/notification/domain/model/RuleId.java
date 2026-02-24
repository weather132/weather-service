package com.github.yun531.climate.notification.domain.model;

/** "추적/식별" 목적의 룰 ID */
public enum RuleId {

    RAIN_ONSET_CHANGE("RainOnsetChangeRule"),
    RAIN_FORECAST("RainForecastRule"),
    WARNING_ISSUED("WarningIssuedRule");

    private final String id;

    RuleId(String id) {
        this.id = id;
    }

    /** payload.srcRule()에 넣을 안정적인 문자열 */
    public String id() {
        return id;
    }
}
