package com.github.yun531.climate.service.notification.rule.adjust;

public class RainOnsetEventOffsetAdjuster extends HourOffsetEventAdjuster {
    public RainOnsetEventOffsetAdjuster(String hourKey, int maxShiftHours) {
        super(hourKey, maxShiftHours);
    }
}