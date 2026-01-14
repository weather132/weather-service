package com.github.yun531.climate.service.notification.rule.adjust;

public class RainOnsetEventValidAtAdjuster extends ValidAtEventAdjuster {
    public RainOnsetEventValidAtAdjuster(String validAtKey, int windowHours) {
        super(validAtKey, windowHours);
    }
}