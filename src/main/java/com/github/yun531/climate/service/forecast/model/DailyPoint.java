package com.github.yun531.climate.service.forecast.model;

public record DailyPoint(
        int dayOffset,       // 0~6
        Integer maxTemp,
        Integer minTemp,
        Integer amPop,
        Integer pmPop
) {}
