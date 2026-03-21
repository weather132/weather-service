package com.github.yun531.climate.snapshot.domain.readmodel;

public record DailyPoint(
        int daysAhead,       // 0~6
        Integer minTemp,
        Integer maxTemp,
        Integer amPop,
        Integer pmPop
) {}