package com.github.yun531.climate.forecast.domain.readmodel;

public record ForecastDailyPoint(
        int dayOffset,
        Integer minTemp,
        Integer maxTemp,
        Integer amPop,
        Integer pmPop
) {}